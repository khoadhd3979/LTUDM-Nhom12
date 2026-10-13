package dao;

import dto.GroupDTO;
import dto.GroupMemberDTO;
import utils.DBConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class GroupDAO {

    /** Tạo group mới, trả về id vừa sinh. */
    public int createGroup(GroupDTO group) {
        String sql = "INSERT INTO [Groups] (name, created_by, created_at) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, group.getName());
            ps.setInt(2, group.getCreatedBy());
            ps.setTimestamp(3, group.getCreatedAt() != null
                    ? Timestamp.valueOf(group.getCreatedAt())
                    : new Timestamp(System.currentTimeMillis()));

            int affected = ps.executeUpdate();
            if (affected == 0) {
                throw new SQLException("Insert group thất bại");
            }

            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    group.setId(id);
                    return id;
                }
            }
            return -1;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi createGroup: " + e.getMessage(), e);
        }
    }

    /** Thêm 1 member vào group. */
    public boolean addGroupMember(GroupMemberDTO member) {
        String sql = "INSERT INTO GroupMembers (group_id, user_id, role) VALUES (?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, member.getGroupId());
            ps.setInt(2, member.getUserId());
            ps.setString(3, member.getRole());
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi addGroupMember: " + e.getMessage(), e);
        }
    }

    /** Lấy danh sách group mà user tham gia. */
    public List<GroupDTO> getGroupsByUserId(int userId) {
        String sql = "SELECT g.id, g.name, g.created_by, g.created_at "
                + "FROM [Groups] g "
                + "JOIN GroupMembers gm ON gm.group_id = g.id "
                + "WHERE gm.user_id = ? "
                + "ORDER BY g.created_at DESC";

        List<GroupDTO> result = new ArrayList<>();
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setInt(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    GroupDTO group = new GroupDTO();
                    group.setId(rs.getInt("id"));
                    group.setName(rs.getString("name"));
                    group.setCreatedBy(rs.getInt("created_by"));
                    Timestamp ts = rs.getTimestamp("created_at");
                    group.setCreatedAt(ts != null ? ts.toLocalDateTime() : null);
                    result.add(group);
                }
            }
            return result;
        } catch (SQLException e) {
            throw new RuntimeException("Lỗi getGroupsByUserId: " + e.getMessage(), e);
        }
    }
}