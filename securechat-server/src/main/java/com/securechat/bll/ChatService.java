package com.securechat.bll;

import com.securechat.common.protocol.Packet;
import com.securechat.dao.GroupDAO;
import com.securechat.dao.MessageDAO;
import com.securechat.dao.UserDAO;
import com.securechat.dto.MessageDTO;
import com.securechat.exception.BusinessException;
import com.securechat.server.ClientConnectionManager;
import com.securechat.server.ClientHandler;
import com.securechat.validation.InputValidator;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ChatService {

    public static final int MAX_HISTORY_LIMIT = 100;
    public static final int DEFAULT_HISTORY_LIMIT = 30;

    private final MessageDAO messageDAO = new MessageDAO();
    private final GroupDAO groupDAO = new GroupDAO();
    private final UserDAO userDAO = new UserDAO();

    // ============================================================
    // Direct message
    // ============================================================
    public MessageDTO sendDirect(long senderId, long receiverId, String content)
            throws SQLException {
        if (senderId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        if (receiverId == senderId)
            throw new BusinessException("RECEIVER_SELF", "Không thể gửi tin nhắn cho chính mình");
        String trimmed = InputValidator.validateMessageContent(content);
        if (!userDAO.existsById(receiverId))
            throw new BusinessException("RECEIVER_NOT_FOUND", "Người nhận không tồn tại");

        long messageId = messageDAO.insertDirect(senderId, receiverId, trimmed, "TEXT", null);
        MessageDTO msg = messageDAO.findDirectById(messageId)
                .orElseThrow(() -> new SQLException("Inserted direct message not found: " + messageId));

        ClientHandler receiverHandler = ClientConnectionManager.findByUserId(receiverId);
        if (receiverHandler != null) {
            Packet push = Packet.ok("PUSH_DIRECT_MESSAGE", "push-" + messageId,
                    toPayloadMap(msg, false));
            if (receiverHandler.sendSecure(push)) {
                if (messageDAO.markDirectDelivered(messageId, receiverId)) {
                    msg.setStatus("DELIVERED");
                }
            }
        }
        return msg;
    }

    // ============================================================
    // Group message
    // ============================================================
    public MessageDTO sendGroup(long senderId, long groupId, String content)
            throws SQLException {
        if (senderId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        String trimmed = InputValidator.validateMessageContent(content);
        if (!groupDAO.exists(groupId))
            throw new BusinessException("GROUP_NOT_FOUND", "Nhóm không tồn tại");
        if (!groupDAO.isMember(groupId, senderId))
            throw new BusinessException("NOT_GROUP_MEMBER", "Bạn không phải thành viên nhóm");

        long messageId = messageDAO.insertGroup(senderId, groupId, trimmed, "TEXT", null);
        MessageDTO msg = messageDAO.findGroupById(messageId)
                .orElseThrow(() -> new SQLException("Inserted group message not found: " + messageId));

        List<Long> memberIds = groupDAO.findMemberUserIds(groupId);
        for (Long memberId : memberIds) {
            if (memberId == null || memberId == senderId) continue;
            ClientHandler receiverHandler = ClientConnectionManager.findByUserId(memberId);
            if (receiverHandler == null) continue;
            Packet push = Packet.ok("PUSH_GROUP_MESSAGE", "push-" + messageId,
                    toPayloadMap(msg, false));
            receiverHandler.sendSecure(push);
        }
        return msg;
    }

    // ============================================================
    // Fetch history
    // ============================================================
    public List<MessageDTO> fetchDirectHistory(long userId, long otherUserId,
                                               Long beforeMessageId, int limit) throws SQLException {
        if (userId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        if (otherUserId <= 0)
            throw new BusinessException("RECEIVER_NOT_FOUND", "Người dùng không hợp lệ");
        int safeLimit = InputValidator.normalizeHistoryLimit(limit);
        return messageDAO.findDirectHistory(userId, otherUserId, beforeMessageId, safeLimit + 1);
    }

    public List<MessageDTO> fetchGroupHistory(long userId, long groupId,
                                              Long beforeMessageId, int limit) throws SQLException {
        if (userId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        if (!groupDAO.exists(groupId))
            throw new BusinessException("GROUP_NOT_FOUND", "Nhóm không tồn tại");
        if (!groupDAO.isMember(groupId, userId))
            throw new BusinessException("NOT_GROUP_MEMBER", "Bạn không phải thành viên nhóm");
        int safeLimit = InputValidator.normalizeHistoryLimit(limit);
        return messageDAO.findGroupHistory(groupId, beforeMessageId, safeLimit + 1);
    }

    // ============================================================
    // Serialization helper
    // ============================================================
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
}