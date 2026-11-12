package com.securechat.bll;

import com.securechat.dao.ActivityLogDAO;
import com.securechat.dao.DBConnection;
import com.securechat.dao.GroupDAO;
import com.securechat.dao.UserDAO;
import com.securechat.dto.GroupDTO;
import com.securechat.exception.BusinessException;
import com.securechat.validation.InputValidator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

public class GroupService {

    private final GroupDAO groupDAO;
    private final UserDAO userDAO;
    private final ActivityLogDAO auditDAO;

    public GroupService() {
        this(new GroupDAO(), new UserDAO(), new ActivityLogDAO());
    }

    /**
     * Test-only constructor (package-private) for injecting DAO variants — used by
     * GroupServiceTest to verify P-1 audit-rollback atomicity. Production code must
     * use the no-arg constructor.
     */
    GroupService(GroupDAO groupDAO, UserDAO userDAO, ActivityLogDAO auditDAO) {
        this.groupDAO = groupDAO;
        this.userDAO = userDAO;
        this.auditDAO = auditDAO;
    }

    /**
     * [M3-B P-5] Tạo nhóm + admin đầu tiên trong cùng transaction của service.
     * KHÔNG ghi bất kỳ audit nào (GROUP_MEMBER_ADDED chỉ phát sinh từ ADD_GROUP_MEMBER).
     */
    public GroupDTO createGroup(long creatorUserId, String rawName) throws SQLException {
        if (creatorUserId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        String name = InputValidator.validateGroupName(rawName);

        long groupId;
        try (Connection c = DBConnection.open()) {
            c.setAutoCommit(false);
            try {
                groupId = groupDAO.createGroupWithAdmin(name, creatorUserId, c);
                c.commit();
            } catch (SQLException | BusinessException e) {
                c.rollback();
                throw e;
            }
        }
        GroupDTO g = new GroupDTO();
        g.setGroupId(groupId);
        g.setGroupName(name);
        g.setCreatedBy(creatorUserId);
        g.setRole("ADMIN");
        return g;
    }

    /**
     * [M3-B P-1] Add member trong MỘT transaction duy nhất.
     * Precedence (baseline M1): GROUP_NOT_FOUND → NOT_GROUP_ADMIN →
     * TARGET_USER_NOT_FOUND → ALREADY_GROUP_MEMBER → insert → audit → commit.
     * Rollback toàn bộ khi bất kỳ bước nào fail. Chỉ dùng overload nhận Connection.
     */
    public boolean addMember(long actorUserId, long groupId, long targetUserId) throws SQLException {
        if (actorUserId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");

        try (Connection c = DBConnection.open()) {
            c.setAutoCommit(false);
            try {
                if (!groupDAO.exists(c, groupId)) {
                    c.rollback();
                    throw new BusinessException("GROUP_NOT_FOUND", "Nhóm không tồn tại");
                }
                if (!"ADMIN".equals(groupDAO.findMemberRole(c, groupId, actorUserId))) {
                    c.rollback();
                    throw new BusinessException("NOT_GROUP_ADMIN", "Chỉ ADMIN mới được thêm thành viên");
                }
                if (userDAO.findById(c, targetUserId).isEmpty()) {
                    c.rollback();
                    throw new BusinessException("TARGET_USER_NOT_FOUND", "Người dùng không tồn tại");
                }
                if (groupDAO.isMember(c, groupId, targetUserId)) {
                    c.rollback();
                    throw new BusinessException("ALREADY_GROUP_MEMBER", "Đã là thành viên nhóm");
                }
                if (!groupDAO.addMember(c, groupId, targetUserId, "MEMBER")) {
                    c.rollback();
                    throw new BusinessException("DB_ERROR", "Thêm thành viên thất bại");
                }
                auditDAO.insert(c, actorUserId, "GROUP_MEMBER_ADDED", "USER", targetUserId,
                        "{\"groupId\":" + groupId + "}", null);
                c.commit();
                return true;
            } catch (SQLException | BusinessException e) {
                try { c.rollback(); } catch (SQLException ignored) {}
                throw e;
            }
        }
    }

    /**
     * [M3-B] Remove member hoặc self-leave.
     * Bảo vệ last-admin invariant — không cho phép xóa ACTIVE ADMIN cuối.
     */
    public boolean removeMember(long actorUserId, long groupId, long targetUserId,
                                 String ipAddress) throws SQLException {
        if (actorUserId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        boolean selfLeave = (actorUserId == targetUserId);

        try (Connection c = DBConnection.open()) {
            c.setAutoCommit(false);
            try {
                List<Long> lockedMemberIds = groupDAO.lockMemberUserIds(c, groupId);
                if (!lockedMemberIds.contains(targetUserId)) {
                    c.rollback();
                    throw new BusinessException("TARGET_NOT_IN_GROUP", "Target không phải thành viên nhóm");
                }
                if (!selfLeave) {
                    String actorRole = groupDAO.findMemberRole(c, groupId, actorUserId);
                    if (!"ADMIN".equals(actorRole)) {
                        c.rollback();
                        throw new BusinessException("NOT_GROUP_ADMIN", "Chỉ ADMIN mới được xóa thành viên");
                    }
                }

                List<Long> activeAdmins = groupDAO.lockActiveAdminIds(c, groupId);
                if (activeAdmins.size() == 1 && activeAdmins.get(0) == targetUserId) {
                    c.rollback();
                    throw new BusinessException("LAST_ADMIN_REQUIRED",
                            "Không thể xóa ADMIN cuối cùng của nhóm");
                }

                if (!groupDAO.removeMember(c, groupId, targetUserId)) {
                    c.rollback();
                    throw new BusinessException("DB_ERROR", "Xóa thành viên thất bại");
                }
                auditDAO.insert(c, actorUserId, "GROUP_MEMBER_REMOVED", "USER", targetUserId,
                        "{\"groupId\":" + groupId + ",\"selfLeave\":" + selfLeave + "}", ipAddress);
                c.commit();
                return true;
            } catch (SQLException | BusinessException e) {
                c.rollback();
                throw e;
            }
        }
    }

    /**
     * [M3-B] SET_GROUP_ROLE — promote/demote.
     * Idempotent: role không đổi → return true ngay, không audit.
     * Demote ADMIN cuối → LAST_ADMIN_REQUIRED.
     */
    public boolean setRole(long actorUserId, long groupId, long targetUserId, String rawNewRole,
                            String ipAddress) throws SQLException {
        if (actorUserId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        String newRole = InputValidator.validateGroupRole(rawNewRole);

        try (Connection c = DBConnection.open()) {
            c.setAutoCommit(false);
            try {
                List<Long> lockedMembers = groupDAO.lockMemberUserIds(c, groupId);
                if (!lockedMembers.contains(targetUserId)) {
                    c.rollback();
                    throw new BusinessException("TARGET_NOT_IN_GROUP", "Target không phải thành viên nhóm");
                }
                String actorRole = groupDAO.findMemberRole(c, groupId, actorUserId);
                if (!"ADMIN".equals(actorRole)) {
                    c.rollback();
                    throw new BusinessException("NOT_GROUP_ADMIN", "Chỉ ADMIN mới được đổi role");
                }
                String currentRole = groupDAO.findMemberRole(c, groupId, targetUserId);
                if (newRole.equals(currentRole)) {
                    c.rollback();
                    return true;
                }

                if ("MEMBER".equals(newRole) && "ADMIN".equals(currentRole)) {
                    List<Long> activeAdmins = groupDAO.lockActiveAdminIds(c, groupId);
                    if (activeAdmins.size() == 1 && activeAdmins.get(0) == targetUserId) {
                        c.rollback();
                        throw new BusinessException("LAST_ADMIN_REQUIRED",
                                "Không thể hạ quyền ADMIN cuối cùng");
                    }
                }

                if (!groupDAO.setRole(c, groupId, targetUserId, newRole)) {
                    c.rollback();
                    throw new BusinessException("DB_ERROR", "Đổi role thất bại");
                }
                auditDAO.insert(c, actorUserId, "GROUP_ROLE_CHANGED", "USER", targetUserId,
                        "{\"groupId\":" + groupId + ",\"newRole\":\"" + newRole + "\"}", ipAddress);
                c.commit();
                return true;
            } catch (SQLException | BusinessException e) {
                c.rollback();
                throw e;
            }
        }
    }

    public List<GroupDTO> fetchMyGroups(long userId) throws SQLException {
        if (userId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        return groupDAO.findGroupsForUser(userId);
    }

    /** [M3-B] Resolve group name (legacy MESSAGE_STATUS). */
    public long resolveGroupByName(long actorId, String groupName) throws SQLException {
        if (actorId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        if (groupName == null || groupName.isBlank())
            throw new BusinessException("GROUP_NOT_FOUND", "Tên nhóm bắt buộc");
        String target = groupName.trim();
        List<GroupDTO> groups = groupDAO.findGroupsForUser(actorId);
        List<GroupDTO> matches = groups.stream()
                .filter(g -> target.equals(g.getGroupName()))
                .toList();
        if (matches.isEmpty())
            throw new BusinessException("GROUP_NOT_FOUND", "Không tìm thấy nhóm");
        if (matches.size() > 1)
            throw new BusinessException("AMBIGUOUS_GROUP_NAME",
                    "Có " + matches.size() + " nhóm trùng tên — cần chỉ định groupId");
        return matches.get(0).getGroupId();
    }
}