package dao;

import dto.MessageDTO;
import utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

public class MessageDAO {

    /** Lưu message mới, trả về id vừa sinh. */
    public int saveMessage(MessageDTO message) {
        String sql = "INSERT INTO Messages (sender_id, receiver_id, group_id, content, type, status, [timestamp]) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setInt(1, message.getSenderId());
            setNullableInt(ps, 2, message.getReceiverId());
            setNullableInt(ps, 3, message.getGroupId());
            ps.setString(4, message.getContent());
            ps.setString(5, message.getType());
            ps.setString(6, message.getStatus());
            ps.setTimestamp(7, message.getTimestamp() != null
                    ? Timestamp.valueOf(message.getTimestamp())
                    : new Timestamp(System.currentTimeMillis()));

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Insert message thất bại");
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    message.setId(id);
                    return id;
                }
            }
            return -1;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi saveMessage: " + e.getMessage(), e);
        }
    }

    /**
     * Lấy lịch sử chat 1-1 giữa 2 user, có phân trang bằng OFFSET ... FETCH.
     * Sắp xếp theo timestamp tăng dần.
     */
    public List<MessageDTO> getChatHistory(int user1Id, int user2Id, int limit, int offset) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit phải > 0");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset phải >= 0");
        }

        String sql = "SELECT id, sender_id, receiver_id, group_id, content, type, status, [timestamp] "
                + "FROM Messages "
                + "WHERE group_id IS NULL "
                + "  AND ((sender_id = ? AND receiver_id = ?) OR (sender_id = ? AND receiver_id = ?)) "
                + "ORDER BY [timestamp] ASC "
                + "OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";

        List<MessageDTO> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, user1Id);
            ps.setInt(2, user2Id);
            ps.setInt(3, user2Id);
            ps.setInt(4, user1Id);
            ps.setInt(5, offset);
            ps.setInt(6, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(mapRow(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi getChatHistory: " + e.getMessage(), e);
        }
    }

    /** Cập nhật trạng thái message (SENT / DELIVERED / READ). */
    public boolean updateMessageStatus(int messageId, String status) {
        String sql = "UPDATE Messages SET status = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, messageId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi updateMessageStatus: " + e.getMessage(), e);
        }
    }

    private void setNullableInt(PreparedStatement ps, int index, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(index, value);
        } else {
            ps.setNull(index, Types.INTEGER);
        }
    }

    private MessageDTO mapRow(ResultSet rs) throws SQLException {
        MessageDTO msg = new MessageDTO();
        msg.setId(rs.getInt("id"));
        msg.setSenderId(rs.getInt("sender_id"));

        int receiverId = rs.getInt("receiver_id");
        msg.setReceiverId(rs.wasNull() ? null : receiverId);

        int groupId = rs.getInt("group_id");
        msg.setGroupId(rs.wasNull() ? null : groupId);

        msg.setContent(rs.getString("content"));
        msg.setType(rs.getString("type"));
        msg.setStatus(rs.getString("status"));

        Timestamp ts = rs.getTimestamp("timestamp");
        msg.setTimestamp(ts != null ? ts.toLocalDateTime() : null);
        return msg;
    }
}