package com.securechat.bll;

import com.securechat.config.AppConfig;
import com.securechat.dao.ActivityLogDAO;
import com.securechat.dao.DBConnection;
import com.securechat.dao.GroupDAO;
import com.securechat.dao.UserDAO;
import com.securechat.dto.GroupDTO;
import com.securechat.dto.UserDTO;
import com.securechat.exception.BusinessException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class GroupServiceTest {

    private UserDAO userDAO;
    private GroupDAO groupDAO;
    private GroupService groupService;
    private final List<Long> testUserIds = new ArrayList<>();
    private final List<Long> testGroupIds = new ArrayList<>();
    private boolean setupCompleted = false;

    @BeforeEach
    void setup() throws Exception {
        Assumptions.assumeTrue(hasDbConfig(), "SC_DB_URL chưa cấu hình — skip DB test.");
        try (Connection ignored = DBConnection.open()) { }
        userDAO = new UserDAO();
        groupDAO = new GroupDAO();
        groupService = new GroupService();
        testUserIds.add(createTestUser("gsvc_a_" + System.nanoTime() + "@test.local"));
        testUserIds.add(createTestUser("gsvc_b_" + System.nanoTime() + "@test.local"));
        setupCompleted = true;
    }

    @AfterEach
    void teardown() throws Exception {
        if (!setupCompleted) return;
        cleanup();
    }

    // ============================================================
    // Baseline tests (giữ nguyên)
    // ============================================================

    @Test
    void createGroupAutoAdmin() throws Exception {
        long creator = testUserIds.get(0);
        GroupDTO g = groupService.createGroup(creator, "Team-" + UUID.randomUUID());
        testGroupIds.add(g.getGroupId());
        assertTrue(g.getGroupId() > 0);
        assertEquals("ADMIN", g.getRole());
        assertEquals("ADMIN", groupDAO.getMemberRole(g.getGroupId(), creator));
    }

    @Test
    void createGroupRejectsBlankName() {
        long creator = testUserIds.get(0);
        assertThrows(BusinessException.class, () -> groupService.createGroup(creator, "   "));
        assertThrows(BusinessException.class, () -> groupService.createGroup(creator, null));
    }

    @Test
    void addMemberRequiresAdmin() throws Exception {
        long admin = testUserIds.get(0);
        long member = testUserIds.get(1);
        GroupDTO g = groupService.createGroup(admin, "Closed-" + UUID.randomUUID());
        testGroupIds.add(g.getGroupId());
        assertThrows(BusinessException.class,
                () -> groupService.addMember(member, g.getGroupId(), member));
        assertTrue(groupService.addMember(admin, g.getGroupId(), member));
        assertTrue(groupDAO.isMember(g.getGroupId(), member));
    }

    @Test
    void addMemberRejectsDuplicate() throws Exception {
        long admin = testUserIds.get(0);
        long member = testUserIds.get(1);
        GroupDTO g = groupService.createGroup(admin, "Dup-" + UUID.randomUUID());
        testGroupIds.add(g.getGroupId());
        groupService.addMember(admin, g.getGroupId(), member);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.addMember(admin, g.getGroupId(), member));
        assertEquals("ALREADY_GROUP_MEMBER", ex.getCode());
    }

    // ============================================================
    // Delta tests (P-1: atomic + audit + precedence)
    // ============================================================

    @Test
    void addMemberWritesMemberAndAuditTogether() throws Exception {
        long admin = testUserIds.get(0);
        long member = testUserIds.get(1);
        GroupDTO g = groupService.createGroup(admin, "Atomic-" + UUID.randomUUID());
        testGroupIds.add(g.getGroupId());

        assertTrue(groupService.addMember(admin, g.getGroupId(), member));
        assertTrue(groupDAO.isMember(g.getGroupId(), member));

        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT COUNT(*) FROM dbo.activity_logs " +
                     "WHERE action_type = 'GROUP_MEMBER_ADDED' AND target_id = ? " +
                     "AND metadata LIKE ?")) {
            ps.setLong(1, member);
            ps.setString(2, "%\"groupId\":" + g.getGroupId() + "}%");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                assertTrue(rs.getLong(1) >= 1,
                        "audit GROUP_MEMBER_ADDED phải tồn tại cùng member=" + member);
            }
        }
    }

    @Test
    void addMemberRollsBackMemberOnAuditFailure() throws Exception {
        long admin = testUserIds.get(0);
        long member = testUserIds.get(1);
        GroupDTO g = groupService.createGroup(admin, "Rollback-" + UUID.randomUUID());
        testGroupIds.add(g.getGroupId());

        ActivityLogDAO failing = new ActivityLogDAO() {
            @Override
            public long insert(Connection c, Long actorId, String actionType,
                               String targetType, Long targetId, String metadata,
                               String ipAddress) throws SQLException {
                throw new SQLException("Forced audit failure for test");
            }
        };
        GroupService failingSvc = new GroupService(groupDAO, userDAO, failing);

        SQLException ex = assertThrows(SQLException.class,
                () -> failingSvc.addMember(admin, g.getGroupId(), member));
        assertTrue(ex.getMessage().toLowerCase().contains("forced audit"));

        assertFalse(groupDAO.isMember(g.getGroupId(), member),
                "member phải bị rollback khi audit fail");
    }

    @Test
    void addMemberPrecedenceRoleBeforeTarget() throws Exception {
        long admin = testUserIds.get(0);
        long nonAdmin = testUserIds.get(1);
        GroupDTO g = groupService.createGroup(admin, "Prec-" + UUID.randomUUID());
        testGroupIds.add(g.getGroupId());
        long nonExistentTarget = 9_999_999_999L;

        BusinessException ex = assertThrows(BusinessException.class,
                () -> groupService.addMember(nonAdmin, g.getGroupId(), nonExistentTarget));
        assertEquals("NOT_GROUP_ADMIN", ex.getCode(),
                "precedence: NOT_GROUP_ADMIN phải trước TARGET_USER_NOT_FOUND");
    }

    // ============================================================
    // Helpers
    // ============================================================

    private static boolean hasDbConfig() {
        try { AppConfig.dbUrl(); return true; }
        catch (IllegalStateException e) { return false; }
    }

    private long createTestUser(String email) throws SQLException {
        UserDTO u = new UserDTO();
        u.setEmail(email);
        u.setPasswordHash("$2a$12$abcdefghijklmnopqrstuv");
        u.setFullName("Test User");
        u.setStatus("ACTIVE");
        return userDAO.insert(u);
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
                        "DELETE FROM dbo.users WHERE id IN (" + ph + ")")) {
                    int i = 1;
                    for (Long id : testUserIds) ps.setLong(i++, id);
                    ps.executeUpdate();
                }
            }
        }
    }
}