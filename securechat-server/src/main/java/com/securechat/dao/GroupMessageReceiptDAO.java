package com.securechat.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * [M3-B] DAO cho dbo.group_message_receipts.
 * Per D-03: N-1 receipts per group message (sender không có receipt).
 * Batch insert cùng transaction với group message insert (Service quản lý).
 */
public class GroupMessageReceiptDAO {

    /**
     * Batch insert N-1 receipts cho message vừa tạo.
     * @return số row đã insert (= số member hiện tại của group trừ sender).
     */
    public int insertReceiptsForCurrentMembers(Connection c, long messageId, long groupId,
                                                long excludeUserId) throws SQLException {
        String sql = "INSERT INTO dbo.group_message_receipts (message_id, user_id) " +
                     "SELECT ?, gm.user_id FROM dbo.group_members gm " +
                     "WHERE gm.group_id = ? AND gm.user_id <> ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            ps.setLong(2, groupId);
            ps.setLong(3, excludeUserId);
            return ps.executeUpdate();
        }
    }

    /**
     * Mark read cho receipt của chính user.
     * COALESCE delivered_at đảm bảo CK_receipt_read_after_delivered không vi phạm.
     * @return true nếu chuyển từ NULL → timestamp.
     */
    public boolean markRead(long messageId, long userId) throws SQLException {
        String sql = "UPDATE dbo.group_message_receipts " +
                     "SET delivered_at = COALESCE(delivered_at, SYSUTCDATETIME()), " +
                     "    read_at      = SYSUTCDATETIME() " +
                     "WHERE message_id = ? AND user_id = ? AND read_at IS NULL";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            ps.setLong(2, userId);
            return ps.executeUpdate() == 1;
        }
    }

    /** Mark read cho tất cả message chưa đọc của user trong 1 group. */
    public int markAllReadInGroup(long userId, long groupId) throws SQLException {
        String sql = "UPDATE r " +
                     "SET r.delivered_at = COALESCE(r.delivered_at, SYSUTCDATETIME()), " +
                     "    r.read_at      = SYSUTCDATETIME() " +
                     "FROM dbo.group_message_receipts r " +
                     "INNER JOIN dbo.group_messages m ON m.message_id = r.message_id " +
                     "WHERE m.group_id = ? AND r.user_id = ? AND r.read_at IS NULL";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            ps.setLong(2, userId);
            return ps.executeUpdate();
        }
    }

    /** Set delivered_at khi push thành công. */
    public boolean markDelivered(long messageId, long userId) throws SQLException {
        String sql = "UPDATE dbo.group_message_receipts SET delivered_at = SYSUTCDATETIME() " +
                     "WHERE message_id = ? AND user_id = ? AND delivered_at IS NULL";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            ps.setLong(2, userId);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Tìm user có receipt cho message — dùng để authorization MARK_READ group.
     * @return true nếu user có receipt.
     */
    public boolean hasReceipt(long messageId, long userId) throws SQLException {
        String sql = "SELECT 1 FROM dbo.group_message_receipts WHERE message_id = ? AND user_id = ?";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public static final class ReceiptRow {
        public long messageId;
        public long userId;
        public LocalDateTime deliveredAt;
        public LocalDateTime readAt;
    }

    public List<ReceiptRow> findByMessage(long messageId) throws SQLException {
        String sql = "SELECT message_id, user_id, delivered_at, read_at " +
                     "FROM dbo.group_message_receipts WHERE message_id = ?";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, messageId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ReceiptRow> list = new ArrayList<>();
                while (rs.next()) {
                    ReceiptRow r = new ReceiptRow();
                    r.messageId = rs.getLong("message_id");
                    r.userId = rs.getLong("user_id");
                    r.deliveredAt = rs.getObject("delivered_at", LocalDateTime.class);
                    r.readAt = rs.getObject("read_at", LocalDateTime.class);
                    list.add(r);
                }
                return list;
            }
        }
    }
}