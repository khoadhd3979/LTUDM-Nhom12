package com.securechat.bll;

import com.securechat.common.protocol.MessageType;
import com.securechat.common.protocol.Packet;
import com.securechat.dao.ActivityLogDAO;
import com.securechat.dao.DBConnection;
import com.securechat.dao.GroupDAO;
import com.securechat.dao.UserDAO;
import com.securechat.dto.UserDTO;
import com.securechat.exception.BusinessException;
import com.securechat.server.ClientConnectionManager;
import com.securechat.server.ClientHandler;
import com.securechat.validation.InputValidator;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AdminService {

    private final UserDAO userDAO = new UserDAO();
    private final GroupDAO groupDAO = new GroupDAO();
    private final ActivityLogDAO auditDAO = new ActivityLogDAO();

    /** Ensure actor is ACTIVE system-admin. */
    public void requireSystemAdmin(long actorId) throws SQLException {
        if (actorId <= 0) throw new BusinessException("UNAUTHENTICATED", "Chưa đăng nhập");
        UserDTO u = userDAO.findById(actorId)
                .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "Actor không tồn tại"));
        if (!"ACTIVE".equals(u.getStatus()))
            throw new BusinessException("AUTH_FAILED", "Tài khoản không ACTIVE");
        if (!u.isSystemAdmin())
            throw new BusinessException("NOT_SYSTEM_ADMIN", "Yêu cầu system administrator");
    }

    /**
     * [M3-B P-2] Lock user. OD-2 = A: reject nếu target là ACTIVE ADMIN cuối của bất kỳ group.
     * Khi bị chặn, exception CANNOT_LOCK_LAST_ADMIN_OF_GROUP mang details = {groupIds: List<Long>} (sorted ASC).
     */
    public void lockUser(long actorId, long targetId, String reason, String ip) throws SQLException {
        requireSystemAdmin(actorId);
        if (actorId == targetId) throw new BusinessException("CANNOT_LOCK_SELF", "Không thể tự lock");

        try (Connection c = DBConnection.open()) {
            c.setAutoCommit(false);
            try {
                Optional<UserDTO> optT = userDAO.findById(c, targetId);
                if (optT.isEmpty()) {
                    c.rollback();
                    throw new BusinessException("USER_NOT_FOUND", "Target không tồn tại");
                }
                UserDTO target = optT.get();
                if ("LOCKED".equals(target.getStatus())) {
                    c.rollback();
                    throw new BusinessException("USER_ALREADY_LOCKED", "Tài khoản đã bị lock");
                }

                // OD-2: lock group_members (sorted ASC by group_id to avoid deadlock).
                List<Long> targetAdminGroupIds = groupDAO.findAndLockGroupIdsWhereUserIsAdmin(c, targetId);
                List<Long> blockedGroups = new ArrayList<>();
                for (Long gid : targetAdminGroupIds) {
                    List<Long> activeAdmins = groupDAO.lockActiveAdminIds(c, gid);
                    if (activeAdmins.size() == 1 && activeAdmins.get(0) == targetId) {
                        blockedGroups.add(gid);
                    }
                }
                if (!blockedGroups.isEmpty()) {
                    c.rollback();
                    Map<String, Object> details = new LinkedHashMap<>();
                    details.put("groupIds", blockedGroups);
                    throw new BusinessException("CANNOT_LOCK_LAST_ADMIN_OF_GROUP",
                            "Không thể lock — target là ACTIVE ADMIN cuối của "
                                    + blockedGroups.size() + " group",
                            details);
                }

                if (!userDAO.updateStatus(c, targetId, "LOCKED")) {
                    c.rollback();
                    throw new BusinessException("DB_ERROR", "Update status LOCKED thất bại");
                }
                String meta = "{\"reason\":\"" + esc(reason) + "\"}";
                auditDAO.insert(c, actorId, "USER_LOCKED", "USER", targetId, meta, ip);
                c.commit();
            } catch (SQLException | BusinessException e) {
                try { c.rollback(); } catch (SQLException ignored) {}
                throw e;
            }
        }
    }

    public void unlockUser(long actorId, long targetId, String ip) throws SQLException {
        requireSystemAdmin(actorId);
        try (Connection c = DBConnection.open()) {
            c.setAutoCommit(false);
            try {
                Optional<UserDTO> optT = userDAO.findById(c, targetId);
                if (optT.isEmpty()) {
                    c.rollback();
                    throw new BusinessException("USER_NOT_FOUND", "Target không tồn tại");
                }
                if (!"LOCKED".equals(optT.get().getStatus())) {
                    c.rollback();
                    throw new BusinessException("USER_NOT_LOCKED", "Tài khoản không ở trạng thái LOCKED");
                }
                if (!userDAO.updateStatus(c, targetId, "ACTIVE")) {
                    c.rollback();
                    throw new BusinessException("DB_ERROR", "Update status ACTIVE thất bại");
                }
                auditDAO.insert(c, actorId, "USER_UNLOCKED", "USER", targetId, "{}", ip);
                c.commit();
            } catch (SQLException | BusinessException e) {
                try { c.rollback(); } catch (SQLException ignored) {}
                throw e;
            }
        }
    }

    /**
     * [M3-B P-8] SYSTEM_BROADCAST fanout.
     * - snapshotOnlineHandlers() là nguồn recipient.
     * - Loại trừ handler của chính admin đang gọi (so sánh identity ==).
     * - Partial failure: 1 handler lỗi không ảnh hưởng handler khác.
     * - Audit SYSTEM_BROADCAST ghi ngoài transaction (best-effort).
     */
    public BroadcastResult broadcast(long actorId, String rawMessage, String ip) throws SQLException {
        requireSystemAdmin(actorId);
        String msg = InputValidator.validateBroadcastMessage(rawMessage);

        ClientHandler caller = ClientConnectionManager.findByUserId(actorId);
        List<ClientHandler> targets = ClientConnectionManager.snapshotOnlineHandlers();

        int success = 0, failed = 0;
        for (ClientHandler h : targets) {
            if (h == caller) continue;   // loại trừ caller
            try {
                Packet push = Packet.ok(MessageType.SYSTEM_BROADCAST,
                        "push-broadcast-" + System.nanoTime(),
                        Map.of("message", msg));
                if (h.sendSecure(push)) success++;
                else failed++;
            } catch (Exception e) {
                failed++;
            }
        }

        int attempted = success + failed;
        String meta = "{\"recipientCount\":" + attempted +
                      ",\"successCount\":" + success +
                      ",\"failedCount\":" + failed +
                      ",\"messagePreview\":\"" + esc(truncate(msg, 100)) + "\"}";
        try {
            auditDAO.insertStandalone(actorId, "SYSTEM_BROADCAST", "BROADCAST", null, meta, ip);
        } catch (SQLException e) {
            System.err.println("[WARN] Broadcast audit fail: " + e.getMessage());
        }

        return new BroadcastResult(attempted, success, failed);
    }

    /**
     * [M3-B P-10 — PENDING] USER_STATS trả đúng 6 metric đã triển khai.
     * §5.2.8 M3-A Design vẫn PENDING; không tự chốt thêm metric.
     */
    public Map<String, Object> userStats(long actorId) throws SQLException {
        requireSystemAdmin(actorId);
        Map<String, Object> out = new HashMap<>();
        try (Connection c = DBConnection.open()) {
            out.put("totalUsers",   countByStatus(c, null));
            out.put("activeUsers",  countByStatus(c, "ACTIVE"));
            out.put("pendingUsers", countByStatus(c, "PENDING"));
            out.put("lockedUsers",  countByStatus(c, "LOCKED"));
            try (var ps = c.prepareStatement("SELECT COUNT(*) FROM dbo.groups");
                 var rs = ps.executeQuery()) {
                if (rs.next()) out.put("totalGroups", rs.getLong(1));
            }
        }
        out.put("onlineUsers", ClientConnectionManager.onlineCount());
        return out;
    }

    private long countByStatus(Connection c, String status) throws SQLException {
        String sql = status == null
                ? "SELECT COUNT(*) FROM dbo.users"
                : "SELECT COUNT(*) FROM dbo.users WHERE status = ?";
        try (var ps = c.prepareStatement(sql)) {
            if (status != null) ps.setString(1, status);
            try (var rs = ps.executeQuery()) { return rs.next() ? rs.getLong(1) : 0; }
        }
    }

    public static final class BroadcastResult {
        public final int total, success, failed;
        public BroadcastResult(int t, int s, int f) { total = t; success = s; failed = f; }
    }

    private static String truncate(String s, int n) { return s.length() <= n ? s : s.substring(0, n); }
    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}