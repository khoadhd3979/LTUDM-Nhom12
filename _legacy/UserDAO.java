// File: src/dao/UserDAO.java
package dao;

import dto.UserDTO;
import utils.DBConnection;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class UserDAO {

    /** Thêm user mới, trả về id vừa sinh. */
    public int insertUser(UserDTO user) {
        String sql = "INSERT INTO Users (email, password_hash, full_name, gender, dob, status) "
                + "VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, user.getEmail());
            ps.setString(2, user.getPasswordHash());
            ps.setString(3, user.getFullName());
            ps.setString(4, user.getGender());
            ps.setDate(5, user.getDob() != null ? Date.valueOf(user.getDob()) : null);
            ps.setString(6, user.getStatus() != null ? user.getStatus() : "OFFLINE");

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Insert user thất bại, không có dòng nào bị ảnh hưởng");
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    user.setId(id);
                    return id;
                }
            }
            return -1;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi insertUser: " + e.getMessage(), e);
        }
    }

    /** Lấy user theo email (email là UNIQUE). */
    public UserDTO getUserByEmail(String email) {
        String sql = "SELECT id, email, password_hash, full_name, gender, dob, status "
                + "FROM Users WHERE email = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, email);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
            return null;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi getUserByEmail: " + e.getMessage(), e);
        }
    }

    /** Cập nhật trạng thái user (ONLINE / OFFLINE / BLOCKED). */
    public boolean updateStatus(int userId, String status) {
        String sql = "UPDATE Users SET status = ? WHERE id = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, status);
            ps.setInt(2, userId);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi updateStatus: " + e.getMessage(), e);
        }
    }

    /** Block user. */
    public boolean blockUser(int userId) {
        return updateStatus(userId, "BLOCKED");
    }

    private UserDTO mapRow(ResultSet rs) throws SQLException {
        UserDTO user = new UserDTO();
        user.setId(rs.getInt("id"));
        user.setEmail(rs.getString("email"));
        user.setPasswordHash(rs.getString("password_hash"));
        user.setFullName(rs.getString("full_name"));
        user.setGender(rs.getString("gender"));
        Date dob = rs.getDate("dob");
        user.setDob(dob != null ? dob.toLocalDate() : null);
        user.setStatus(rs.getString("status"));
        return user;
    }
}