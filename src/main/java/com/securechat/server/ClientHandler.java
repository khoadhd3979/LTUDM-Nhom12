package com.securechat.server;

import com.securechat.bll.AuthService;
import com.securechat.common.protocol.MessageType;
import com.securechat.common.protocol.Packet;
import com.securechat.common.protocol.ProtocolSerializer;
import com.securechat.dto.UserDTO;
import com.securechat.exception.BusinessException;
import com.securechat.security.*;

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
    private final SecureSession session   = new SecureSession();
    private final SecureTransport transport = new SecureTransport(session);

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

            socket.setSoTimeout(30000);
            while (true) {
                String line;
                try {
                    line = reader.readLine();
                } catch (BoundedLineReader.LineTooLongException e) {
                    writePlain(out, Packet.error(null, "FRAME_TOO_LARGE"));
                    break;
                }
                if (line == null) break;
                if (line.isBlank()) continue;

                boolean keepOpen = handleLine(line, out);
                if (!keepOpen) break;
            }
        } catch (IOException ignored) {
        } finally {
            releaseSession();
            System.out.println("[DISC] " + remote);
        }

    }

    private boolean handleLine(String line, PrintWriter out) {
        Packet req;
        try {
            req = ProtocolSerializer.fromJson(line);
        } catch (Exception e) {
            writePlain(out, Packet.error(null, "BAD_JSON"));
            return true;
        }
        if (req == null || req.getType() == null) {
            writePlain(out, Packet.error(req == null ? null : req.getRequestId(), "MISSING_TYPE"));
            return true;
        }

        String t = req.getType();

        try {
            if (MessageType.SECURE_MESSAGE.equals(t)) {
                return handleSecure(req, out);
            }
            switch (t) {
                case MessageType.PING:
                    if (session.getState() == SecureSession.State.ESTABLISHED) {
                        writePlain(out, Packet.error(req.getRequestId(), "SECURE_REQUIRED"));
                        return true;
                    }
                    writePlain(out, Packet.ok(MessageType.PONG, req.getRequestId(), tsPayload()));
                    return true;

                case MessageType.CLIENT_HELLO:
                    return handleClientHello(req, out);

                case MessageType.KEY_EXCHANGE:
                    return handleKeyExchange(req, out);

                default:
                    if (session.getState() == SecureSession.State.ESTABLISHED) {
                        writePlain(out, Packet.error(req.getRequestId(), "SECURE_REQUIRED"));
                        return true;
                    }
                    if (session.getState() == SecureSession.State.INIT) {
                        writePlain(out, Packet.error(req.getRequestId(), "HANDSHAKE_REQUIRED"));
                        return true;
                    }
                    writePlain(out, Packet.error(req.getRequestId(), "UNKNOWN_TYPE"));
                    return true;
            }
        } catch (SecurityException se) {
            System.err.println("[SEC] " + t + " reqId=" + req.getRequestId() + ": " + se.getMessage());
            writePlain(out, Packet.error(req.getRequestId(), "SECURITY_ERROR"));
            return false;
        } catch (Exception e) {
            System.err.println("[ERROR] " + t + " reqId=" + req.getRequestId() + ": "
                    + e.getClass().getSimpleName() + ": " + e.getMessage());
            writePlain(out, Packet.error(req.getRequestId(), "INTERNAL_ERROR"));
            return false;
        }
    }

    // ============================================================
    // Handshake
    // ============================================================

    private boolean handleClientHello(Packet req, PrintWriter out) throws Exception {
        if (session.getState() != SecureSession.State.INIT) {
            writePlain(out, Packet.error(req.getRequestId(), "INVALID_STATE"));
            return true;
        }
        Map<String, Object> p = req.getPayload();
        String version = getStr(p, "version");
        String clientNonceB64 = getStr(p, "clientNonce");
        if (!PROTO_VERSION.equals(version) || clientNonceB64 == null) {
            writePlain(out, Packet.error(req.getRequestId(), "HELLO_INVALID"));
            return false;
        }
        byte[] clientNonce = Base64.getDecoder().decode(clientNonceB64);
        if (clientNonce.length != 32) {
            writePlain(out, Packet.error(req.getRequestId(), "HELLO_INVALID"));
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
                clientNonce,
                serverNonce,
                eph.getPublic().getEncoded());
        byte[] sig = RSAUtil.pssSign(ServerIdentityKeyManager.getInstance().privateKey(), tbs);

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("version", PROTO_VERSION);
        resp.put("keyId", ServerIdentityKeyManager.getInstance().keyId());
        resp.put("serverNonce", Base64.getEncoder().encodeToString(serverNonce));
        resp.put("ephemeralPublicKey", Base64.getEncoder().encodeToString(eph.getPublic().getEncoded()));
        resp.put("signature", Base64.getEncoder().encodeToString(sig));

        writePlain(out, Packet.ok(MessageType.SERVER_HELLO, req.getRequestId(), resp));
        session.setState(SecureSession.State.HELLO_SENT);
        return true;
    }

    private boolean handleKeyExchange(Packet req, PrintWriter out) throws Exception {
        if (session.getState() != SecureSession.State.HELLO_SENT) {
            writePlain(out, Packet.error(req.getRequestId(), "INVALID_STATE"));
            return true;
        }
        Map<String, Object> p = req.getPayload();
        String encB64 = getStr(p, "encryptedSecret");
        if (encB64 == null) {
            writePlain(out, Packet.error(req.getRequestId(), "KEY_EXCHANGE_INVALID"));
            return false;
        }
        byte[] secret;
        try {
            secret = RSAUtil.oaepDecrypt(session.getEphemeralPrivate(),
                    Base64.getDecoder().decode(encB64));
        } catch (Exception e) {
            writePlain(out, Packet.error(req.getRequestId(), "KEY_EXCHANGE_INVALID"));
            return false;
        }
        if (secret.length != 32) {
            AESUtil.wipe(secret);
            writePlain(out, Packet.error(req.getRequestId(), "KEY_EXCHANGE_INVALID"));
            return false;
        }

        byte[] salt = SecurityUtils.concat(session.getClientNonce(), session.getServerNonce());
        byte[] okm = HKDFUtil.derive(secret, salt, HKDF_INFO.getBytes(StandardCharsets.UTF_8), 64);
        AESUtil.wipe(secret);

        SecretKey c2s = new SecretKeySpec(Arrays.copyOfRange(okm, 0, 32), "AES");
        SecretKey s2c = new SecretKeySpec(Arrays.copyOfRange(okm, 32, 64), "AES");
        session.setC2sKey(c2s);
        session.setS2cKey(s2c);
        session.setEphemeralPrivate(null);   // wipe reference
        session.setState(SecureSession.State.KEY_RECEIVED);

        Map<String, Object> confirmPayload = new LinkedHashMap<>();
        confirmPayload.put("confirm", "SERVER_CONFIRMED");
        Packet confirmInner = Packet.ok(MessageType.HANDSHAKE_CONFIRM, req.getRequestId(), confirmPayload);
        writeSecure(out, confirmInner);
        return true;
    }

    private boolean handleSecure(Packet req, PrintWriter out) throws Exception {
        if (session.getState() == SecureSession.State.KEY_RECEIVED) {
            // Expect client's HANDSHAKE_CONFIRM
            Packet inner = transport.decodeInbound(req);
            if (!isClientConfirm(inner)) {
                writePlain(out, Packet.error(req.getRequestId(), "KEY_CONFIRM_INVALID"));
                return false;
            }
            session.setState(SecureSession.State.ESTABLISHED);
            return true;
        }
        if (session.getState() != SecureSession.State.ESTABLISHED) {
            writePlain(out, Packet.error(req.getRequestId(), "HANDSHAKE_REQUIRED"));
            return false;
        }
        Packet inner = transport.decodeInbound(req);
        Packet response = dispatchInner(inner);
        if (response != null) writeSecure(out, response);
        return true;
    }

    private static boolean isClientConfirm(Packet inner) {
        if (inner == null || !MessageType.HANDSHAKE_CONFIRM.equals(inner.getType())) return false;
        Map<String, Object> p = inner.getPayload();
        return p != null && "CLIENT_CONFIRMED".equals(p.get("confirm"));
    }

    // ============================================================
    // Application dispatch (encrypted)
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
                default:
                    return Packet.error(reqId, "UNKNOWN_TYPE");
            }
        } catch (BusinessException be) {
            return Packet.error(reqId, be.getCode());
        } catch (SQLException sqle) {
            System.err.println("[ERROR] inner " + req.getType() + " SQLState=" + sqle.getSQLState());
            return Packet.error(reqId, "DB_ERROR");
        } catch (RuntimeException re) {
            System.err.println("[ERROR] inner " + req.getType() + " "
                    + re.getClass().getSimpleName() + ": " + re.getMessage());
            return Packet.error(reqId, "INTERNAL_ERROR");
        }
    }

    // ============================================================
    // Helpers
    // ============================================================

    private void writeSecure(PrintWriter out, Packet inner) {
        try {
            out.println(transport.encodeOutbound(inner));
        } catch (Exception e) {
            System.err.println("[ERROR] encode failed: " + e.getMessage());
        }
    }

    private static void writePlain(PrintWriter out, Packet p) {
        try { out.println(ProtocolSerializer.toJson(p)); } catch (Exception ignored) {}
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
}