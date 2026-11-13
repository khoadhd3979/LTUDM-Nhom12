package com.securechat.bll;

import com.securechat.common.protocol.Packet;
import com.securechat.config.AppConfig;
import com.securechat.dao.DBConnection;
import com.securechat.dao.GroupDAO;
import com.securechat.dao.UserDAO;
import com.securechat.dto.GroupDTO;
import com.securechat.dto.UserDTO;
import com.securechat.exception.BusinessException;
import com.securechat.server.ClientHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FileRelayServiceTest {

    private UserDAO userDAO;
    private GroupDAO groupDAO;
    private GroupService groupService;
    private FakeSender sender;
    private FileRelayService relay;
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
        sender = new FakeSender();
        relay = new FileRelayService(userDAO, groupDAO, groupService, sender);
        testUserIds.add(createTestUser("sender_a_" + System.nanoTime() + "@test.local", "ACTIVE"));
        testUserIds.add(createTestUser("recv_b_"   + System.nanoTime() + "@test.local", "ACTIVE"));
        setupCompleted = true;
    }

    @AfterEach
    void teardown() throws Exception {
        if (!setupCompleted) return;
        cleanup();
    }

    // ============================================================
    // Direct
    // ============================================================

    @Test
    void relayDirect_HappyPath_RelayPacketHasOriginalFieldsPlusSenderIdentity() throws Exception {
        long senderId = testUserIds.get(0);
        long receiverId = testUserIds.get(1);
        UserDTO senderUser = userDAO.findById(senderId).orElseThrow();
        UserDTO receiverUser = userDAO.findById(receiverId).orElseThrow();

        FakeHandler target = new FakeHandler();
        sender.handlers.put(receiverId, target);

        Map<String, Object> chunk = validChunk("file-1", 0, 3);
        chunk.put("receiver", receiverUser.getEmail());

        relay.relayDirect(senderUser, receiverUser.getEmail(), chunk);

        assertEquals(1, target.received.size());
        Packet p = target.received.get(0);
        assertEquals("FILE_CHUNK", p.getType());
        assertEquals("OK", p.getStatus());
        assertEquals("push-file-file-1-0", p.getRequestId());

        Map<String, Object> payload = p.getPayload();
        assertEquals("file-1", payload.get("fileId"));
        assertEquals("chunk.txt", payload.get("fileName"));
        assertEquals(0, ((Number) payload.get("chunkIndex")).intValue());
        assertEquals(3, ((Number) payload.get("totalChunks")).intValue());
        assertEquals("SGVsbG8=", payload.get("data"));   // nguyên văn
        assertEquals(senderId, ((Number) payload.get("senderId")).longValue());
        assertEquals(senderUser.getEmail(), payload.get("senderEmail"));
        assertFalse(payload.containsKey("receiver"));
        assertFalse(payload.containsKey("groupName"));
        assertFalse(payload.containsKey("groupId"));
    }

    @Test
    void relayDirect_UnknownReceiverEmail_ThrowsUserNotFound() throws Exception {
        UserDTO senderUser = userDAO.findById(testUserIds.get(0)).orElseThrow();
        Map<String, Object> chunk = validChunk("file-x", 0, 1);
        chunk.put("receiver", "nobody_" + System.nanoTime() + "@test.local");

        BusinessException ex = assertThrows(BusinessException.class,
                () -> relay.relayDirect(senderUser, (String) chunk.get("receiver"), chunk));
        assertEquals("USER_NOT_FOUND", ex.getCode());
        assertTrue(sender.handlers.isEmpty() || sender.handlers.values().stream().allMatch(h -> h.received.isEmpty()));
    }

    @Test
    void relayDirect_ReceiverOffline_ThrowsReceiverNotFound() throws Exception {
        long senderId = testUserIds.get(0);
        long receiverId = testUserIds.get(1);
        UserDTO senderUser = userDAO.findById(senderId).orElseThrow();
        UserDTO receiverUser = userDAO.findById(receiverId).orElseThrow();
        // receiver không được put vào sender.handlers → findByUserId null

        Map<String, Object> chunk = validChunk("file-y", 0, 1);
        chunk.put("receiver", receiverUser.getEmail());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> relay.relayDirect(senderUser, receiverUser.getEmail(), chunk));
        assertEquals("RECEIVER_NOT_FOUND", ex.getCode());
    }

    // ============================================================
    // Group
    // ============================================================

    @Test
    void relayGroup_HappyPath_FanoutToOnlineMembersExceptSender() throws Exception {
        long senderId = testUserIds.get(0);
        long member1 = testUserIds.get(1);
        long member2 = createTestUser("grp_c_" + System.nanoTime() + "@test.local", "ACTIVE");
        testUserIds.add(member2);

        long gid = groupService.createGroup(senderId, "RelayGrp-" + UUID.randomUUID()).getGroupId();
        testGroupIds.add(gid);
        groupService.addMember(senderId, gid, member1);
        groupService.addMember(senderId, gid, member2);
        GroupDTO group = groupDAO.findById(gid).orElseThrow();

        FakeHandler h1 = new FakeHandler();
        FakeHandler h2 = new FakeHandler();
        sender.handlers.put(member1, h1);
        sender.handlers.put(member2, h2);
        // senderId không được put → verify không bị relay

        UserDTO senderUser = userDAO.findById(senderId).orElseThrow();
        Map<String, Object> chunk = validChunk("file-g1", 1, 4);
        chunk.put("groupName", group.getGroupName());

        relay.relayGroup(senderUser, group.getGroupName(), chunk);

        assertEquals(1, h1.received.size());
        assertEquals(1, h2.received.size());

        Map<String, Object> p1 = h1.received.get(0).getPayload();
        assertEquals("file-g1", p1.get("fileId"));
        assertEquals(1, ((Number) p1.get("chunkIndex")).intValue());
        assertEquals(senderId, ((Number) p1.get("senderId")).longValue());
        assertEquals(senderUser.getEmail(), p1.get("senderEmail"));
        assertEquals(gid, ((Number) p1.get("groupId")).longValue());
        assertEquals(group.getGroupName(), p1.get("groupName"));
        assertFalse(p1.containsKey("receiver"));
    }

    @Test
    void relayGroup_NoMembersOnline_NoOpNotError() throws Exception {
        long senderId = testUserIds.get(0);
        long offlineMember = testUserIds.get(1);
        long gid = groupService.createGroup(senderId, "EmptyGrp-" + UUID.randomUUID()).getGroupId();
        testGroupIds.add(gid);
        groupService.addMember(senderId, gid, offlineMember);
        GroupDTO group = groupDAO.findById(gid).orElseThrow();
        // không put offlineMember vào sender.handlers

        UserDTO senderUser = userDAO.findById(senderId).orElseThrow();
        Map<String, Object> chunk = validChunk("file-g2", 0, 1);
        chunk.put("groupName", group.getGroupName());

        // Không throw — chỉ no-op
        relay.relayGroup(senderUser, group.getGroupName(), chunk);
        assertTrue(sender.handlers.isEmpty());
    }

    @Test
    void relayGroup_UnknownGroupName_ThrowsGroupNotFound() throws Exception {
        UserDTO senderUser = userDAO.findById(testUserIds.get(0)).orElseThrow();
        Map<String, Object> chunk = validChunk("file-g3", 0, 1);
        String unknown = "NoSuchGroup-" + UUID.randomUUID();
        chunk.put("groupName", unknown);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> relay.relayGroup(senderUser, unknown, chunk));
        assertEquals("GROUP_NOT_FOUND", ex.getCode());
    }

    // ============================================================
    // Helpers
    // ============================================================

    @Test
    void relayGroup_AmbiguousGroupName_ThrowsAmbiguousGroupName() throws Exception {
        long senderId = testUserIds.get(0);
        String dupName = "DupGrp-" + UUID.randomUUID();
        long g1 = groupService.createGroup(senderId, dupName).getGroupId();
        testGroupIds.add(g1);
        long g2 = groupService.createGroup(senderId, dupName).getGroupId();
        testGroupIds.add(g2);

        UserDTO senderUser = userDAO.findById(senderId).orElseThrow();
        Map<String, Object> chunk = validChunk("file-g4", 0, 1);
        chunk.put("groupName", dupName);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> relay.relayGroup(senderUser, dupName, chunk));
        assertEquals("AMBIGUOUS_GROUP_NAME", ex.getCode());
    }


    /** Fake ClientHandler — override sendSecure để capture packet, không cần session thật. */
    
    static class FakeHandler extends ClientHandler {
        final List<Packet> received = new ArrayList<>();
        FakeHandler() { super(null); }
        @Override public boolean sendSecure(Packet p) { received.add(p); return true; }
    }

    /** Fake Sender — routing qua map userId → FakeHandler. */
    static class FakeSender implements FileRelayService.Sender {
        final Map<Long, FakeHandler> handlers = new HashMap<>();
        @Override public ClientHandler findByUserId(long userId) { return handlers.get(userId); }
        @Override public boolean send(ClientHandler target, Packet packet) {
            return target.sendSecure(packet);
        }
    }

    private static Map<String, Object> validChunk(String fileId, int chunkIndex, int totalChunks) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("fileId", fileId);
        m.put("fileName", "chunk.txt");
        m.put("chunkIndex", chunkIndex);
        m.put("totalChunks", totalChunks);
        m.put("data", Base64.getEncoder().encodeToString("Hello".getBytes()));
        return m;
    }

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