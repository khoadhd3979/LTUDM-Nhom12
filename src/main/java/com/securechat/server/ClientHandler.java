package com.securechat.server;

import com.securechat.bll.AuthService;
import com.securechat.common.protocol.MessageType;
import com.securechat.common.protocol.Packet;
import com.securechat.common.protocol.ProtocolSerializer;
import com.securechat.dto.UserDTO;
import com.securechat.exception.BusinessException;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class ClientHandler implements Runnable {

    private static final int MAX_FRAME_BYTES = 1_000_000; // 1 MB

    private final Socket socket;
    private final AuthService authService = new AuthService();

    /** Non-null only after successful LOGIN. Volatile: read from shutdown hook potentially. */
    private volatile UserDTO currentUser;

    public ClientHandler(Socket socket) { this.socket = socket; }

    @Override
    public void run() {
        String remote = String.valueOf(socket.getRemoteSocketAddress());
        System.out.println("[CONN] " + remote);

        try (socket;
             BoundedLineReader reader = new BoundedLineReader(socket.getInputStream(), MAX_FRAME_BYTES);
             PrintWriter out = new PrintWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true)) {

            while (true) {
                String line;
                try {
                    line = reader.readLine();
                } catch (BoundedLineReader.LineTooLongException e) {
                    // Cannot resync: framing is lost. Send best-effort error, then close.
                    safeWritePacket(out, Packet.error(null, "FRAME_TOO_LARGE"));
                    break;
                }
                if (line == null) break;     // EOF
                if (line.isBlank()) continue;

                boolean keepOpen = handleLine(line, out);
                if (!keepOpen) break;
            }
        } catch (IOException e) {
            // client dropped — no stack trace
        } finally {
            releaseSession();
            System.out.println("[DISC] " + remote);
        }
    }

    private boolean handleLine(String line, PrintWriter out) {
        Packet req;
        try {
            req = ProtocolSerializer.fromJson(line);
        } catch (Exception ex) {
            safeWritePacket(out, Packet.error(null, "BAD_JSON"));
            return true;
        }

        Packet resp = dispatch(req);
        if (resp != null) {
            try {
                safeWrite(out, ProtocolSerializer.toJson(resp));
            } catch (Exception ignored) { return false; }
        }
        return true;
    }

    private Packet dispatch(Packet req) {
        if (req == null || req.getType() == null) {
            return Packet.error(req == null ? null : req.getRequestId(), "MISSING_TYPE");
        }
        String reqId = req.getRequestId();
        Map<String, Object> p = req.getPayload();

        try {
            switch (req.getType()) {
                case MessageType.PING:
                    return Packet.ok(MessageType.PONG, reqId, tsPayload());

                case MessageType.REGISTER: {
                    if (currentUser != null) return Packet.error(reqId, "ALREADY_LOGGED_IN");
                    UserDTO u = authService.register(
                            getStr(p, "email"),
                            getStr(p, "password"),
                            getStr(p, "fullName"),
                            getStr(p, "gender"),
                            getStr(p, "dateOfBirth"));
                    return Packet.ok(MessageType.OK, reqId, userPayload(u));
                }

                case MessageType.VERIFY_OTP: {
                    if (currentUser != null) return Packet.error(reqId, "ALREADY_LOGGED_IN");
                    UserDTO u = authService.verifyOtp(
                            getStr(p, "email"), getStr(p, "otp"));
                    return Packet.ok(MessageType.OK, reqId, userPayload(u));
                }

                case MessageType.LOGIN: {
                    if (currentUser != null) return Packet.error(reqId, "ALREADY_LOGGED_IN");
                    UserDTO u = authService.login(
                            getStr(p, "email"), getStr(p, "password"));
                    if (!ClientConnectionManager.register(u.getId(), this)) {
                        return Packet.error(reqId, "ALREADY_LOGGED_IN");
                    }
                    this.currentUser = u;
                    return Packet.ok(MessageType.OK, reqId, userPayload(u));
                }

                case MessageType.LOGOUT: {
                    if (currentUser == null) return Packet.error(reqId, "NOT_LOGGED_IN");
                    ClientConnectionManager.unregister(currentUser.getId(), this);
                    this.currentUser = null;
                    return Packet.ok(MessageType.OK, reqId, Collections.emptyMap());
                }

                default:
                    return Packet.error(reqId, "UNKNOWN_TYPE");
            }
        } catch (BusinessException be) {
            return Packet.error(reqId, be.getCode());
        } catch (SQLException sqle) {
            System.err.println("[ERROR] " + req.getType() + " reqId=" + reqId
                    + " SQLState=" + sqle.getSQLState() + " msg=" + sqle.getMessage());
            return Packet.error(reqId, "DB_ERROR");
        } catch (RuntimeException re) {
            System.err.println("[ERROR] " + req.getType() + " reqId=" + reqId
                    + " unexpected: " + re.getClass().getSimpleName() + ": " + re.getMessage());
            return Packet.error(reqId, "INTERNAL_ERROR");
        }
    }

    private void releaseSession() {
        UserDTO u = this.currentUser;
        if (u != null) {
            ClientConnectionManager.unregister(u.getId(), this);
            this.currentUser = null;
        }
    }

    // ---- helpers ----

    private static Map<String, Object> tsPayload() {
        Map<String, Object> p = new HashMap<>();
        p.put("ts", System.currentTimeMillis());
        return p;
    }

    /** Allowlist — never includes passwordHash, otp, otp_hash. */
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

    private static void safeWrite(PrintWriter out, String json) {
        try { out.println(json); } catch (Exception ignored) {}
    }
    private static void safeWritePacket(PrintWriter out, Packet p) {
        try {
            out.println(ProtocolSerializer.toJson(p));
        } catch (Exception ignored) {
        }
    }
}