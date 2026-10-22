package com.securechat.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * DAO for dbo.email_otp. All DATETIME2 columns are treated as UTC wall-clock.
 * Uses setObject/getObject(LocalDateTime) to avoid JVM timezone shifts.
 */
public class EmailOtpDAO {

    public record OtpRecord(
            long id, long userId, String otpHash, String purpose,
            LocalDateTime expiresAt, int attemptCount, LocalDateTime consumedAt) {}

    /** Insert using caller-provided Connection. Assumes OTP hash already computed. */
    public long insert(Connection c, long userId, String otpHash, String purpose, LocalDateTime expiresAt)
            throws SQLException {
        String sql = "INSERT INTO dbo.email_otp (user_id, otp_hash, purpose, expires_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS)) {
            ps.setLong(1, userId);
            ps.setString(2, otpHash);
            ps.setString(3, purpose);
            ps.setObject(4, expiresAt);
            if (ps.executeUpdate() != 1) throw new SQLException("Insert OTP thất bại.");
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getLong(1);
            }
            throw new SQLException("Insert OTP không trả về ID.");
        }
    }

    /** Marks all previous unconsumed OTPs for (user, purpose) as consumed now. */
    public int invalidatePrevious(Connection c, long userId, String purpose) throws SQLException {
        String sql = "UPDATE dbo.email_otp SET consumed_at = SYSUTCDATETIME() " +
                     "WHERE user_id = ? AND purpose = ? AND consumed_at IS NULL";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, purpose);
            return ps.executeUpdate();
        }
    }

    /** Latest non-consumed OTP regardless of expiry/attempt (caller checks). */
    public Optional<OtpRecord> findLatest(long userId, String purpose) throws SQLException {
        String sql = "SELECT TOP (1) id, user_id, otp_hash, purpose, expires_at, attempt_count, consumed_at " +
                    "FROM dbo.email_otp WHERE user_id = ? AND purpose = ? " +
                    "ORDER BY created_at DESC";
        try (Connection c = DBConnection.open();
            PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            ps.setString(2, purpose);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    /**
     * Atomic increment guarded by attempt limit.
     * @return new attempt count, or -1 if row was already at/above limit OR consumed.
     */
    public int incrementAttemptIfBelowLimit(long otpId, int limit) throws SQLException {
        String sql = "UPDATE dbo.email_otp SET attempt_count = attempt_count + 1 " +
                     "WHERE id = ? AND consumed_at IS NULL AND attempt_count < ?";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, otpId);
            ps.setInt(2, limit);
            if (ps.executeUpdate() != 1) return -1;
        }
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT attempt_count FROM dbo.email_otp WHERE id = ?")) {
            ps.setLong(1, otpId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    /**
     * Atomically mark consumed. Returns true iff row transitioned NULL → timestamp.
     * Prevents two concurrent verify requests from both succeeding.
     */
    public boolean markConsumed(Connection c, long otpId) throws SQLException {
        String sql = "UPDATE dbo.email_otp SET consumed_at = SYSUTCDATETIME() " +
                     "WHERE id = ? AND consumed_at IS NULL";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, otpId);
            return ps.executeUpdate() == 1;
        }
    }

    /** Compensating cleanup on registration rollback. */
    public int deleteByUser(long userId) throws SQLException {
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement("DELETE FROM dbo.email_otp WHERE user_id = ?")) {
            ps.setLong(1, userId);
            return ps.executeUpdate();
        }
    }

    private OtpRecord map(ResultSet rs) throws SQLException {
        LocalDateTime exp = rs.getObject("expires_at", LocalDateTime.class);
        LocalDateTime cons = rs.getObject("consumed_at", LocalDateTime.class);
        return new OtpRecord(
                rs.getLong("id"),
                rs.getLong("user_id"),
                rs.getString("otp_hash"),
                rs.getString("purpose"),
                exp, rs.getInt("attempt_count"), cons);
    }
}