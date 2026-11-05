package com.securechat.bll;

import com.securechat.dao.GroupDAO;
import com.securechat.dao.UserDAO;
import com.securechat.dto.GroupDTO;
import com.securechat.exception.BusinessException;
import com.securechat.validation.InputValidator;

import java.sql.SQLException;
import java.util.List;

public class GroupService {

    private final GroupDAO groupDAO = new GroupDAO();
    private final UserDAO userDAO = new UserDAO();

    public GroupDTO createGroup(long creatorUserId, String rawName) throws SQLException {
        if (creatorUserId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        String name = InputValidator.validateGroupName(rawName);
        long groupId = groupDAO.createGroupWithAdmin(name, creatorUserId);

        GroupDTO g = new GroupDTO();
        g.setGroupId(groupId);
        g.setGroupName(name);
        g.setCreatedBy(creatorUserId);
        g.setRole("ADMIN");
        return g;
    }

    public boolean addMember(long actorUserId, long groupId, long targetUserId)
            throws SQLException {
        if (actorUserId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        if (!groupDAO.exists(groupId))
            throw new BusinessException("GROUP_NOT_FOUND", "Nhóm không tồn tại");
        String actorRole = groupDAO.getMemberRole(groupId, actorUserId);
        if (!"ADMIN".equals(actorRole))
            throw new BusinessException("NOT_GROUP_ADMIN", "Chỉ ADMIN mới được thêm thành viên");
        if (!userDAO.existsById(targetUserId))
            throw new BusinessException("TARGET_USER_NOT_FOUND", "Người dùng không tồn tại");
        if (groupDAO.isMember(groupId, targetUserId))
            throw new BusinessException("ALREADY_GROUP_MEMBER", "Người dùng đã là thành viên nhóm");

        return groupDAO.addMember(groupId, targetUserId, "MEMBER");
    }

    public List<GroupDTO> fetchMyGroups(long userId) throws SQLException {
        if (userId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        return groupDAO.findGroupsForUser(userId);
    }
}