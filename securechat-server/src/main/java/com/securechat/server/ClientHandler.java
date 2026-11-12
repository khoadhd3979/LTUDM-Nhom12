package com.securechat.server;

import com.securechat.bll.AuthService;
import com.securechat.bll.ChatService;
import com.securechat.bll.GroupService;
import com.securechat.common.protocol.MessageType;
import com.securechat.common.protocol.Packet;
import com.securechat.common.protocol.ProtocolSerializer;
import com.securechat.dto.GroupDTO;
import com.securechat.dto.MessageDTO;
import com.securechat.dto.UserDTO;
import com.securechat.exception.BusinessException;
import com.securechat.security.*;
import com.securechat.validation.InputValidator;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.PublicKey;
import java.sql.SQLException;
import java.util.*;

public class ClientHandler implements Runnable {

    private static final int MAX_FRAME_BYTES = 1_000_000;
    private static final String PROTO_VERSION = "v1";
    private static final String HKDF_INFO     = "SecureChat-Handshake-v1-HKDF";
    private static final String HANDSHAKE_CTX = "SecureChat-Handshake-v1";

    private final Socket socket;
    private final AuthService authService = new AuthService();
    private final ChatService chatService = new ChatService();
    private final GroupService groupService = new GroupService();
    private final SecureSession session   = new SecureSession();
    private final SecureTransport transport = new SecureTransport(session);

    private final Object writeLock = new Object();
    private volatile long connId = -1L;
    private volatile UserDTO currentUser;
    private volatile PrintWriter out;

    public ClientHandler(Socket socket) { this.socket = socket; }

    /** @return authenticated user id, or -1 if not logged in. */
    public long getCurrentUserId() {
        UserDTO u = this.currentUser;
        return u == null ? -1L : u.getId();
    }

    public boolean isAuthenticated() {
        return this.currentUser != null;
    }

    /**
     * Public thread-safe — dùng cho Phase 4 push từ thread khác.
     * @return true nếu encode + write thành công (không phát hiện lỗi ghi).
     */
    public boolean sendSecure(Packet inner) {
        synchronized (writeLock) {
            try {
                String json = transport.encodeOutbound(inner);
                PrintWriter writer = this.out;
                if (writer == null) return false;
                writer.println(json);
                if (writer.checkError()) {
                    System.err.println("[ERROR] sendSecure write failed connId=" + connId
                            + " type=" + (inner == null ? "null" : inner.getType())
                            + " — closing socket.");
                    closeSocketQuietly();
                    return false;
                }
                return true;
            } catch (Exception e) {
                System.err.println("[ERROR] sendSecure failed connId=" + connId
                        + " type=" + (inner == null ? "null" : inner.getType())
                        + " " + e.getClass().getSimpleName() + ": " + e.getMessage());
                closeSocketQuietly();
                return false;
            }
        }
    }

