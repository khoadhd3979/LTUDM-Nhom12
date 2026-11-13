package com.securechat.bll;

import com.securechat.common.protocol.MessageType;
import com.securechat.common.protocol.Packet;
import com.securechat.dao.GroupDAO;
import com.securechat.dao.UserDAO;
import com.securechat.dto.GroupDTO;
import com.securechat.dto.UserDTO;
import com.securechat.exception.BusinessException;
import com.securechat.server.ClientConnectionManager;
import com.securechat.server.ClientHandler;

import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * [M4-B] Stateless per-chunk FILE_CHUNK relay.
 *
 * Nguyên tắc:
 *  - Không buffer, không lưu file, không DB, không migration.
 *  - Mỗi chunk độc lập: validate đã làm ở InputValidator (ClientHandler dispatch); service này
 *    chỉ resolve đích + build relay packet + gửi qua sendSecure của handler đích.
 *  - KD-2: bổ sung senderId + senderEmail vào payload chuyển tiếp; sender lấy từ session.
 *  - KD-3: stateless trên từng chunk.
 *  - KD-4: direct receiver không online → RECEIVER_NOT_FOUND; email không resolve user ACTIVE
 *          → USER_NOT_FOUND.
 *  - KD-5: chunk hợp lệ → caller trả OK với payload rỗng.
 *  - Không ghi activity_logs, không log data.
 *
 * Seam test: giao tiếp với ClientConnectionManager qua interface Sender. Production dùng
 * DefaultSender (ủy thác static). Test truyền Sender tùy biến (không đổi ClientConnectionManager).
 */
public class FileRelayService {

    /** Seam test tối thiểu: tra handler theo userId + gửi packet tới handler đích. */
    public interface Sender {
        ClientHandler findByUserId(long userId);
        boolean send(ClientHandler target, Packet packet);
    }

    public static final class DefaultSender implements Sender {
        @Override public ClientHandler findByUserId(long userId) {
            return ClientConnectionManager.findByUserId(userId);
        }
        @Override public boolean send(ClientHandler target, Packet packet) {
            return target.sendSecure(packet);
        }
    }

    private final UserDAO userDAO;
    private final GroupDAO groupDAO;
    private final GroupService groupService;
    private final Sender sender;

    public FileRelayService() {
        this(new UserDAO(), new GroupDAO(), new GroupService(), new DefaultSender());
    }

    /** Package-private constructor cho test — không dùng ngoài test. */
    FileRelayService(UserDAO userDAO, GroupDAO groupDAO, GroupService groupService, Sender sender) {
        this.userDAO = userDAO;
        this.groupDAO = groupDAO;
        this.groupService = groupService;
        this.sender = sender;
    }

    /**
     * Relay FILE_CHUNK tới một receiver direct.
     * Resolve email → User ACTIVE (USER_NOT_FOUND nếu không tìm thấy).
     * Nếu target không online (findByUserId null) → RECEIVER_NOT_FOUND.
     * Nếu send thất bại (sendSecure false) → best-effort, không throw.
     */
    public void relayDirect(UserDTO senderUser, String receiverEmail,
                            Map<String, Object> chunk) throws SQLException {
        Optional<UserDTO> targetOpt = userDAO.findActiveByEmail(receiverEmail);
        if (targetOpt.isEmpty()) {
            throw new BusinessException("USER_NOT_FOUND",
                    "Không tìm thấy người nhận ACTIVE với email: " + receiverEmail);
        }
        long receiverId = targetOpt.get().getId();

        ClientHandler targetHandler = sender.findByUserId(receiverId);
        if (targetHandler == null) {
            throw new BusinessException("RECEIVER_NOT_FOUND", "Người nhận không online");
        }

        Map<String, Object> relayPayload = buildRelayPayload(chunk, senderUser, null, null);
        String reqId = buildRequestId(chunk);
        Packet push = Packet.ok(MessageType.FILE_CHUNK, reqId, relayPayload);
        sender.send(targetHandler, push);   // best-effort
    }

    /**
     * Relay FILE_CHUNK tới mọi thành viên online của group (trừ sender).
     * Resolve group name → groupId (GROUP_NOT_FOUND / AMBIGUOUS_GROUP_NAME từ resolveGroupByName).
     * Không thành viên nào online → no-op, không lỗi.
     */
    public void relayGroup(UserDTO senderUser, String groupName,
                           Map<String, Object> chunk) throws SQLException {
        long groupId = groupService.resolveGroupByName(senderUser.getId(), groupName);
        List<Long> memberIds = groupDAO.findMemberUserIds(groupId);
        String canonicalGroupName = groupDAO.findById(groupId)
                .map(GroupDTO::getGroupName)
                .orElse(groupName);

        Map<String, Object> relayPayload = buildRelayPayload(
                chunk, senderUser, groupId, canonicalGroupName);
        String reqId = buildRequestId(chunk);
        Packet push = Packet.ok(MessageType.FILE_CHUNK, reqId, relayPayload);

        for (Long mid : memberIds) {
            if (mid == null || mid == senderUser.getId()) continue;
            ClientHandler h = sender.findByUserId(mid);
            if (h == null) continue;
            sender.send(h, push);   // best-effort, không throw khi 1 handler fail
        }
    }

    private static String buildRequestId(Map<String, Object> chunk) {
        return "push-file-" + chunk.get("fileId") + "-" + chunk.get("chunkIndex");
    }

    private static Map<String, Object> buildRelayPayload(Map<String, Object> chunk,
                                                        UserDTO senderUser,
                                                        Long groupId,
                                                        String canonicalGroupName) {
        Map<String, Object> p = new LinkedHashMap<>();
        // 5 field gốc — giữ nguyên byte, không decode/re-encode data
        p.put("fileId", chunk.get("fileId"));
        p.put("fileName", chunk.get("fileName"));
        p.put("chunkIndex", chunk.get("chunkIndex"));
        p.put("totalChunks", chunk.get("totalChunks"));
        p.put("data", chunk.get("data"));
        // KD-2: sender identity
        p.put("senderId", senderUser.getId());
        p.put("senderEmail", senderUser.getEmail());
        // Group: bổ sung groupId + groupName canonical
        if (groupId != null) {
            p.put("groupId", groupId);
            p.put("groupName", canonicalGroupName);
        }
        return p;
    }
}