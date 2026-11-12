package com.securechat.dao;

import com.securechat.dto.UserDTO;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

public class UserDAO {

    private static final String USER_COLS =
            "id, email, password_hash, full_name, gender, date_of_birth, status, " +
            "created_at, updated_at, last_seen_at, is_system_admin";

    public long insert(UserDTO u) throws SQLException {
        try (Connection c = DBConnection.open()) {
            return insert(c, u);
        }
    }

    public long insert(Connection c, UserDTO u) throws SQLException {
        String sql = "INSERT INTO dbo.users " +
                     "(email, password_hash, full_name, gender, date_of_birth, status, is_system_admin) " +
                     "VALUES (?, ?, ?, ?, ?, ?, 0)";
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getEmail());
            ps.setString(2, u.getPasswordHash());
            ps.setString(3, u.getFullName());
            if (u.getGender() != null) ps.setString(4, u.getGender());
            else ps.setNull(4, Types.NVARCHAR);
            if (u.getDateOfBirth() != null) ps.setDate(5, Date.valueOf(u.getDateOfBirth()));
            else ps.setNull(5, Types.DATE);
            ps.setString(6, u.getStatus() == null ? "PENDING" : u.getStatus());
            if (ps.executeUpdate() != 1) throw new SQLException("Insert user thất bại.");
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) { long id = rs.getLong(1); u.setId(id); return id; }
            }
            throw new SQLException("Insert user không trả về ID.");
        }
    }

    public Optional<UserDTO> getUserByEmail(String email) throws SQLException {
        String sql = "SELECT " + USER_COLS + " FROM dbo.users WHERE email = ?";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<UserDTO> findById(long userId) throws SQLException {
        String sql = "SELECT " + USER_COLS + " FROM dbo.users WHERE id = ?";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public Optional<UserDTO> findById(Connection c, long userId) throws SQLException {
        String sql = "SELECT " + USER_COLS + " FROM dbo.users WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public boolean updateStatus(long userId, String status) throws SQLException {
        try (Connection c = DBConnection.open()) {
            return updateStatus(c, userId, status);
        }
    }

    public boolean updateStatus(Connection c, long userId, String status) throws SQLException {
        String sql = "UPDATE dbo.users SET status = ?, updated_at = SYSUTCDATETIME() WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, userId);
            return ps.executeUpdate() == 1;
        }
    }

    /** [M3-B] Update 3 trường cho phép. Caller chịu trách nhiệm validate. */
    public boolean updateProfile(Connection c, long userId, String fullName,
                                  String gender, LocalDate dateOfBirth) throws SQLException {
        String sql = "UPDATE dbo.users SET full_name = ?, gender = ?, date_of_birth = ?, " +
                     "updated_at = SYSUTCDATETIME() WHERE id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, fullName);
            if (gender != null) ps.setString(2, gender);
            else ps.setNull(2, Types.NVARCHAR);
            if (dateOfBirth != null) ps.setDate(3, Date.valueOf(dateOfBirth));
            else ps.setNull(3, Types.DATE);
            ps.setLong(4, userId);
            return ps.executeUpdate() == 1;
        }
    }

    /** [M3-B] Best-effort presence update. Trả false nếu user không tồn tại. */
    public boolean updateLastSeen(long userId) throws SQLException {
        String sql = "UPDATE dbo.users SET last_seen_at = SYSUTCDATETIME() WHERE id = ?";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean deleteById(long userId) throws SQLException {
        try (Connection c = DBConnection.open()) {
            return deleteById(c, userId);
        }
    }

    public boolean deleteById(Connection c, long userId) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM dbo.users WHERE id = ?")) {
            ps.setLong(1, userId);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean existsById(long userId) throws SQLException {
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement("SELECT 1 FROM dbo.users WHERE id = ?")) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next(); }
        }
    }

    /** N-4: chỉ 3 cột cho SEARCH_USER — không thêm is_system_admin. */
    public Optional<UserDTO> findActiveByEmail(String email) throws SQLException {
        String sql = "SELECT id, email, full_name FROM dbo.users " +
                     "WHERE email = ? AND status = 'ACTIVE'";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                UserDTO u = new UserDTO();
                u.setId(rs.getLong("id"));
                u.setEmail(rs.getString("email"));
                u.setFullName(rs.getString("full_name"));
                u.setStatus("ACTIVE");
                return Optional.of(u);
            }
        }
    }

    private UserDTO map(ResultSet rs) throws SQLException {
        UserDTO u = new UserDTO();
        u.setId(rs.getLong("id"));
        u.setEmail(rs.getString("email"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setFullName(rs.getString("full_name"));
        u.setGender(rs.getString("gender"));
        Date d = rs.getDate("date_of_birth");
        u.setDateOfBirth(d == null ? null : d.toLocalDate());
        u.setStatus(rs.getString("status"));
        u.setCreatedAt(rs.getObject("created_at", LocalDateTime.class));
        u.setUpdatedAt(rs.getObject("updated_at", LocalDateTime.class));
        u.setLastSeenAt(rs.getObject("last_seen_at", LocalDateTime.class));
        u.setSystemAdmin(rs.getBoolean("is_system_admin"));   // [M3-B] Note N-1
        return u;
    }
}