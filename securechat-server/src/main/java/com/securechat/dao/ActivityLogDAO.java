package com.securechat.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * [M3-B] DAO cho bảng dbo.activity_logs.
 * Per OD-5: chỉ log nghiệp vụ thành công. Không có cột `result`.
 * Catalog action_type cố định (M3-A §12.1).
 */
public class ActivityLogDAO {

    /**
     * Insert trong transaction do Service quản lý.
     * Caller phải commit/rollback sau khi gọi.
     */
    public long insert(Connection c,
                       Long actorId,
                       String actionType,
                       String targetType,
                       Long targetId,
                       String metadata,
                       String ipAddress) throws SQLException {
        String sql = "INSERT INTO dbo.activity_logs " +
                     "(actor_id, action_type, target_type, target_id, metadata, ip_address) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            if (actorId == null) ps.setNull(1, Types.BIGINT);
            else ps.setLong(1, actorId);
            ps.setString(2, actionType);
            if (targetType == null) ps.setNull(3, Types.NVARCHAR);
            else ps.setString(3, targetType);
            if (targetId == null) ps.setNull(4, Types.BIGINT);
            else ps.setLong(4, targetId);
            if (metadata == null) ps.setNull(5, Types.NVARCHAR);
            else ps.setString(5, metadata);
            if (ipAddress == null) ps.setNull(6, Types.NVARCHAR);
            else ps.setString(6, ipAddress);

            if (ps.executeUpdate() != 1) throw new SQLException("Insert activity log thất bại.");
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
            throw new SQLException("Insert activity log không trả về ID.");
        }
    }

    /**
     * Standalone insert — tự mở Connection.
     * Chỉ dùng cho event không có transaction mẹ (VD SYSTEM_BROADCAST sau fanout).
     */
    public long insertStandalone(Long actorId,
                                 String actionType,
                                 String targetType,
                                 Long targetId,
                                 String metadata,
                                 String ipAddress) throws SQLException {
        try (Connection c = DBConnection.open()) {
            return insert(c, actorId, actionType, targetType, targetId, metadata, ipAddress);
        }
    }

    /** Query theo actor (phục vụ admin view nếu cần ở milestone sau). */
    public List<ActivityLogRow> findByActor(long actorId, int limit) throws SQLException {
        String sql = "SELECT TOP (?) log_id, actor_id, action_type, target_type, target_id, " +
                     "metadata, ip_address, created_at " +
                     "FROM dbo.activity_logs WHERE actor_id = ? ORDER BY created_at DESC, log_id DESC";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, limit);
            ps.setLong(2, actorId);
            try (ResultSet rs = ps.executeQuery()) {
                List<ActivityLogRow> list = new ArrayList<>();
                while (rs.next()) list.add(map(rs));
                return list;
            }
        }
    }

    public static final class ActivityLogRow {
        public long logId;
        public Long actorId;
        public String actionType;
        public String targetType;
        public Long targetId;
        public String metadata;
        public String ipAddress;
        public LocalDateTime createdAt;
    }

    private ActivityLogRow map(ResultSet rs) throws SQLException {
        ActivityLogRow r = new ActivityLogRow();
        r.logId = rs.getLong("log_id");
        long a = rs.getLong("actor_id");
        r.actorId = rs.wasNull() ? null : a;
        r.actionType = rs.getString("action_type");
        r.targetType = rs.getString("target_type");
        long t = rs.getLong("target_id");
        r.targetId = rs.wasNull() ? null : t;
        r.metadata = rs.getString("metadata");
        r.ipAddress = rs.getString("ip_address");
        r.createdAt = rs.getObject("created_at", LocalDateTime.class);
        return r;
    }
}