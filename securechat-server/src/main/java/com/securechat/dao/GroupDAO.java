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

    // ============================================================
    // Create group
    // ============================================================

    /** Standalone createGroupWithAdmin — opens its own connection & commits. */
    public long createGroupWithAdmin(String groupName, long creatorUserId) throws SQLException {
        try (Connection c = DBConnection.open()) {
            c.setAutoCommit(false);
            try {
                long gid = createGroupWithAdmin(groupName, creatorUserId, c);
                c.commit();
                return gid;
            } catch (SQLException e) {
                try { c.rollback(); } catch (SQLException ignored) {}
                throw e;
            }
        }
    }

    /**
     * Caller-managed createGroupWithAdmin — uses caller's Connection.
     * Does NOT open/commit/rollback/close the connection; caller owns transaction.
     */
    public long createGroupWithAdmin(String groupName, long creatorUserId, Connection c) throws SQLException {
        String sqlGroup  = "INSERT INTO dbo.groups (group_name, created_by) VALUES (?, ?)";
        String sqlMember = "INSERT INTO dbo.group_members (group_id, user_id, role) VALUES (?, ?, 'ADMIN')";
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
        return groupId;
    }

    // ============================================================
    // Members
    // ============================================================

    /** Standalone addMember — opens its own connection & commits. */
    public boolean addMember(long groupId, long userId, String role) throws SQLException {
        try (Connection c = DBConnection.open()) {
            return addMember(c, groupId, userId, role);
        }
    }

    /** Caller-managed addMember — uses caller's Connection; no commit/rollback/close. */
    public boolean addMember(Connection c, long groupId, long userId, String role) throws SQLException {
        String sql = "INSERT INTO dbo.group_members (group_id, user_id, role) VALUES (?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            ps.setLong(2, userId);
            ps.setString(3, role);
            return ps.executeUpdate() == 1;
        }
    }

    // ============================================================
    // Existence
    // ============================================================

    public boolean exists(long groupId) throws SQLException {
        try (Connection c = DBConnection.open()) {
            return exists(c, groupId);
        }
    }

    public boolean exists(Connection c, long groupId) throws SQLException {
        String sql = "SELECT 1 FROM dbo.groups WHERE group_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    // ============================================================
    // Membership queries
    // ============================================================

    public boolean isMember(long groupId, long userId) throws SQLException {
        try (Connection c = DBConnection.open()) {
            return isMember(c, groupId, userId);
        }
    }

    public boolean isMember(Connection c, long groupId, long userId) throws SQLException {
        String sql = "SELECT 1 FROM dbo.group_members WHERE group_id = ? AND user_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            ps.setLong(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public String getMemberRole(long groupId, long userId) throws SQLException {
        try (Connection c = DBConnection.open()) {
            return findMemberRole(c, groupId, userId);
        }
    }

    public String findMemberRole(Connection c, long groupId, long userId) throws SQLException {
        String sql = "SELECT role FROM dbo.group_members WHERE group_id = ? AND user_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
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

    // ============================================================
    // Lookups
    // ============================================================

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

    // ============================================================
    // [M3-B] Locking / mutation methods (transaction-managed by caller)
    // ============================================================

    /**
     * Lock all group_members rows for a group (UPDLOCK, HOLDLOCK).
     * Returns user_id list. Serializes concurrent remove/setRole operations.
     */
    public List<Long> lockMemberUserIds(Connection c, long groupId) throws SQLException {
        String sql = "SELECT user_id FROM dbo.group_members WITH (UPDLOCK, HOLDLOCK) " +
                     "WHERE group_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getLong(1));
                return ids;
            }
        }
    }

    /**
     * Lock group_members joined with users; return ACTIVE ADMIN user_id list of the group.
     * Used to check last-admin invariant.
     */
    public List<Long> lockActiveAdminIds(Connection c, long groupId) throws SQLException {
        String sql = "SELECT gm.user_id FROM dbo.group_members gm WITH (UPDLOCK, HOLDLOCK) " +
                     "INNER JOIN dbo.users u ON u.id = gm.user_id " +
                     "WHERE gm.group_id = ? AND gm.role = 'ADMIN' AND u.status = 'ACTIVE'";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getLong(1));
                return ids;
            }
        }
    }

    public boolean removeMember(Connection c, long groupId, long userId) throws SQLException {
        String sql = "DELETE FROM dbo.group_members WHERE group_id = ? AND user_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, groupId);
            ps.setLong(2, userId);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean setRole(Connection c, long groupId, long userId, String role) throws SQLException {
        String sql = "UPDATE dbo.group_members SET role = ? WHERE group_id = ? AND user_id = ?";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, role);
            ps.setLong(2, groupId);
            ps.setLong(3, userId);
            return ps.executeUpdate() == 1;
        }
    }

    /**
     * Lock all groups where user is currently ADMIN (UPDLOCK, HOLDLOCK).
     * Sorted ASC by group_id to avoid deadlocks when locking multiple groups.
     */
    public List<Long> findAndLockGroupIdsWhereUserIsAdmin(Connection c, long userId)
            throws SQLException {
        String sql = "SELECT gm.group_id FROM dbo.group_members gm WITH (UPDLOCK, HOLDLOCK) " +
                     "INNER JOIN dbo.groups g ON g.group_id = gm.group_id " +
                     "WHERE gm.user_id = ? AND gm.role = 'ADMIN' " +
                     "ORDER BY gm.group_id ASC";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                List<Long> ids = new ArrayList<>();
                while (rs.next()) ids.add(rs.getLong(1));
                return ids;
            }
        }
    }
}