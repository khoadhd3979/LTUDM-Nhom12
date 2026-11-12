package com.securechat.bll;

import com.securechat.common.protocol.MessageType;
import com.securechat.common.protocol.Packet;
import com.securechat.dao.DBConnection;
import com.securechat.dao.GroupDAO;
import com.securechat.dao.GroupMessageReceiptDAO;
import com.securechat.dao.MessageDAO;
import com.securechat.dao.UserDAO;
import com.securechat.dto.MessageDTO;
import com.securechat.dto.UserDTO;
import com.securechat.exception.BusinessException;
import com.securechat.server.ClientConnectionManager;
import com.securechat.server.ClientHandler;
import com.securechat.validation.InputValidator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChatService {

    public static final int MAX_HISTORY_LIMIT = 100;
    public static final int DEFAULT_HISTORY_LIMIT = 30;

    private final MessageDAO messageDAO = new MessageDAO();
    private final GroupDAO groupDAO = new GroupDAO();
    private final GroupMessageReceiptDAO receiptDAO = new GroupMessageReceiptDAO();
    private final UserDAO userDAO = new UserDAO();

    // ============================================================
    // SEARCH_USER (M1)
    // ============================================================
    public UserDTO searchUser(String rawEmail) throws SQLException {
        String email = InputValidator.validateEmail(rawEmail);
        return userDAO.findActiveByEmail(email)
                .orElseThrow(() -> new BusinessException(
                        "USER_NOT_FOUND", "Không tìm thấy người dùng khả dụng"));
    }

    // ============================================================
    // [M3-B] sendGroup — transaction: message + N-1 receipts. KHÔNG audit.
    // ============================================================
    public MessageDTO sendGroup(long senderId, long groupId, String content, String rawMsgType)
            throws SQLException {
        if (senderId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        String trimmed = InputValidator.validateMessageContent(content);
        String msgType = InputValidator.validateMsgType(rawMsgType);

        if (!groupDAO.exists(groupId))
            throw new BusinessException("GROUP_NOT_FOUND", "Nhóm không tồn tại");
        if (!groupDAO.isMember(groupId, senderId))
            throw new BusinessException("NOT_GROUP_MEMBER", "Bạn không phải thành viên nhóm");

        long messageId;
        int receiptCount;
        try (Connection c = DBConnection.open()) {
            c.setAutoCommit(false);
            try {
                messageId = messageDAO.insertGroup(c, senderId, groupId, trimmed, msgType, null);
                receiptCount = receiptDAO.insertReceiptsForCurrentMembers(
                        c, messageId, groupId, senderId);
                c.commit();
            } catch (SQLException | BusinessException e) {
                c.rollback();
                throw e;
            }
        }

        MessageDTO msg = messageDAO.findGroupById(messageId)
                .orElseThrow(() -> new SQLException("Inserted group message not found: " + messageId));

        List<Long> memberIds = groupDAO.findMemberUserIds(groupId);
        for (Long mid : memberIds) {
            if (mid == null || mid == senderId) continue;
            ClientHandler h = ClientConnectionManager.findByUserId(mid);
            if (h == null) continue;
            Packet push = Packet.ok(MessageType.PUSH_GROUP_MESSAGE, "push-" + messageId,
                    toPayloadMap(msg, false));
            if (h.sendSecure(push)) {
                try { receiptDAO.markDelivered(messageId, mid); }
                catch (SQLException e) { System.err.println("[WARN] markDelivered fail msg=" + messageId + " user=" + mid); }
            }
        }
        return msg;
    }

    // ============================================================
    // sendDirect — giữ semantics M1, push + DELIVERED khi thành công.
    // ============================================================
    public MessageDTO sendDirect(long senderId, long receiverId, String content, String rawMsgType)
            throws SQLException {
        if (senderId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        if (receiverId == senderId)
            throw new BusinessException("RECEIVER_SELF", "Không thể gửi cho chính mình");
        String trimmed = InputValidator.validateMessageContent(content);
        String msgType = InputValidator.validateMsgType(rawMsgType);
        if (!userDAO.existsById(receiverId))
            throw new BusinessException("RECEIVER_NOT_FOUND", "Người nhận không tồn tại");

        long messageId = messageDAO.insertDirect(senderId, receiverId, trimmed, msgType, null);
        MessageDTO msg = messageDAO.findDirectById(messageId)
                .orElseThrow(() -> new SQLException("Inserted direct message not found: " + messageId));

        ClientHandler receiver = ClientConnectionManager.findByUserId(receiverId);
        if (receiver != null) {
            Packet push = Packet.ok(MessageType.PUSH_DIRECT_MESSAGE, "push-" + messageId,
                    toPayloadMap(msg, false));
            if (receiver.sendSecure(push)) {
                if (messageDAO.markDirectDelivered(messageId, receiverId)) {
                    msg.setStatus("DELIVERED");
                }
            }
        }
        return msg;
    }

    // ============================================================
    // [M3-B] MARK_READ — canonical message-level
    // ============================================================
    public MessageDTO markReadDirect(long actorId, long messageId) throws SQLException {
        if (actorId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        MessageDTO msg = messageDAO.findDirectById(messageId)
                .orElseThrow(() -> new BusinessException("MESSAGE_NOT_FOUND", "Tin nhắn không tồn tại"));
        if (msg.getReceiverId() == null || msg.getReceiverId() != actorId)
            throw new BusinessException("NOT_MESSAGE_RECIPIENT", "Bạn không phải người nhận tin");
        if (!messageDAO.markDirectRead(messageId, actorId)) {
            return messageDAO.findDirectById(messageId).orElseThrow();
        }
        MessageDTO updated = messageDAO.findDirectById(messageId).orElseThrow();

        ClientHandler senderHandler = ClientConnectionManager.findByUserId(msg.getSenderId());
        if (senderHandler != null) {
            senderHandler.sendSecure(Packet.ok(MessageType.MESSAGE_STATUS, "push-read-" + messageId,
                    Map.of("status", "SEEN")));
        }
        return updated;
    }

    public boolean markReadGroup(long actorId, long messageId) throws SQLException {
        if (actorId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        if (!receiptDAO.hasReceipt(messageId, actorId))
            throw new BusinessException("NOT_MESSAGE_RECIPIENT", "Bạn không có quyền mark read tin này");
        receiptDAO.markRead(messageId, actorId);
        return true;
    }

    // ============================================================
    // [M3-B P-9] Legacy MESSAGE_STATUS (conversation-level) — chỉ push SEEN khi n > 0.
    // ============================================================
    public int markConversationSeenDirect(long actorId, long otherUserId) throws SQLException {
        if (actorId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        if (otherUserId <= 0) throw new BusinessException("RECEIVER_NOT_FOUND", "Người dùng không hợp lệ");
        int n = messageDAO.markDirectConversationSeen(actorId, otherUserId);
        if (n > 0) {   // P-9: không push SEEN giả
            ClientHandler peer = ClientConnectionManager.findByUserId(otherUserId);
            if (peer != null) {
                peer.sendSecure(Packet.ok(MessageType.MESSAGE_STATUS, "push-seen-" + actorId,
                        Map.of("status", "SEEN")));
            }
        }
        return n;
    }

    public int markConversationSeenGroup(long actorId, long groupId) throws SQLException {
        if (actorId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        if (!groupDAO.exists(groupId))
            throw new BusinessException("GROUP_NOT_FOUND", "Nhóm không tồn tại");
        if (!groupDAO.isMember(groupId, actorId))
            throw new BusinessException("NOT_GROUP_MEMBER", "Bạn không phải thành viên nhóm");
        return receiptDAO.markAllReadInGroup(actorId, groupId);
    }

    // ============================================================
    // Fetch history (giữ nguyên M1)
    // ============================================================
    public List<MessageDTO> fetchDirectHistory(long userId, long otherUserId,
                                               Long beforeMessageId, int limit) throws SQLException {
        if (userId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        if (otherUserId <= 0) throw new BusinessException("RECEIVER_NOT_FOUND", "Người dùng không hợp lệ");
        int safe = InputValidator.normalizeHistoryLimit(limit);
        return messageDAO.findDirectHistory(userId, otherUserId, beforeMessageId, safe + 1);
    }

    public List<MessageDTO> fetchGroupHistory(long userId, long groupId,
                                              Long beforeMessageId, int limit) throws SQLException {
        if (userId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        if (!groupDAO.exists(groupId)) throw new BusinessException("GROUP_NOT_FOUND", "Nhóm không tồn tại");
        if (!groupDAO.isMember(groupId, userId)) throw new BusinessException("NOT_GROUP_MEMBER", "Bạn không phải thành viên");
        int safe = InputValidator.normalizeHistoryLimit(limit);
        return messageDAO.findGroupHistory(groupId, beforeMessageId, safe + 1);
    }

    public static Map<String, Object> toPayloadMap(MessageDTO m, boolean includeStatus) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("messageId", m.getMessageId());
        p.put("senderId", m.getSenderId());
        if (m.getReceiverId() != null) p.put("receiverId", m.getReceiverId());
        if (m.getGroupId() != null) p.put("groupId", m.getGroupId());
        p.put("content", m.getContent());
        p.put("messageType", m.getMessageType());
        if (m.getSentAt() != null) p.put("sentAt", m.getSentAt().toString());
        if (includeStatus && m.getStatus() != null) p.put("status", m.getStatus());
        return p;
    }

    public static Map<String, Object> toSearchUserPayload(UserDTO u) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("userId", u.getId());
        p.put("email", u.getEmail());
        p.put("fullName", u.getFullName());
        return p;
    }
}