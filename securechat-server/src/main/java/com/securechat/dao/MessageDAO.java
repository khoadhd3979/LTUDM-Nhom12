package com.securechat.dao;

import com.securechat.dto.MessageDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class MessageDAO {

    public long insertDirect(long senderId, long receiverId, String content,
                             String messageType, Long attachmentId) throws SQLException {
        String sql = "INSERT INTO dbo.direct_messages " +
                     "(sender_id, receiver_id, content, message_type, attachment_id, status) " +
                     "VALUES (?, ?, ?, ?, ?, 'SENT')";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, senderId);
            ps.setLong(2, receiverId);
            ps.setString(3, content);
            ps.setString(4, messageType);
            if (attachmentId != null) ps.setLong(5, attachmentId);
            else ps.setNull(5, Types.BIGINT);
            if (ps.executeUpdate() != 1) throw new SQLException("Insert direct message thất bại.");
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
            throw new SQLException("Insert direct message không trả về ID.");
        }
    }

    public long insertGroup(long senderId, long groupId, String content,
                            String messageType, Long attachmentId) throws SQLException {
        String sql = "INSERT INTO dbo.group_messages " +
                     "(sender_id, group_id, content, message_type, attachment_id) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, senderId);
            ps.setLong(2, groupId);
            ps.setString(3, content);
            ps.setString(4, messageType);
            if (attachmentId != null) ps.setLong(5, attachmentId);
            else ps.setNull(5, Types.BIGINT);
            if (ps.executeUpdate() != 1) throw new SQLException("Insert group message thất bại.");
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
            throw new SQLException("Insert group message không trả về ID.");
        }
    }

    public Optional<MessageDTO> findDirectById(long messageId) throws SQLException {
        String sql = "SELECT message_id, sender_id, receiver_id, content, message_type, " +
                     "attachment_id, status, sent_at FROM dbo.direct_messages WHERE message_id = ?";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapDirect(rs)) : Optional.empty();
            }
        }
    }

    public Optional<MessageDTO> findGroupById(long messageId) throws SQLException {
        String sql = "SELECT message_id, sender_id, group_id, content, message_type, " +
                     "attachment_id, sent_at FROM dbo.group_messages WHERE message_id = ?";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapGroup(rs)) : Optional.empty();
            }
        }
    }

    public boolean markDirectDelivered(long messageId, long receiverId) throws SQLException {
        String sql = "UPDATE dbo.direct_messages SET status = 'DELIVERED' " +
                     "WHERE message_id = ? AND receiver_id = ? AND status = 'SENT'";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            ps.setLong(2, receiverId);
            return ps.executeUpdate() == 1;
        }
    }

    public List<MessageDTO> findDirectHistory(long userId, long otherUserId,
                                              Long beforeMessageId, int limit) throws SQLException {
        boolean withCursor = beforeMessageId != null;
        String sql = "SELECT TOP (?) message_id, sender_id, receiver_id, content, message_type, " +
                     "attachment_id, status, sent_at FROM dbo.direct_messages " +
                     "WHERE ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)) " +
                     (withCursor ? "AND message_id < ? " : "") +
                     "ORDER BY message_id DESC";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, limit);
            ps.setLong(i++, userId);
            ps.setLong(i++, otherUserId);
            ps.setLong(i++, otherUserId);
            ps.setLong(i++, userId);
            if (withCursor) ps.setLong(i, beforeMessageId);
            try (ResultSet rs = ps.executeQuery()) {
                List<MessageDTO> result = new ArrayList<>();
                while (rs.next()) result.add(mapDirect(rs));
                return result;
            }
        }
    }

    public List<MessageDTO> findGroupHistory(long groupId, Long beforeMessageId, int limit)
            throws SQLException {
        boolean withCursor = beforeMessageId != null;
        String sql = "SELECT TOP (?) message_id, sender_id, group_id, content, message_type, " +
                     "attachment_id, sent_at FROM dbo.group_messages " +
                     "WHERE group_id = ? " +
                     (withCursor ? "AND message_id < ? " : "") +
                     "ORDER BY message_id DESC";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            ps.setInt(i++, limit);
            ps.setLong(i++, groupId);
            if (withCursor) ps.setLong(i, beforeMessageId);
            try (ResultSet rs = ps.executeQuery()) {
                List<MessageDTO> result = new ArrayList<>();
                while (rs.next()) result.add(mapGroup(rs));
                return result;
            }
        }
    }

    private MessageDTO mapCommon(ResultSet rs) throws SQLException {
        MessageDTO m = new MessageDTO();
        m.setMessageId(rs.getLong("message_id"));
        m.setSenderId(rs.getLong("sender_id"));
        m.setContent(rs.getString("content"));
        m.setMessageType(rs.getString("message_type"));
        long attId = rs.getLong("attachment_id");
        m.setAttachmentId(rs.wasNull() ? null : attId);
        LocalDateTime sentAt = rs.getObject("sent_at", LocalDateTime.class);
        m.setSentAt(sentAt);
        return m;
    }

    private MessageDTO mapDirect(ResultSet rs) throws SQLException {
        MessageDTO m = mapCommon(rs);
        m.setReceiverId(rs.getLong("receiver_id"));
        m.setStatus(rs.getString("status"));
        return m;
    }

    private MessageDTO mapGroup(ResultSet rs) throws SQLException {
        MessageDTO m = mapCommon(rs);
        m.setGroupId(rs.getLong("group_id"));
        return m;
    }

    public long insertDirect(Connection c, long senderId, long receiverId, String content,
                             String messageType, Long attachmentId) throws SQLException {
        String sql = "INSERT INTO dbo.direct_messages " +
                     "(sender_id, receiver_id, content, message_type, attachment_id, status) " +
                     "VALUES (?, ?, ?, ?, ?, 'SENT')";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, senderId);
            ps.setLong(2, receiverId);
            ps.setString(3, content);
            ps.setString(4, messageType);
            if (attachmentId != null) ps.setLong(5, attachmentId);
            else ps.setNull(5, Types.BIGINT);
            if (ps.executeUpdate() != 1) throw new SQLException("Insert direct message thất bại.");
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
            throw new SQLException("Insert direct message không trả về ID.");
        }
    }

    public long insertGroup(Connection c, long senderId, long groupId, String content,
                            String messageType, Long attachmentId) throws SQLException {
        String sql = "INSERT INTO dbo.group_messages " +
                     "(sender_id, group_id, content, message_type, attachment_id) " +
                     "VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, senderId);
            ps.setLong(2, groupId);
            ps.setString(3, content);
            ps.setString(4, messageType);
            if (attachmentId != null) ps.setLong(5, attachmentId);
            else ps.setNull(5, Types.BIGINT);
            if (ps.executeUpdate() != 1) throw new SQLException("Insert group message thất bại.");
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
            throw new SQLException("Insert group message không trả về ID.");
        }
    }

    /** MARK_READ direct — idempotent, chỉ receiver mới update được. */
    public boolean markDirectRead(long messageId, long receiverId) throws SQLException {
        String sql = "UPDATE dbo.direct_messages SET status = 'READ', read_at = SYSUTCDATETIME() " +
                     "WHERE message_id = ? AND receiver_id = ? AND status <> 'READ'";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            ps.setLong(2, receiverId);
            return ps.executeUpdate() == 1;
        }
    }

    /** Legacy MESSAGE_STATUS direct — bulk mark từ peer. */
    public int markDirectConversationSeen(long receiverId, long senderId) throws SQLException {
        String sql = "UPDATE dbo.direct_messages SET status = 'READ', read_at = SYSUTCDATETIME() " +
                     "WHERE receiver_id = ? AND sender_id = ? AND read_at IS NULL " +
                     "AND status IN ('SENT','DELIVERED')";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, receiverId);
            ps.setLong(2, senderId);
            return ps.executeUpdate();
        }
    }
}