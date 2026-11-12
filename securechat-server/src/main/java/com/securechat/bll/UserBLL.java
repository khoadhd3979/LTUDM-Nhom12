package com.securechat.bll;

import com.securechat.dao.ActivityLogDAO;
import com.securechat.dao.DBConnection;
import com.securechat.dao.UserDAO;
import com.securechat.dto.UserDTO;
import com.securechat.exception.BusinessException;
import com.securechat.validation.InputValidator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

public class UserBLL {

    private final UserDAO userDAO = new UserDAO();
    private final ActivityLogDAO auditDAO = new ActivityLogDAO();

    /**
     * [M3-B] Update profile của chính actor.
     * Chỉ fullName/gender/dateOfBirth. Không email/password/status/isSystemAdmin.
     */
    public UserDTO updateProfile(long actorUserId, Map<String, Object> payload, String ipAddress)
            throws SQLException {
        if (actorUserId <= 0)
            throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        InputValidator.validateProfileAllowedFields(payload);

        String fullName = InputValidator.validateFullName(getStr(payload, "fullName"));
        String gender = InputValidator.validateGender(getStr(payload, "gender"));
        var dob = InputValidator.validateDateOfBirth(getStr(payload, "dateOfBirth"));

        try (Connection c = DBConnection.open()) {
            c.setAutoCommit(false);
            try {
                if (!userDAO.updateProfile(c, actorUserId, fullName, gender, dob)) {
                    c.rollback();
                    throw new BusinessException("DB_ERROR", "Cập nhật profile thất bại");
                }
                auditDAO.insert(c, actorUserId, "PROFILE_UPDATED", "USER", actorUserId,
                        "{\"fields\":[\"fullName\",\"gender\",\"dateOfBirth\"]}", ipAddress);
                c.commit();
            } catch (SQLException | BusinessException e) {
                c.rollback();
                throw e;
            }
        }

        return userDAO.findById(actorUserId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Không tìm thấy user"));
    }

    private static String getStr(Map<String, Object> p, String k) {
        if (p == null) return null;
        Object v = p.get(k);
        return v == null ? null : v.toString();
    }
}