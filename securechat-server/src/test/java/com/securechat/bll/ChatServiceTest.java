package com.securechat.bll;

import com.securechat.dao.DBConnection;
import com.securechat.dao.UserDAO;
import com.securechat.dto.MessageDTO;
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

import static org.junit.jupiter.api.Assertions.*;

class ChatServiceTest {

    private UserDAO userDAO;
    private ChatService chatService;
    private final List<Long> testUserIds = new ArrayList<>();
    private boolean setupCompleted = false;

    @BeforeEach
    void setup() throws Exception {
        Assumptions.assumeTrue(hasDbConfig(), "SC_DB_URL chưa cấu hình — skip DB test.");
        try (Connection ignored = DBConnection.open()) { /* verify connection */ }
        userDAO = new UserDAO();
        chatService = new ChatService();
        testUserIds.add(createTestUser("chat_a_" + System.nanoTime() + "@test.local", "ACTIVE"));
        testUserIds.add(createTestUser("chat_b_" + System.nanoTime() + "@test.local", "ACTIVE"));
        testUserIds.add(createTestUser("chat_pending_" + System.nanoTime() + "@test.local", "PENDING"));
        setupCompleted = true;
    }

    @AfterEach
    void teardown() throws Exception {
        if (!setupCompleted) return;
        cleanup();
    }

    // --- SEARCH_USER ---

    @Test
    void searchUser_FindsActiveUser() throws Exception {
        long u1 = testUserIds.get(0);
        String email = userDAO.getUserByEmail(userEmailOf(u1)).orElseThrow().getEmail();
        UserDTO found = chatService.searchUser(email);
        assertEquals(u1, found.getId());
        assertEquals(email, found.getEmail());
    }

    @Test
    void searchUser_NormalizesInput() throws Exception {
        long u1 = testUserIds.get(0);
        String email = userEmailOf(u1);
        UserDTO found = chatService.searchUser("  " + email.toUpperCase() + "  ");
        assertEquals(u1, found.getId());
    }

    @Test
    void searchUser_NotFound() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chatService.searchUser("nonexistent_" + System.nanoTime() + "@test.local"));
        assertEquals("USER_NOT_FOUND", ex.getCode());
    }

    @Test
    void searchUser_RejectsPendingUser() {
        long pendingId = testUserIds.get(2);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chatService.searchUser(userEmailOf(pendingId)));
        assertEquals("USER_NOT_FOUND", ex.getCode());
    }

    @Test
    void searchUser_RejectsInvalidEmail() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chatService.searchUser("not-an-email"));
        assertEquals("EMAIL_INVALID", ex.getCode());
    }

    // --- sendDirect msgType ---

    @Test
    void sendDirect_DefaultsToText() throws Exception {
        long s = testUserIds.get(0), r = testUserIds.get(1);
        MessageDTO m = chatService.sendDirect(s, r, "hello", null);
        assertEquals("TEXT", m.getMessageType());
    }

    @Test
    void sendDirect_Sticker() throws Exception {
        long s = testUserIds.get(0), r = testUserIds.get(1);
        MessageDTO m = chatService.sendDirect(s, r, "sticker-code-1", "STICKER");
        assertEquals("STICKER", m.getMessageType());
    }

    @Test
    void sendDirect_RejectsFileMsgType() {
        long s = testUserIds.get(0), r = testUserIds.get(1);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chatService.sendDirect(s, r, "fake file name", "FILE"));
        assertEquals("INVALID_MESSAGE_TYPE", ex.getCode());
    }

    @Test
    void sendDirect_RejectsUnknownMsgType() {
        long s = testUserIds.get(0), r = testUserIds.get(1);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chatService.sendDirect(s, r, "x", "VIDEO"));
        assertEquals("INVALID_MESSAGE_TYPE", ex.getCode());
    }

    @Test
    void sendDirect_RejectsSelfMessage() {
        long s = testUserIds.get(0);
        BusinessException ex = assertThrows(BusinessException.class,
                () -> chatService.sendDirect(s, s, "self", null));
        assertEquals("RECEIVER_SELF", ex.getCode());
    }

    // --- helpers ---

    private static boolean hasDbConfig() {
        String url = System.getenv("SC_DB_URL");
        return url != null && !url.isBlank();
    }

    private String userEmailOf(long userId) {
        String cached = emailCache.get(userId);
        if (cached == null) throw new IllegalStateException("No cached email for userId=" + userId);
        return cached;
    }

    // Lưu email đã tạo để có thể truy lại
    private final java.util.Map<Long, String> emailCache = new java.util.HashMap<>();
    private String userEmailCache(long userId) throws SQLException {
        String cached = emailCache.get(userId);
        if (cached != null) return cached;
        // Nếu chưa cache (test setup đã insert trực tiếp), query DB
        throw new IllegalStateException("No cached email for userId=" + userId);
    }

    private long createTestUser(String email, String status) throws SQLException {
        UserDTO u = new UserDTO();
        u.setEmail(email);
        u.setPasswordHash("$2a$12$abcdefghijklmnopqrstuv");
        u.setFullName("Chat Test User");
        u.setStatus(status);
        long id = userDAO.insert(u);
        emailCache.put(id, email);
        return id;
    }

    private void cleanup() throws SQLException {
        try (Connection c = DBConnection.open()) {
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