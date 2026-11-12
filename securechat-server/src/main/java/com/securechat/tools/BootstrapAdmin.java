package com.securechat.tools;

import com.securechat.config.AppConfig;
import com.securechat.dao.ActivityLogDAO;
import com.securechat.dao.DBConnection;
import com.securechat.dao.UserDAO;
import com.securechat.dto.UserDTO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Optional;

/**
 * [M3-B] Bootstrap system administrator — chạy THỦ CÔNG một lần.
 *
 * Cách chạy:
 *   1. Đảm bảo user đã đăng ký và ACTIVE trong database.
 *   2. Set env SC_BOOTSTRAP_ADMIN_EMAIL=<email>.
 *   3. java -cp securechat-server.jar com.securechat.tools.BootstrapAdmin
 *
 * Không tự động chạy từ ServerMain.main.
 * Không tạo user mới.
 * Không tự đặt password.
 * Không log password hoặc dữ liệu nhạy cảm.
 */
public final class BootstrapAdmin {

    private BootstrapAdmin() {}

    public static void main(String[] args) {
        String email = AppConfig.bootstrapAdminEmail();
        if (email == null) {
            System.err.println("[BOOTSTRAP] Missing env SC_BOOTSTRAP_ADMIN_EMAIL — abort.");
            System.exit(2);
        }

        UserDAO userDAO = new UserDAO();
        ActivityLogDAO auditDAO = new ActivityLogDAO();

        try (Connection c = DBConnection.open()) {
            c.setAutoCommit(false);
            try {
                Optional<UserDTO> opt = userDAO.getUserByEmail(email);
                if (opt.isEmpty()) {
                    c.rollback();
                    System.err.println("[BOOTSTRAP] User not found: " + email + " — abort.");
                    System.exit(3);
                }
                UserDTO u = opt.get();
                if (!"ACTIVE".equals(u.getStatus())) {
                    c.rollback();
                    System.err.println("[BOOTSTRAP] User is not ACTIVE (status=" + u.getStatus() + ") — abort.");
                    System.exit(4);
                }

                if (u.isSystemAdmin()) {
                    System.out.println("[BOOTSTRAP] User " + email + " is already system-admin — no-op.");
                    c.rollback();
                    return;
                }

                // Update
                try (var ps = c.prepareStatement(
                        "UPDATE dbo.users SET is_system_admin = 1, updated_at = SYSUTCDATETIME() " +
                        "WHERE id = ? AND status = 'ACTIVE'")) {
                    ps.setLong(1, u.getId());
                    if (ps.executeUpdate() != 1) {
                        c.rollback();
                        System.err.println("[BOOTSTRAP] Update affected 0 rows — abort.");
                        System.exit(5);
                    }
                }

                // Audit (cùng transaction)
                auditDAO.insert(c, null, "SYSTEM_ADMIN_BOOTSTRAPPED", "USER", u.getId(),
                        "{\"email\":\"" + escape(email) + "\"}", null);

                c.commit();
                System.out.println("[BOOTSTRAP] User " + email + " promoted to system-admin. id=" + u.getId());
            } catch (SQLException e) {
                try { c.rollback(); } catch (SQLException ignored) {}
                System.err.println("[BOOTSTRAP] SQL error: " + e.getMessage());
                System.exit(6);
            }
        } catch (SQLException e) {
            System.err.println("[BOOTSTRAP] Cannot open connection: " + e.getMessage());
            System.exit(7);
        }
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}