    @Override
    public void run() {
        ClientConnectionManager.Connection conn = null;
        try {
            conn = ClientConnectionManager.registerConnection(this, socket);
            this.connId = conn.id;
            System.out.println("[CONN] id=" + connId + " remote=" + conn.remote);

            try (socket;
                 BoundedLineReader reader = new BoundedLineReader(socket.getInputStream(), MAX_FRAME_BYTES);
                 PrintWriter writer = new PrintWriter(
                         new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

                this.out = writer;
                socket.setSoTimeout(300_000);
                while (true) {
                    String line;
                    try {
                        line = reader.readLine();
                    } catch (BoundedLineReader.LineTooLongException e) {
                        if (session.getState() == SecureSession.State.ESTABLISHED) {
                            System.err.println("[SEC] connId=" + connId + " frame too large on ESTABLISHED — closing.");
                            break;
                        }
                        writePlain(writer, Packet.error(null, "FRAME_TOO_LARGE"));
                        break;
                    }
                    if (line == null) break;
                    if (line.isBlank()) continue;
                    boolean keepOpen = handleLine(line, writer);
                    if (!keepOpen) break;
                }
            } catch (IOException ignored) {
            } finally {
                this.out = null;
            }
        } finally {
            releaseSession();
            ClientConnectionManager.unregisterConnection(conn);
            System.out.println("[DISC] id=" + connId);
        }
    }

    private boolean handleLine(String line, PrintWriter writer) {
        Packet req;
        try {
            req = ProtocolSerializer.fromJson(line);
        } catch (Exception e) {
            if (session.getState() == SecureSession.State.ESTABLISHED) {
                System.err.println("[SEC] connId=" + connId + " malformed JSON on ESTABLISHED — closing.");
                return false;
            }
            writePlain(writer, Packet.error(null, "BAD_JSON"));
            return true;
        }
        if (req == null || req.getType() == null) {
            if (session.getState() == SecureSession.State.ESTABLISHED) {
                System.err.println("[SEC] connId=" + connId + " missing type on ESTABLISHED — closing.");
                return false;
            }
            writePlain(writer, Packet.error(req == null ? null : req.getRequestId(), "MISSING_TYPE"));
            return true;
        }

        String t = req.getType();
        try {
            if (MessageType.SECURE_MESSAGE.equals(t)) {
                return handleSecure(req, writer);
            }
            switch (t) {
                case MessageType.PING:
                    if (session.getState() == SecureSession.State.ESTABLISHED) {
                        System.err.println("[SEC] connId=" + connId + " plaintext PING on ESTABLISHED — closing.");
                        return false;
                    }
                    writePlain(writer, Packet.ok(MessageType.PONG, req.getRequestId(), tsPayload()));
                    return true;

                case MessageType.CLIENT_HELLO:
                    return handleClientHello(req, writer);

                case MessageType.KEY_EXCHANGE:
                    return handleKeyExchange(req, writer);

                default:
                    if (session.getState() == SecureSession.State.ESTABLISHED) {
                        System.err.println("[SEC] connId=" + connId + " plaintext " + req.getType()
                                + " on ESTABLISHED — closing.");
                        return false;
                    }
                    if (session.getState() == SecureSession.State.INIT) {
                        writePlain(writer, Packet.error(req.getRequestId(), "HANDSHAKE_REQUIRED"));
                        return true;
                    }
                    writePlain(writer, Packet.error(req.getRequestId(), "UNKNOWN_TYPE"));
                    return true;
            }
        } catch (SecurityException se) {
            System.err.println("[SEC] connId=" + connId + " " + t + " reqId=" + req.getRequestId()
                    + ": " + se.getMessage());
            writePlain(writer, Packet.error(req.getRequestId(), "SECURITY_ERROR"));
            return false;
        } catch (Exception e) {
            System.err.println("[ERROR] connId=" + connId + " " + t + " reqId=" + req.getRequestId()
                    + ": " + e.getClass().getSimpleName() + ": " + e.getMessage());
            writePlain(writer, Packet.error(req.getRequestId(), "INTERNAL_ERROR"));
            return false;
        }
    }

    // ============================================================
    // Handshake
    // ============================================================

    private boolean handleClientHello(Packet req, PrintWriter writer) throws Exception {
        if (session.getState() != SecureSession.State.INIT) {
            writePlain(writer, Packet.error(req.getRequestId(), "INVALID_STATE"));
            return true;
        }
        Map<String, Object> p = req.getPayload();
        String version = getStr(p, "version");
        String clientNonceB64 = getStr(p, "clientNonce");
        if (!PROTO_VERSION.equals(version) || clientNonceB64 == null) {
            writePlain(writer, Packet.error(req.getRequestId(), "HELLO_INVALID"));
            return false;
        }
        byte[] clientNonce = Base64.getDecoder().decode(clientNonceB64);
        if (clientNonce.length != 32) {
            writePlain(writer, Packet.error(req.getRequestId(), "HELLO_INVALID"));
            return false;
        }

        byte[] serverNonce = AESUtil.randomBytes(32);
        KeyPair eph = RSAUtil.generateEphemeralKeyPair();

        session.setClientNonce(clientNonce);
        session.setServerNonce(serverNonce);
        session.setEphemeralPrivate(eph.getPrivate());
        session.setEphemeralPublic(eph.getPublic());

        byte[] tbs = SecurityUtils.concatLenPrefixed(
                HANDSHAKE_CTX.getBytes(StandardCharsets.UTF_8),
                ServerIdentityKeyManager.getInstance().keyId().getBytes(StandardCharsets.UTF_8),
                clientNonce, serverNonce, eph.getPublic().getEncoded());
        byte[] sig = RSAUtil.pssSign(ServerIdentityKeyManager.getInstance().privateKey(), tbs);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("version", PROTO_VERSION);
        resp.put("keyId", ServerIdentityKeyManager.getInstance().keyId());
        resp.put("serverNonce", Base64.getEncoder().encodeToString(serverNonce));
        resp.put("ephemeralPublicKey", Base64.getEncoder().encodeToString(eph.getPublic().getEncoded()));
        resp.put("signature", Base64.getEncoder().encodeToString(sig));

        writePlain(writer, Packet.ok(MessageType.SERVER_HELLO, req.getRequestId(), resp));
        session.setState(SecureSession.State.HELLO_SENT);
        return true;
    }

    private boolean handleKeyExchange(Packet req, PrintWriter writer) throws Exception {
        if (session.getState() != SecureSession.State.HELLO_SENT) {
            writePlain(writer, Packet.error(req.getRequestId(), "INVALID_STATE"));
            return true;
        }
        Map<String, Object> p = req.getPayload();
        String encB64 = getStr(p, "encryptedSecret");
        if (encB64 == null) {
            writePlain(writer, Packet.error(req.getRequestId(), "KEY_EXCHANGE_INVALID"));
            return false;
        }
        byte[] secret;
        try {
            secret = RSAUtil.oaepDecrypt(session.getEphemeralPrivate(),
                    Base64.getDecoder().decode(encB64));
        } catch (Exception e) {
            writePlain(writer, Packet.error(req.getRequestId(), "KEY_EXCHANGE_INVALID"));
            return false;
        }
        if (secret.length != 32) {
            AESUtil.wipe(secret);
            writePlain(writer, Packet.error(req.getRequestId(), "KEY_EXCHANGE_INVALID"));
            return false;
        }

        byte[] salt = SecurityUtils.concat(session.getClientNonce(), session.getServerNonce());
        byte[] okm = HKDFUtil.derive(secret, salt, HKDF_INFO.getBytes(StandardCharsets.UTF_8), 64);
        AESUtil.wipe(secret);

        SecretKey c2s = new SecretKeySpec(Arrays.copyOfRange(okm, 0, 32), "AES");
        SecretKey s2c = new SecretKeySpec(Arrays.copyOfRange(okm, 32, 64), "AES");
        session.setC2sKey(c2s);
        session.setS2cKey(s2c);
        session.setEphemeralPrivate(null);
        session.setState(SecureSession.State.KEY_RECEIVED);

        Map<String, Object> confirmPayload = new LinkedHashMap<>();
        confirmPayload.put("confirm", "SERVER_CONFIRMED");
        sendSecure(Packet.ok(MessageType.HANDSHAKE_CONFIRM, req.getRequestId(), confirmPayload));
        return true;
    }

    private boolean handleSecure(Packet req, PrintWriter writer) throws Exception {
        if (session.getState() == SecureSession.State.KEY_RECEIVED) {
            Packet inner = transport.decodeInbound(req);
            if (!isClientConfirm(inner)) {
                writePlain(writer, Packet.error(req.getRequestId(), "KEY_CONFIRM_INVALID"));
                return false;
            }
            session.setState(SecureSession.State.ESTABLISHED);
            return true;
        }
        if (session.getState() != SecureSession.State.ESTABLISHED) {
            writePlain(writer, Packet.error(req.getRequestId(), "HANDSHAKE_REQUIRED"));
            return false;
        }
        Packet inner = transport.decodeInbound(req);
        Packet response = dispatchInner(inner);
        if (response != null) sendSecure(response);
        return true;
    }

    private static boolean isClientConfirm(Packet inner) {
        if (inner == null || !MessageType.HANDSHAKE_CONFIRM.equals(inner.getType())) return false;
        Map<String, Object> p = inner.getPayload();
        return p != null && "CLIENT_CONFIRMED".equals(p.get("confirm"));
    }

    // ============================================================
    // Application dispatch
    // ============================================================

    private Packet dispatchInner(Packet req) {
        if (req == null || req.getType() == null)
            return Packet.error(req == null ? null : req.getRequestId(), "MISSING_TYPE");

        String reqId = req.getRequestId();
        Map<String, Object> p = req.getPayload();

        try {
            switch (req.getType()) {
                case MessageType.PING:
                    return Packet.ok(MessageType.PONG, reqId, tsPayload());

                case MessageType.REGISTER: {
                    if (currentUser != null) return Packet.error(reqId, "ALREADY_LOGGED_IN");
                    UserDTO u = authService.register(
                            getStr(p, "email"), getStr(p, "password"), getStr(p, "fullName"),
                            getStr(p, "gender"), getStr(p, "dateOfBirth"));
                    return Packet.ok(MessageType.OK, reqId, userPayload(u));
                }
                case MessageType.VERIFY_OTP: {
                    if (currentUser != null) return Packet.error(reqId, "ALREADY_LOGGED_IN");
                    UserDTO u = authService.verifyOtp(getStr(p, "email"), getStr(p, "otp"));
                    return Packet.ok(MessageType.OK, reqId, userPayload(u));
                }
                case MessageType.LOGIN: {
                    if (currentUser != null) return Packet.error(reqId, "ALREADY_LOGGED_IN");
                    UserDTO u = authService.login(getStr(p, "email"), getStr(p, "password"));
                    if (!ClientConnectionManager.register(u.getId(), this))
                        return Packet.error(reqId, "ALREADY_LOGGED_IN");
                    this.currentUser = u;
                    return Packet.ok(MessageType.OK, reqId, userPayload(u));
                }
                case MessageType.LOGOUT: {
                    if (currentUser == null) return Packet.error(reqId, "NOT_LOGGED_IN");
                    ClientConnectionManager.unregister(currentUser.getId(), this);
                    this.currentUser = null;
                    return Packet.ok(MessageType.OK, reqId, Collections.emptyMap());
                }
                case MessageType.SEARCH_USER: {
                    long senderId = getCurrentUserId();
                    if (senderId <= 0L) return Packet.error(reqId, "UNAUTHENTICATED");
                    UserDTO found = chatService.searchUser(getStr(p, "email"));
                    return Packet.ok(MessageType.OK, reqId,
                            ChatService.toSearchUserPayload(found));
                }

                // ---- Phase 4 — Chat ----
                case MessageType.SEND_DIRECT_MESSAGE: {
                    long senderId = getCurrentUserId();
                    if (senderId <= 0L) return Packet.error(reqId, "UNAUTHENTICATED");
                    Long rid = getLongOrNull(p, "receiverId");
                    if (rid == null || rid <= 0L) return Packet.error(reqId, "RECEIVER_NOT_FOUND");
                    MessageDTO m = chatService.sendDirect(
                            senderId, rid, getStr(p, "content"), getStr(p, "msgType"));
                    return Packet.ok(MessageType.OK, reqId,
                            ChatService.toPayloadMap(m, true));
                }
                case MessageType.SEND_GROUP_MESSAGE: {
                    long senderId = getCurrentUserId();
                    if (senderId <= 0L) return Packet.error(reqId, "UNAUTHENTICATED");
                    Long gid = getLongOrNull(p, "groupId");
                    if (gid == null || gid <= 0L) return Packet.error(reqId, "GROUP_NOT_FOUND");
                    MessageDTO m = chatService.sendGroup(
                            senderId, gid, getStr(p, "content"), getStr(p, "msgType"));
                    return Packet.ok(MessageType.OK, reqId,
                            ChatService.toPayloadMap(m, false));
                }
                case MessageType.FETCH_DIRECT_HISTORY: {
                    long senderId = getCurrentUserId();
                    if (senderId <= 0L) return Packet.error(reqId, "UNAUTHENTICATED");
                    Long other = getLongOrNull(p, "otherUserId");
                    if (other == null || other <= 0L) return Packet.error(reqId, "RECEIVER_NOT_FOUND");
                    Long before = getLongOrNull(p, "beforeMessageId");
                    int limit = getIntOrDefault(p, "limit", ChatService.DEFAULT_HISTORY_LIMIT);
                    List<MessageDTO> list = chatService.fetchDirectHistory(
                            senderId, other, before, limit);
                    return Packet.ok(MessageType.OK, reqId,
                            historyPayload(list, limit, true));
                }
                case MessageType.FETCH_GROUP_HISTORY: {
                    long senderId = getCurrentUserId();
                    if (senderId <= 0L) return Packet.error(reqId, "UNAUTHENTICATED");
                    Long gid = getLongOrNull(p, "groupId");
                    if (gid == null || gid <= 0L) return Packet.error(reqId, "GROUP_NOT_FOUND");
                    Long before = getLongOrNull(p, "beforeMessageId");
                    int limit = getIntOrDefault(p, "limit", ChatService.DEFAULT_HISTORY_LIMIT);
                    List<MessageDTO> list = chatService.fetchGroupHistory(
                            senderId, gid, before, limit);
                    return Packet.ok(MessageType.OK, reqId,
                            historyPayload(list, limit, false));
                }

                // ---- Phase 4 — Group ----
                case MessageType.CREATE_GROUP: {
                    long senderId = getCurrentUserId();
                    if (senderId <= 0L) return Packet.error(reqId, "UNAUTHENTICATED");
                    GroupDTO g = groupService.createGroup(
                            senderId, getStr(p, "groupName"));
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("groupId", g.getGroupId());
                    payload.put("groupName", g.getGroupName());
                    payload.put("role", g.getRole());
                    payload.put("createdBy", g.getCreatedBy());
                    return Packet.ok(MessageType.OK, reqId, payload);
                }
                case MessageType.ADD_GROUP_MEMBER: {
                    long senderId = getCurrentUserId();
                    if (senderId <= 0L) return Packet.error(reqId, "UNAUTHENTICATED");
                    Long gid = getLongOrNull(p, "groupId");
                    Long target = getLongOrNull(p, "targetUserId");
                    if (gid == null || target == null || gid <= 0L || target <= 0L)
                        return Packet.error(reqId, "INVALID_MESSAGE");
                    groupService.addMember(senderId, gid, target);
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("groupId", gid);
                    payload.put("userId", target);
                    payload.put("role", "MEMBER");
                    return Packet.ok(MessageType.OK, reqId, payload);
                }
                case MessageType.FETCH_MY_GROUPS: {
                    long senderId = getCurrentUserId();
                    if (senderId <= 0L) return Packet.error(reqId, "UNAUTHENTICATED");
                    List<GroupDTO> groups = groupService.fetchMyGroups(senderId);
                    List<Map<String, Object>> list = new ArrayList<>();
                    for (GroupDTO g : groups) {
                        Map<String, Object> gm = new LinkedHashMap<>();
                        gm.put("groupId", g.getGroupId());
                        gm.put("groupName", g.getGroupName());
                        gm.put("createdBy", g.getCreatedBy());
                        if (g.getCreatedAt() != null) gm.put("createdAt", g.getCreatedAt().toString());
                        if (g.getRole() != null) gm.put("role", g.getRole());
                        list.add(gm);
                    }
                    Map<String, Object> payload = new LinkedHashMap<>();
                    payload.put("groups", list);
                    return Packet.ok(MessageType.OK, reqId, payload);
                }

                default:
                    return Packet.error(reqId, "UNKNOWN_TYPE");
            }
        } catch (BusinessException be) {
            return Packet.error(reqId, be.getCode());
        } catch (SQLException sqle) {
            logSqlException(req.getType(), reqId, sqle);
            return Packet.error(reqId, "DB_ERROR");
        } catch (RuntimeException re) {
            System.err.println("[ERROR] connId=" + connId + " inner " + req.getType() + " reqId=" + reqId
                    + " " + re.getClass().getSimpleName() + ": " + re.getMessage());
            return Packet.error(reqId, "INTERNAL_ERROR");
        }
    }

    /**
     * Chuẩn hóa limit MỘT LẦN theo cùng quy tắc của ChatService
     * để hasMore / nextCursor / page size luôn khớp với số message
     * thực tế mà DAO đã trả về (safeLimit + 1).
     */
    private Map<String, Object> historyPayload(List<MessageDTO> raw, int requestedLimit,
                                               boolean includeStatus) {
        int safeLimit = InputValidator.normalizeHistoryLimit(requestedLimit);
        boolean hasMore = raw.size() > safeLimit;
        List<MessageDTO> page = hasMore ? raw.subList(0, safeLimit) : raw;
        List<Map<String, Object>> list = new ArrayList<>();
        for (MessageDTO m : page) list.add(ChatService.toPayloadMap(m, includeStatus));
        Long nextCursor = page.isEmpty() ? null : page.get(page.size() - 1).getMessageId();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("messages", list);
        payload.put("hasMore", hasMore);
        payload.put("nextCursor", nextCursor);
        return payload;
    }

    private void logSqlException(String operation, String reqId, SQLException root) {
        System.err.println("[ERROR] connId=" + connId + " SQL operation=" + operation + " reqId=" + reqId);
        SQLException cur = root;
        int depth = 0;
        while (cur != null && depth < 5) {
            System.err.println("  [sql-chain-" + depth + "]"
                    + " SQLState=" + cur.getSQLState()
                    + " errorCode=" + cur.getErrorCode()
                    + " message=" + sanitize(cur.getMessage()));
            cur = cur.getNextException();
            depth++;
        }
        SQLException first = root;
        if (first.getErrorCode() == 208) System.err.println("  [HINT] errorCode=208: invalid object name.");
        else if (first.getErrorCode() == 207) System.err.println("  [HINT] errorCode=207: invalid column name.");
        else if (first.getErrorCode() == 4060) System.err.println("  [HINT] errorCode=4060: cannot open database.");
        else if (first.getErrorCode() == 2601 || first.getErrorCode() == 2627)
            System.err.println("  [HINT] unique/PK violation.");
        else if (first.getErrorCode() == 547)
            System.err.println("  [HINT] errorCode=547: FK/CHECK constraint violation.");
    }

    private static String sanitize(String msg) {
        if (msg == null) return "null";
        return msg.replaceAll("(?i)(password|otp|token|secret)\\s*=\\s*\\S+", "$1=***");
    }

    private void writePlain(PrintWriter writer, Packet p) {
        synchronized (writeLock) {
            try {
                writer.println(ProtocolSerializer.toJson(p));
                if (writer.checkError()) {
                    System.err.println("[ERROR] writePlain write failed connId=" + connId);
                    closeSocketQuietly();
                }
            } catch (Exception ignored) {}
        }
    }

    private void closeSocketQuietly() {
        try { socket.close(); } catch (IOException ignored) {}
    }

    private void releaseSession() {
        UserDTO u = this.currentUser;
        if (u != null) {
            ClientConnectionManager.unregister(u.getId(), this);
            this.currentUser = null;
        }
    }

    private static Map<String, Object> tsPayload() {
        Map<String, Object> p = new HashMap<>();
        p.put("ts", System.currentTimeMillis());
        return p;
    }

    private static Map<String, Object> userPayload(UserDTO u) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("userId", u.getId());
        m.put("email", u.getEmail());
        m.put("fullName", u.getFullName());
        m.put("status", u.getStatus());
        if (u.getGender() != null) m.put("gender", u.getGender());
        if (u.getDateOfBirth() != null) m.put("dateOfBirth", u.getDateOfBirth().toString());
        return m;
    }

    private static String getStr(Map<String, Object> p, String k) {
        if (p == null) return null;
        Object v = p.get(k);
        return v == null ? null : v.toString();
    }

    private static Long getLongOrNull(Map<String, Object> p, String k) {
        if (p == null) return null;
        Object v = p.get(k);
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException("INVALID_MESSAGE", "Field '" + k + "' không hợp lệ");
        }
    }

    private static int getIntOrDefault(Map<String, Object> p, String k, int def) {
        if (p == null) return def;
        Object v = p.get(k);
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(v.toString());
        } catch (NumberFormatException e) {
            throw new BusinessException("INVALID_MESSAGE", "Field '" + k + "' không hợp lệ");
        }
    }
}