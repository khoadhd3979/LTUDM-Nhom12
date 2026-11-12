package com.securechat.dao;

import com.securechat.config.AppConfig;
import com.securechat.dto.GroupDTO;
import com.securechat.dto.UserDTO;
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

class GroupDAOTest {

    private UserDAO userDAO;
    private GroupDAO groupDAO;
    private final List<Long> testUserIds = new ArrayList<>();
    private final List<Long> testGroupIds = new ArrayList<>();
    private boolean setupCompleted = false;

    @BeforeEach
    void setup() throws Exception {
        Assumptions.assumeTrue(hasDbConfig(), "SC_DB_URL chưa cấu hình — skip DB test.");
        try (Connection ignored = DBConnection.open()) { /* verify connection */ }
        userDAO = new UserDAO();
        groupDAO = new GroupDAO();
        long u1 = createTestUser("grp_a_" + System.nanoTime() + "@test.local");
        long u2 = createTestUser("grp_b_" + System.nanoTime() + "@test.local");
        testUserIds.add(u1);
        testUserIds.add(u2);
        setupCompleted = true;
    }

    @AfterEach
    void teardown() throws Exception {
        if (!setupCompleted) return;
        cleanup();
    }

    @Test
    void createGroupWithAdminCreatesGroupAndMembership() throws Exception {
        long creator = testUserIds.get(0);
        String name = "TestGroup-" + UUID.randomUUID();
        long gid = groupDAO.createGroupWithAdmin(name, creator);
        testGroupIds.add(gid);

        assertTrue(gid > 0);
        assertTrue(groupDAO.exists(gid));
        assertTrue(groupDAO.isMember(gid, creator));
        assertEquals("ADMIN", groupDAO.getMemberRole(gid, creator));
    }

    @Test
    void addMemberAndCheckRole() throws Exception {
        long creator = testUserIds.get(0);
        long member = testUserIds.get(1);
        long gid = groupDAO.createGroupWithAdmin("TestAdd-" + UUID.randomUUID(), creator);
        testGroupIds.add(gid);

        assertFalse(groupDAO.isMember(gid, member));
        assertTrue(groupDAO.addMember(gid, member, "MEMBER"));
        assertTrue(groupDAO.isMember(gid, member));
        assertEquals("MEMBER", groupDAO.getMemberRole(gid, member));
    }

    @Test
    void findGroupsForUser() throws Exception {
        long u1 = testUserIds.get(0), u2 = testUserIds.get(1);
        long gid1 = groupDAO.createGroupWithAdmin("ForU1-" + UUID.randomUUID(), u1);
        long gid2 = groupDAO.createGroupWithAdmin("ForU2-" + UUID.randomUUID(), u2);
        testGroupIds.add(gid1);
        testGroupIds.add(gid2);
        groupDAO.addMember(gid2, u1, "MEMBER");

        List<GroupDTO> u1Groups = groupDAO.findGroupsForUser(u1);
        assertTrue(u1Groups.stream().anyMatch(g -> g.getGroupId() == gid1 && "ADMIN".equals(g.getRole())));
        assertTrue(u1Groups.stream().anyMatch(g -> g.getGroupId() == gid2 && "MEMBER".equals(g.getRole())));
    }

    @Test
    void findMemberUserIds() throws Exception {
        long u1 = testUserIds.get(0), u2 = testUserIds.get(1);
        long gid = groupDAO.createGroupWithAdmin("Members-" + UUID.randomUUID(), u1);
        testGroupIds.add(gid);
        groupDAO.addMember(gid, u2, "MEMBER");

        List<Long> members = groupDAO.findMemberUserIds(gid);
        assertEquals(2, members.size());
        assertTrue(members.contains(u1));
        assertTrue(members.contains(u2));
    }

    private static boolean hasDbConfig() {
        try {
            AppConfig.dbUrl();
            return true;
        } catch (IllegalStateException e) {
            return false;
        }
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
                        "DELETE FROM dbo.direct_messages WHERE sender_id IN (" + ph + ") " +
                        "OR receiver_id IN (" + ph + ")")) {
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