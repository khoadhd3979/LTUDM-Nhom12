package com.securechat.dao;

import com.securechat.config.AppConfig;
import com.securechat.dto.MessageDTO;
import com.securechat.dto.UserDTO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class MessageDAOTest {

    private UserDAO userDAO;
    private MessageDAO messageDAO;
    private GroupDAO groupDAO;
    private final List<Long> testUserIds = new ArrayList<>();
    private final List<Long> testGroupIds = new ArrayList<>();
    private boolean setupCompleted = false;

    @BeforeEach
    void setup() throws Exception {
        Assumptions.assumeTrue(hasDbConfig(), "SC_DB_URL chưa cấu hình — skip DB test.");
        // Có config nhưng DB không kết nối được → FAIL (không skip).
        try (Connection ignored = DBConnection.open()) {
            // chỉ verify connection
        }
        userDAO = new UserDAO();
        messageDAO = new MessageDAO();
        groupDAO = new GroupDAO();
        long u1 = createTestUser("msg_a_" + System.nanoTime() + "@test.local");
        long u2 = createTestUser("msg_b_" + System.nanoTime() + "@test.local");
        testUserIds.add(u1);
        testUserIds.add(u2);
        setupCompleted = true;
    }

    @AfterEach
    void teardown() throws Exception {
        if (!setupCompleted) return;
        cleanup();
    }

    // ============================================================
    // Direct message tests
    // ============================================================

    @Test
    void insertDirectAndFindById() throws Exception {
        long s = testUserIds.get(0), r = testUserIds.get(1);
        long id = messageDAO.insertDirect(s, r, "Hello", "TEXT", null);
        assertTrue(id > 0);

        Optional<MessageDTO> opt = messageDAO.findDirectById(id);
        assertTrue(opt.isPresent());
        MessageDTO m = opt.get();
        assertEquals(s, m.getSenderId());
        assertEquals(r, m.getReceiverId().longValue());
        assertEquals("Hello", m.getContent());
        assertEquals("TEXT", m.getMessageType());
        assertEquals("SENT", m.getStatus());
        assertNotNull(m.getSentAt());
        assertNull(m.getGroupId());
    }

    @Test
    void markDirectDelivered() throws Exception {
        long s = testUserIds.get(0), r = testUserIds.get(1);
        long id = messageDAO.insertDirect(s, r, "Test", "TEXT", null);
        assertTrue(messageDAO.markDirectDelivered(id, r));
        assertFalse(messageDAO.markDirectDelivered(id, r));
        MessageDTO m = messageDAO.findDirectById(id).orElseThrow();
        assertEquals("DELIVERED", m.getStatus());
    }

    @Test
    void directHistoryBidirectional() throws Exception {
        long u1 = testUserIds.get(0), u2 = testUserIds.get(1);
        long id1 = messageDAO.insertDirect(u1, u2, "First from 1", "TEXT", null);
        long id2 = messageDAO.insertDirect(u2, u1, "Reply from 2", "TEXT", null);
        long id3 = messageDAO.insertDirect(u1, u2, "Second from 1", "TEXT", null);

        List<MessageDTO> page = messageDAO.findDirectHistory(u1, u2, null, 10);
        assertEquals(3, page.size());
        assertEquals(id3, page.get(0).getMessageId());
        assertEquals(id2, page.get(1).getMessageId());
        assertEquals(id1, page.get(2).getMessageId());
    }

    @Test
    void directHistoryCursorPagination() throws Exception {
        long u1 = testUserIds.get(0), u2 = testUserIds.get(1);
        long id1 = messageDAO.insertDirect(u1, u2, "m1", "TEXT", null);
        long id2 = messageDAO.insertDirect(u1, u2, "m2", "TEXT", null);
        long id3 = messageDAO.insertDirect(u1, u2, "m3", "TEXT", null);

        List<MessageDTO> page1 = messageDAO.findDirectHistory(u1, u2, null, 2);
        assertEquals(2, page1.size());
        assertEquals(id3, page1.get(0).getMessageId());
        assertEquals(id2, page1.get(1).getMessageId());

        long cursor = page1.get(1).getMessageId();
        List<MessageDTO> page2 = messageDAO.findDirectHistory(u1, u2, cursor, 2);
        assertEquals(1, page2.size());
        assertEquals(id1, page2.get(0).getMessageId());
    }

    // ============================================================
    // Group message tests
    // ============================================================

    @Test
    void insertGroupAndFindById() throws Exception {
        long sender = testUserIds.get(0);
        long gid = groupDAO.createGroupWithAdmin("MsgGrp-" + System.nanoTime(), sender);
        testGroupIds.add(gid);

        long id = messageDAO.insertGroup(sender, gid, "Hello group", "TEXT", null);
        assertTrue(id > 0);

        Optional<MessageDTO> opt = messageDAO.findGroupById(id);
        assertTrue(opt.isPresent());
        MessageDTO m = opt.get();
        assertEquals(id, m.getMessageId());
        assertEquals(sender, m.getSenderId());
        assertEquals(gid, m.getGroupId().longValue());
        assertEquals("Hello group", m.getContent());
        assertEquals("TEXT", m.getMessageType());
        assertNull(m.getReceiverId(), "Group message không có receiverId");
        assertNull(m.getStatus(), "Group message không có status");
        assertNotNull(m.getSentAt());
    }

    @Test
    void groupHistoryCursorPagination() throws Exception {
        long u1 = testUserIds.get(0);
        long gid = groupDAO.createGroupWithAdmin("GrpPage-" + System.nanoTime(), u1);
        testGroupIds.add(gid);

        long id1 = messageDAO.insertGroup(u1, gid, "g1", "TEXT", null);
        long id2 = messageDAO.insertGroup(u1, gid, "g2", "TEXT", null);
        long id3 = messageDAO.insertGroup(u1, gid, "g3", "TEXT", null);

        List<MessageDTO> page1 = messageDAO.findGroupHistory(gid, null, 2);
        assertEquals(2, page1.size());
        assertEquals(id3, page1.get(0).getMessageId());
        assertEquals(id2, page1.get(1).getMessageId());

        long cursor = page1.get(1).getMessageId();
        List<MessageDTO> page2 = messageDAO.findGroupHistory(gid, cursor, 2);
        assertEquals(1, page2.size());
        assertEquals(id1, page2.get(0).getMessageId());
    }

    @Test
    void groupHistoryReturnsOnlyRequestedGroup() throws Exception {
        long u1 = testUserIds.get(0);
        long gid1 = groupDAO.createGroupWithAdmin("GrpIso1-" + System.nanoTime(), u1);
        long gid2 = groupDAO.createGroupWithAdmin("GrpIso2-" + System.nanoTime(), u1);
        testGroupIds.add(gid1);
        testGroupIds.add(gid2);

        long idG1 = messageDAO.insertGroup(u1, gid1, "in-g1", "TEXT", null);
        long idG2 = messageDAO.insertGroup(u1, gid2, "in-g2", "TEXT", null);

        List<MessageDTO> historyG1 = messageDAO.findGroupHistory(gid1, null, 50);
        List<MessageDTO> historyG2 = messageDAO.findGroupHistory(gid2, null, 50);

        assertEquals(1, historyG1.size());
        assertEquals(idG1, historyG1.get(0).getMessageId());
        assertEquals(gid1, historyG1.get(0).getGroupId().longValue());

        assertEquals(1, historyG2.size());
        assertEquals(idG2, historyG2.get(0).getMessageId());
        assertEquals(gid2, historyG2.get(0).getGroupId().longValue());
    }

    // ============================================================
    // Helpers
    // ============================================================

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
        u.setGender("OTHER");
        u.setDateOfBirth(LocalDate.of(2000, 1, 1));
        u.setStatus("ACTIVE");
        return userDAO.insert(u);
    }

    private void cleanup() throws SQLException {
        try (Connection c = DBConnection.open()) {
            // 1. Delete groups (cascade → group_members + group_messages)
            if (!testGroupIds.isEmpty()) {
                String ph = String.join(",", Collections.nCopies(testGroupIds.size(), "?"));
                try (PreparedStatement ps = c.prepareStatement(
                        "DELETE FROM dbo.groups WHERE group_id IN (" + ph + ")")) {
                    int i = 1;
                    for (Long id : testGroupIds) ps.setLong(i++, id);
                    ps.executeUpdate();
                }
            }
            // 2. Delete direct messages by test users
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
            }
            // 3. Delete users
            if (!testUserIds.isEmpty()) {
                String ph = String.join(",", Collections.nCopies(testUserIds.size(), "?"));
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