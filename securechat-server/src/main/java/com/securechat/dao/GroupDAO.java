package com.securechat.dao;

import com.securechat.dto.GroupDTO;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class GroupDAO {

    public long createGroupWithAdmin(String groupName, long creatorUserId) throws SQLException {
        String sqlGroup = "INSERT INTO dbo.groups (group_name, created_by) VALUES (?, ?)";
        String sqlMember = "INSERT INTO dbo.group_members (group_id, user_id, role) VALUES (?, ?, 'ADMIN')";
        try (Connection c = DBConnection.open()) {
            boolean original = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                long groupId;
                try (PreparedStatement ps = c.prepareStatement(sqlGroup, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, groupName);
                    ps.setLong(2, creatorUserId);
                    if (ps.executeUpdate() != 1) throw new SQLException("Insert group thất bại.");
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (!rs.next()) throw new SQLException("Insert group không trả về ID.");
                        groupId = rs.getLong(1);
                    }
                }
                try (PreparedStatement ps = c.prepareStatement(sqlMember)) {
                    ps.setLong(1, groupId);
                    ps.setLong(2, creatorUserId);
                    if (ps.executeUpdate() != 1) throw new SQLException("Insert admin member thất bại.");
                }
                c.commit();
                return groupId;
            } catch (SQLException e) {
                try { c.rollback(); } catch (SQLException ignored) {}
                throw e;
            } finally {
                try { c.setAutoCommit(original); } catch (SQLException ignored) {}
            }
        }
    }

    public boolean addMember(long groupId, long userId, String role) throws SQLException {
        String sql = "INSERT INTO dbo.group_members (group_id, user_id, role) VALUES (?, ?, ?)";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            ps.setLong(2, userId);
            ps.setString(3, role);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean exists(long groupId) throws SQLException {
        String sql = "SELECT 1 FROM dbo.groups WHERE group_id = ?";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean isMember(long groupId, long userId) throws SQLException {
        String sql = "SELECT 1 FROM dbo.group_members WHERE group_id = ? AND user_id = ?";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public String getMemberRole(long groupId, long userId) throws SQLException {
        String sql = "SELECT role FROM dbo.group_members WHERE group_id = ? AND user_id = ?";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("role") : null;
            }
        }
    }

    public List<Long> findMemberUserIds(long groupId) throws SQLException {
        String sql = "SELECT user_id FROM dbo.group_members WHERE group_id = ?";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getLong("user_id"));
                return ids;
            }
        }
    }

    public Optional<GroupDTO> findById(long groupId) throws SQLException {
        String sql = "SELECT group_id, group_name, created_by, created_at FROM dbo.groups WHERE group_id = ?";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                GroupDTO g = new GroupDTO();
                g.setGroupId(rs.getLong("group_id"));
                g.setGroupName(rs.getString("group_name"));
                g.setCreatedBy(rs.getLong("created_by"));
                LocalDateTime ca = rs.getObject("created_at", LocalDateTime.class);
                g.setCreatedAt(ca);
                return Optional.of(g);
            }
        }
    }

    public List<GroupDTO> findGroupsForUser(long userId) throws SQLException {
        String sql = "SELECT g.group_id, g.group_name, g.created_by, g.created_at, gm.role " +
                     "FROM dbo.groups g " +
                     "INNER JOIN dbo.group_members gm ON gm.group_id = g.group_id " +
                     "WHERE gm.user_id = ? " +
                     "ORDER BY g.created_at DESC, g.group_id DESC";
        try (Connection c = DBConnection.open();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<GroupDTO> groups = new ArrayList<>();
                while (rs.next()) {
                    GroupDTO g = new GroupDTO();
                    g.setGroupId(rs.getLong("group_id"));
                    g.setGroupName(rs.getString("group_name"));
                    g.setCreatedBy(rs.getLong("created_by"));
                    LocalDateTime ca = rs.getObject("created_at", LocalDateTime.class);
                    g.setCreatedAt(ca);
                    g.setRole(rs.getString("role"));
                    groups.add(g);
                }
                return groups;
            }
        }
    }
}