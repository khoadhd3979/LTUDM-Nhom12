package com.securechat.dao;

import com.securechat.dto.UserDTO;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Optional;

public class UserDAO {

    public long insert(UserDTO u) throws SQLException {
        String sql = "INSERT INTO dbo.users (email, password_hash, full_name, gender, date_of_birth, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
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

        /** Overload dùng Connection của caller — KHÔNG tự đóng Connection (transaction do caller quản lý). */
    public long insert(Connection c, UserDTO u) throws SQLException {
        String sql = "INSERT INTO dbo.users (email, password_hash, full_name, gender, date_of_birth, status) " +
                     "VALUES (?, ?, ?, ?, ?, ?)";
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
        String sql = "SELECT id, email, password_hash, full_name, gender, date_of_birth, status, " +
                     "created_at, updated_at FROM dbo.users WHERE email = ?";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    public boolean updateStatus(long userId, String status) throws SQLException {
        String sql = "UPDATE dbo.users SET status = ?, updated_at = SYSUTCDATETIME() WHERE id = ?";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setLong(2, userId);
            return ps.executeUpdate() == 1;
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
        Timestamp ct = rs.getTimestamp("created_at");
        u.setCreatedAt(ct == null ? null : ct.toLocalDateTime());
        Timestamp ut = rs.getTimestamp("updated_at");
        u.setUpdatedAt(ut == null ? null : ut.toLocalDateTime());
        return u;
    }
}