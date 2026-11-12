package com.securechat.bll;

import com.securechat.config.AppConfig;
import com.securechat.dao.DBConnection;
import com.securechat.dao.GroupDAO;
import com.securechat.dao.UserDAO;
import com.securechat.dto.UserDTO;
import com.securechat.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AdminServiceTest {

    private UserDAO userDAO;
    private GroupDAO groupDAO;
    private GroupService groupService;
    private AdminService adminService;
    private final List<Long> testUserIds = new ArrayList<>();
    private final List<Long> testGroupIds = new ArrayList<>();
    private long adminId;
    private boolean setupCompleted = false;

    @BeforeEach
    void setup() throws Exception {
        Assumptions.assumeTrue(hasDbConfig(), "SC_DB_URL chưa cấu hình — skip DB test.");
        try (Connection ignored = DBConnection.open()) { }
        userDAO = new UserDAO();
        groupDAO = new GroupDAO();
        groupService = new GroupService();
        adminService = new AdminService();

        adminId = createTestUser("admin_" + System.nanoTime() + "@test.local", "ACTIVE");
        promoteSystemAdmin(adminId);
        testUserIds.add(adminId);
        setupCompleted = true;
    }

    @AfterEach
    void teardown() throws Exception {
        if (!setupCompleted) return;
        cleanup();
    }

    // ============================================================
    // Q-2: happy path
    // ============================================================

    @Test
    void lockUser_NonBlocked_Succeeds() throws Exception {
        long targetId = createTestUser("target_" + System.nanoTime() + "@test.local", "ACTIVE");
        testUserIds.add(targetId);

        adminService.lockUser(adminId, targetId, "test reason", null);

        UserDTO target = userDAO.findById(targetId).orElseThrow();
        assertEquals("LOCKED", target.getStatus());
    }

    // ============================================================
    // Q-1: last-admin rejection — setup phải cho creator tự rời để target thành admin cuối
    // ============================================================

    @Test
    void lockUser_RejectsLastAdminOfOneGroup_DetailsContainsGroupId() throws Exception {
        long creatorId = createTestUser("creator1_" + System.nanoTime() + "@test.local", "ACTIVE");
        long targetId  = createTestUser("target1_"  + System.nanoTime() + "@test.local", "ACTIVE");
        testUserIds.add(creatorId);
        testUserIds.add(targetId);

        long gid = groupService.createGroup(creatorId, "G1-" + UUID.randomUUID()).getGroupId();
        testGroupIds.add(gid);
        groupService.addMember(creatorId, gid, targetId);
        groupService.setRole(creatorId, gid, targetId, "ADMIN", null);
        // Q-1: creator tự rời — target trở thành ACTIVE ADMIN duy nhất
        groupService.removeMember(creatorId, gid, creatorId, null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminService.lockUser(adminId, targetId, "test", null));
        assertEquals("CANNOT_LOCK_LAST_ADMIN_OF_GROUP", ex.getCode());
        Object gids = ex.getDetails().get("groupIds");
        assertNotNull(gids, "details must carry groupIds");
        assertTrue(gids instanceof List<?>);
        List<?> list = (List<?>) gids;
        assertEquals(1, list.size());
        assertEquals(gid, ((Number) list.get(0)).longValue());
    }

    @Test
    void lockUser_RejectsLastAdminOfMultipleGroups_DetailsContainsAllGroupIds() throws Exception {
        long creatorId = createTestUser("creatorM_" + System.nanoTime() + "@test.local", "ACTIVE");
        long targetId  = createTestUser("targetM_"  + System.nanoTime() + "@test.local", "ACTIVE");
        testUserIds.add(creatorId);
        testUserIds.add(targetId);

        long gid1 = groupService.createGroup(creatorId, "Gm1-" + UUID.randomUUID()).getGroupId();
        long gid2 = groupService.createGroup(creatorId, "Gm2-" + UUID.randomUUID()).getGroupId();
        testGroupIds.add(gid1);
        testGroupIds.add(gid2);

        for (long gid : List.of(gid1, gid2)) {
            groupService.addMember(creatorId, gid, targetId);
            groupService.setRole(creatorId, gid, targetId, "ADMIN", null);
            // Q-1: creator tự rời từng group
            groupService.removeMember(creatorId, gid, creatorId, null);
        }

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminService.lockUser(adminId, targetId, "test", null));
        assertEquals("CANNOT_LOCK_LAST_ADMIN_OF_GROUP", ex.getCode());
        Object gids = ex.getDetails().get("groupIds");
        assertNotNull(gids);
        List<?> list = (List<?>) gids;
        assertEquals(2, list.size());
        List<Long> ids = new ArrayList<>();
        for (Object o : list) ids.add(((Number) o).longValue());
        List<Long> expected = new ArrayList<>(List.of(gid1, gid2));
        Collections.sort(expected);
        Collections.sort(ids);
        assertEquals(expected, ids);
    }

    // ============================================================
    // Other lock guards
    // ============================================================

    @Test
    void lockUser_RejectsSelf() throws Exception {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminService.lockUser(adminId, adminId, "self", null));
        assertEquals("CANNOT_LOCK_SELF", ex.getCode());
    }

    @Test
    void lockUser_RejectsAlreadyLocked() throws Exception {
        long targetId = createTestUser("lock_" + System.nanoTime() + "@test.local", "ACTIVE");
        testUserIds.add(targetId);
        adminService.lockUser(adminId, targetId, "first", null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminService.lockUser(adminId, targetId, "second", null));
        assertEquals("USER_ALREADY_LOCKED", ex.getCode());
    }

    @Test
    void lockUser_RequiresSystemAdmin() throws Exception {
        long nonAdminId = createTestUser("nonadmin_" + System.nanoTime() + "@test.local", "ACTIVE");
        long targetId   = createTestUser("tgt2_"     + System.nanoTime() + "@test.local", "ACTIVE");
        testUserIds.add(nonAdminId);
        testUserIds.add(targetId);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> adminService.lockUser(nonAdminId, targetId, "test", null));
        assertEquals("NOT_SYSTEM_ADMIN", ex.getCode());
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static boolean hasDbConfig() {
        try { AppConfig.dbUrl(); return true; }
        catch (IllegalStateException e) { return false; }
    }

    private long createTestUser(String email, String status) throws SQLException {
        UserDTO u = new UserDTO();
        u.setEmail(email);
        u.setPasswordHash("$2a$12$abcdefghijklmnopqrstuv");
        u.setFullName("Test User");
        u.setStatus(status);
        return userDAO.insert(u);
    }

    private void promoteSystemAdmin(long userId) throws SQLException {
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(
                     "UPDATE dbo.users SET is_system_admin = 1 WHERE id = ?")) {
            ps.setLong(1, userId);
            ps.executeUpdate();
        }
    }

    private void cleanup() throws SQLException {
        try (Connection c = DBConnection.open()) {
            if (!testGroupIds.isEmpty()) {
                String ph = String.join(",", Collections.nCopies(testGroupIds.size(), "?"));
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM dbo.groups WHERE group_id IN (" + ph + ")")) {
                    int i = 1;
                    for (Long id : testGroupIds) ps.setLong(i++, id);
                    ps.executeUpdate();
                }
            }
            if (!testUserIds.isEmpty()) {
                String ph = String.join(",", Collections.nCopies(testUserIds.size(), "?"));
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM dbo.activity_logs WHERE actor_id IN (" + ph + ") OR target_id IN (" + ph + ")")) {
                    int i = 1;
                    for (Long id : testUserIds) ps.setLong(i++, id);
                    for (Long id : testUserIds) ps.setLong(i++, id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM dbo.direct_messages WHERE sender_id IN (" + ph + ") OR receiver_id IN (" + ph + ")")) {
                    int i = 1;
                    for (Long id : testUserIds) ps.setLong(i++, id);
                    for (Long id : testUserIds) ps.setLong(i++, id);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM dbo.users WHERE id IN (" + ph + ")")) {
                    int i = 1;
                    for (Long id : testUserIds) ps.setLong(i++, id);
                    ps.executeUpdate();
                }
            }
        }
    }
}