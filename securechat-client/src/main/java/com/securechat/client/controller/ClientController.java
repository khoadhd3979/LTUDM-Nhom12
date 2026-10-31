package com.securechat.client.controller;

import com.securechat.client.gui.*;
import com.securechat.client.network.NetworkManager;
import com.securechat.client.security.SecureTransport;
import com.securechat.client.validation.InputValidator;
import com.securechat.common.protocol.MessageType;
import com.securechat.common.protocol.Packet;
import com.securechat.common.protocol.ProtocolSerializer;
import com.securechat.security.*;

import javax.crypto.spec.SecretKeySpec;
import javax.swing.*;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import java.util.function.Consumer;
import javax.swing.Timer;

import static java.util.Map.entry;

/**
 * Điều phối Client theo Protocol v0.2 (Phase 2: REGISTER, VERIFY_OTP, LOGIN, LOGOUT).
 * Mọi hàm đều chạy trên EDT (NetworkManager đã chuyển callback về EDT).
 */
public class ClientController implements NetworkManager.MessageListener {

    private static final int HANDSHAKE_TIMEOUT_MS = 10_000;
    private static final int REQUEST_TIMEOUT_MS = 15_000;

    private enum ConnectionState { DISCONNECTED, HANDSHAKING, SECURE_READY }

    private static final Map<String, String> ERRORS = Map.ofEntries(
            entry("BAD_JSON", "Dữ liệu gửi lên sai định dạng JSON."),
            entry("FRAME_TOO_LARGE", "Gói dữ liệu vượt quá 1MB, Server đã đóng kết nối."),
            entry("EMAIL_INVALID", "Email không hợp lệ."),
            entry("PASSWORD_TOO_SHORT", "Mật khẩu quá ngắn."),
            entry("PASSWORD_TOO_LONG", "Mật khẩu quá dài."),
            entry("FULLNAME_REQUIRED", "Vui lòng nhập họ và tên."),
            entry("FULLNAME_INVALID", "Họ và tên không hợp lệ."),
            entry("INVALID_GENDER", "Giới tính không hợp lệ."),
            entry("INVALID_DATE_OF_BIRTH", "Ngày sinh không hợp lệ (định dạng yyyy-MM-dd)."),
            entry("EMAIL_EXISTS", "Email này đã được đăng ký."),
            entry("EMAIL_SEND_FAILED", "Server không gửi được email OTP. Vui lòng thử lại sau."),
            entry("ALREADY_LOGGED_IN", "Tài khoản đang được đăng nhập ở nơi khác."),
            entry("USER_NOT_FOUND", "Không tìm thấy tài khoản."),
            entry("ACCOUNT_NOT_PENDING", "Tài khoản không ở trạng thái chờ kích hoạt."),
            entry("OTP_NOT_FOUND", "Không tìm thấy mã OTP. Vui lòng đăng ký lại."),
            entry("OTP_INVALID", "Mã OTP không đúng."),
            entry("OTP_INVALID_FORMAT", "Mã OTP phải gồm 6 chữ số."),
            entry("OTP_EXPIRED", "Mã OTP đã hết hạn (10 phút)."),
            entry("OTP_ATTEMPTS_EXCEEDED", "Nhập sai OTP quá số lần cho phép."),
            entry("OTP_ALREADY_USED", "Mã OTP đã được sử dụng."),
            entry("AUTH_FAILED", "Email hoặc mật khẩu không đúng."),
            entry("ACCOUNT_PENDING", "Tài khoản chưa được kích hoạt bằng OTP."),
            entry("ACCOUNT_LOCKED", "Tài khoản đã bị khóa."));

    private static final class Pending {
        final Consumer<Packet> callback;
        final Timer timer;
        Pending(Consumer<Packet> cb, Timer t) { callback = cb; timer = t; }
    }

    private final NetworkManager network = new NetworkManager();
    private final MainFrame mainFrame;
    private final ChatPanel chatPanel;
    private final OTPPanel otpPanel;

    private PublicKey serverIdentityPub;
    private String keyLoadError;

    private SecureSession session;
    private SecureTransport transport;
    private ConnectionState state = ConnectionState.DISCONNECTED;

    private String host = "127.0.0.1";
    private int port = 8080;
    private String connHost;
    private int connPort;

    private boolean busy;
    private boolean loggedIn;
    private String pendingEmail = "";   // email đang chờ nhập OTP (không phải dữ liệu bí mật)

    private Runnable afterSecure;
    private Runnable discardAction;
    private Timer handshakeTimer;
    private final Map<String, Pending> pending = new HashMap<>();

    public ClientController() {
        network.setListener(this);
        loadServerIdentityKey();

        mainFrame = new MainFrame(this);
        otpPanel = new OTPPanel(this);
        chatPanel = new ChatPanel(this);
        mainFrame.addPanel(new LoginPanel(this), "LOGIN");
        mainFrame.addPanel(new RegisterPanel(this), "REGISTER");
        mainFrame.addPanel(otpPanel, "OTP");
        mainFrame.addPanel(chatPanel, "CHAT");
    }

    public void start() {
        mainFrame.showPanel("LOGIN");
        mainFrame.setVisible(true);
    }

    public String getHost() { return host; }
    public int getPort() { return port; }

    public void showRegisterScreen() { mainFrame.showPanel("REGISTER"); }
    public void showLoginScreen() { mainFrame.showPanel("LOGIN"); }
    public void showOTPScreen() { mainFrame.showPanel("OTP"); }

    // ------------------------------------------------------------------
    // Khóa định danh Server (Key Pinning)
    // ------------------------------------------------------------------
    private void loadServerIdentityKey() {
        try {
            String text;
            try (InputStream in = getClass().getResourceAsStream("/server_identity.pub")) {
                if (in != null) {
                    text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                } else {
                    Path path = Path.of("server_identity.pub");
                    if (!Files.exists(path)) path = Path.of("securechat-client/server_identity.pub");
                    text = Files.readString(path);
                }
            }
            String b64 = text.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s+", "");
            byte[] der = Base64.getDecoder().decode(b64);
            serverIdentityPub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
        } catch (Exception e) {
            keyLoadError = "Không nạp được server_identity.pub: " + e.getMessage();
        }
    }

    // ------------------------------------------------------------------
    // Thao tác người dùng (Phase 2)
    // ------------------------------------------------------------------
    public void login(String ip, int p, String email, char[] password) {
        if (busy || loggedIn) { Arrays.fill(password, '\0'); return; }
        this.host = ip; this.port = p;
        withSecure(() -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("email", email);
            payload.put("password", new String(password));
            Arrays.fill(password, '\0');                       // không giữ mật khẩu trong bộ nhớ
            request(MessageType.LOGIN, payload, r -> onLoginReply(email, r));
        }, () -> Arrays.fill(password, '\0'));
    }

    private void onLoginReply(String email, Packet r) {
        if (isOk(r)) {
            loggedIn = true;
            chatPanel.setUser(email);
            mainFrame.showPanel("CHAT");
        } else if ("ACCOUNT_PENDING".equals(r.getError())) {
            pendingEmail = email;
            otpPanel.setEmail(email);
            showInfo("Tài khoản chưa kích hoạt. Vui lòng nhập mã OTP.");
            showOTPScreen();
        } else {
            showServerError(r);
        }
    }

    public void register(String ip, int p, String email, char[] password,
                         String fullName, String gender, String dob) {
        if (busy) { Arrays.fill(password, '\0'); return; }
        this.host = ip; this.port = p;
        withSecure(() -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("email", email);
            payload.put("password", new String(password));
            Arrays.fill(password, '\0');
            payload.put("fullName", fullName);
            payload.put("gender", gender);          // MALE | FEMALE | OTHER
            payload.put("dateOfBirth", dob);        // yyyy-MM-dd
            request(MessageType.REGISTER, payload, r -> {
                if (isOk(r)) {
                    pendingEmail = email;
                    otpPanel.setEmail(email);
                    showInfo("Đăng ký thành công. Mã OTP có hiệu lực 10 phút.");
                    showOTPScreen();
                } else {
                    showServerError(r);
                }
            });
        }, () -> Arrays.fill(password, '\0'));
    }

    public void verifyOtp(String otp) {
        if (busy) return;
        if (pendingEmail.isEmpty()) { showError("Chưa có tài khoản nào đang chờ xác thực."); return; }
        withSecure(() -> request(MessageType.VERIFY_OTP,
                new HashMap<>(Map.of("email", pendingEmail, "otp", otp)), r -> {
                    if (isOk(r)) {
                        showInfo("Kích hoạt tài khoản thành công. Vui lòng đăng nhập.");
                        pendingEmail = "";
                        showLoginScreen();
                    } else {
                        showServerError(r);
                    }
                }), null);
    }

    public void logout() {
        if (state == ConnectionState.SECURE_READY && loggedIn) {
            sendQuietly(Packet.request(MessageType.LOGOUT, UUID.randomUUID().toString(), new HashMap<>()));
        }
        network.disconnect();
        resetConnection();
        showLoginScreen();
    }

    /** Gọi khi đóng cửa sổ. */
    public void shutdown() {
        if (state == ConnectionState.SECURE_READY && loggedIn) {
            sendQuietly(Packet.request(MessageType.LOGOUT, UUID.randomUUID().toString(), new HashMap<>()));
        }
        network.disconnectAndWait();
        System.exit(0);
    }

    // Các tính năng của Phase 4: Server chưa định nghĩa gói tin nên không gửi gói tự chế.
    public void sendChatMessage(String m) { comingSoon("Chat 1-1 / nhóm"); }
    public void loadMessageHistory()     { comingSoon("Tải lịch sử tin nhắn"); }
    public void createGroup(String n)    { comingSoon("Tạo nhóm"); }
    public void blockUser(String u)      { comingSoon("Chặn người dùng"); }
    public void sendFile(File f)         { comingSoon("Gửi file đính kèm"); }

    private void comingSoon(String feature) {
        chatPanel.appendMessage("[Hệ thống]: '" + feature + "' sẽ có ở Phase 4 (chờ Server định nghĩa giao thức).");
    }

    // ------------------------------------------------------------------
    // Kết nối + bắt tay
    // ------------------------------------------------------------------
    private void withSecure(Runnable action, Runnable discard) {
        if (state != ConnectionState.DISCONNECTED && (!host.equals(connHost) || port != connPort)) {
            network.disconnect();
            resetConnection();
        }
        setBusy(true);
        if (state == ConnectionState.SECURE_READY) { action.run(); return; }
        if (serverIdentityPub == null) {
            if (discard != null) discard.run();
            setBusy(false);
            showError(keyLoadError != null ? keyLoadError : "Chưa nạp được Public Key của Server.");
            return;
        }
        afterSecure = action;
        discardAction = discard;
        startHandshake();
    }

    private void startHandshake() {
        state = ConnectionState.HANDSHAKING;
        connHost = host; connPort = port;
        session = new SecureSession();
        transport = new SecureTransport(session);

        handshakeTimer = new Timer(HANDSHAKE_TIMEOUT_MS, e -> fail("Server không phản hồi khi thiết lập kênh bảo mật."));
        handshakeTimer.setRepeats(false);
        handshakeTimer.start();

        network.connect(host, port, this::sendClientHello,
                e -> fail("Không thể kết nối đến Server tại " + host + ":" + port));
    }

    private void sendClientHello() {
        try {
            byte[] clientNonce = AESUtil.randomBytes(32);
            session.setClientNonce(clientNonce);
            session.setState(SecureSession.State.HELLO_SENT);
            Map<String, Object> payload = new HashMap<>();
            payload.put("version", "v1");
            payload.put("clientNonce", Base64.getEncoder().encodeToString(clientNonce));
            Packet hello = Packet.request(MessageType.CLIENT_HELLO, UUID.randomUUID().toString(), payload);
            network.sendMessage(ProtocolSerializer.toJson(hello));
        } catch (Exception e) {
            fail("Lỗi khi gửi CLIENT_HELLO: " + e.getMessage());
        }
    }

    private void handleHandshakeResponse(Packet p) throws Exception {
        if (MessageType.ERROR.equals(p.getType())) {
            fail("Server từ chối bắt tay: " + errorText(p.getError()));
            return;
        }
        if (!MessageType.SERVER_HELLO.equals(p.getType())) return;

        Map<String, Object> payload = p.getPayload();
        String keyId = (String) payload.get("keyId");
        byte[] serverNonce = Base64.getDecoder().decode((String) payload.get("serverNonce"));
        byte[] ephPubDer = Base64.getDecoder().decode((String) payload.get("ephemeralPublicKey"));
        byte[] signature = Base64.getDecoder().decode((String) payload.get("signature"));

        byte[] tbs = SecurityUtils.concatLenPrefixed(
                "SecureChat-Handshake-v1".getBytes(StandardCharsets.UTF_8),
                keyId.getBytes(StandardCharsets.UTF_8),
                session.getClientNonce(), serverNonce, ephPubDer);

        if (!RSAUtil.pssVerify(serverIdentityPub, tbs, signature)) {
            fail("Xác thực Server thất bại! Chữ ký không khớp với server_identity.pub.");
            return;
        }

        PublicKey ephPub = KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(ephPubDer));
        byte[] secret = AESUtil.randomBytes(32);
        byte[] wrapped = RSAUtil.oaepEncrypt(ephPub, secret);

        Packet kx = Packet.request(MessageType.KEY_EXCHANGE, UUID.randomUUID().toString(),
                Map.of("encryptedSecret", Base64.getEncoder().encodeToString(wrapped)));
        network.sendMessage(ProtocolSerializer.toJson(kx));

        byte[] salt = SecurityUtils.concat(session.getClientNonce(), serverNonce);
        byte[] okm = HKDFUtil.derive(secret, salt,
                "SecureChat-Handshake-v1-HKDF".getBytes(StandardCharsets.UTF_8), 64);
        AESUtil.wipe(secret);
        session.setC2sKey(new SecretKeySpec(Arrays.copyOfRange(okm, 0, 32), "AES"));
        session.setS2cKey(new SecretKeySpec(Arrays.copyOfRange(okm, 32, 64), "AES"));
        AESUtil.wipe(okm);
        session.setState(SecureSession.State.KEY_RECEIVED);
    }

    // ------------------------------------------------------------------
    // Gửi / nhận
    // ------------------------------------------------------------------
    private void request(String type, Map<String, Object> payload, Consumer<Packet> onReply) {
        final String id = UUID.randomUUID().toString();           // requestId duy nhất
        Packet p = Packet.request(type, id, payload);

        Timer t = new Timer(REQUEST_TIMEOUT_MS, e -> {
            Pending x = pending.remove(id);
            if (x != null) { setBusy(false); x.callback.accept(Packet.error(id, "TIMEOUT")); }
        });
        t.setRepeats(false);
        pending.put(id, new Pending(onReply, t));

        if (!sendEncrypted(p)) { pending.remove(id); setBusy(false); return; }
        t.start();
    }

    private boolean sendEncrypted(Packet inner) {
        if (state != ConnectionState.SECURE_READY) { showError("Chưa có kênh bảo mật tới Server."); return false; }
        try {
            // Kiểm tra TRƯỚC khi mã hóa để không làm lệch số thứ tự (sequence) với Server.
            int plainBytes = ProtocolSerializer.toJson(inner).getBytes(StandardCharsets.UTF_8).length;
            long estimated = ((plainBytes + 16L + 2) / 3) * 4 + 300;   // Base64 + envelope
            if (estimated > InputValidator.MAX_FRAME_BYTES) {
                showError("Gói tin vượt quá giới hạn 1MB của giao thức.");
                return false;
            }
            network.sendMessage(transport.encodeOutbound(inner));
            return true;
        } catch (Exception e) {
            fail("Lỗi gửi dữ liệu: " + e.getMessage());
            return false;
        }
    }

    private void sendQuietly(Packet inner) {
        try { network.sendMessage(transport.encodeOutbound(inner)); } catch (Exception ignored) {}
    }

    @Override
    public void onMessageReceived(String messageJson) {
        try {
            Packet packet = ProtocolSerializer.fromJson(messageJson);
            if (MessageType.SECURE_MESSAGE.equals(packet.getType())) {
                if (transport == null) return;
                handleSecureInnerPacket(transport.decodeInbound(packet));
            } else if (state == ConnectionState.HANDSHAKING) {
                handleHandshakeResponse(packet);
            } else if (MessageType.ERROR.equals(packet.getType())) {
                fail("Server báo lỗi: " + errorText(packet.getError()));
            }
        } catch (javax.crypto.AEADBadTagException e) {
            fail("Không giải mã được dữ liệu từ Server (khóa phiên không khớp). Vui lòng thử lại.");
        } catch (Exception e) {
            fail("Lỗi xử lý dữ liệu từ Server: " + e.getClass().getSimpleName());
        }
    }

    private void handleSecureInnerPacket(Packet inner) throws Exception {
        if (MessageType.HANDSHAKE_CONFIRM.equals(inner.getType())) {
            if (!"SERVER_CONFIRMED".equals(inner.getPayload().get("confirm"))) return;
            Packet confirm = Packet.ok(MessageType.HANDSHAKE_CONFIRM, UUID.randomUUID().toString(),
                    Map.of("confirm", "CLIENT_CONFIRMED"));
            network.sendMessage(transport.encodeOutbound(confirm));

            if (handshakeTimer != null) handshakeTimer.stop();
            state = ConnectionState.SECURE_READY;
            session.setState(SecureSession.State.ESTABLISHED);

            Runnable a = afterSecure;
            afterSecure = null; discardAction = null;
            if (a != null) a.run();            // gửi tiếp thao tác đang chờ (LOGIN/REGISTER/...)
            else setBusy(false);
            return;
        }

        // Ghép phản hồi theo requestId
        Pending x = pending.remove(inner.getRequestId());
        if (x != null) {
            x.timer.stop();
            setBusy(false);
            x.callback.accept(inner);
        } else if (MessageType.ERROR.equals(inner.getType())) {
            showServerError(inner);
        }
        // Gói đẩy từ Server (broadcast online/offline, tin nhắn đến): Phase 4.
    }

    @Override
    public void onConnectionLost() {
        if (state == ConnectionState.DISCONNECTED) return;
        fail("Mất kết nối với Server. Vui lòng đăng nhập lại để kết nối lại.");
    }

    // ------------------------------------------------------------------
    // Tiện ích
    // ------------------------------------------------------------------
    private void fail(String msg) {
        boolean wasLoggedIn = loggedIn;
        network.disconnect();
        resetConnection();
        if (wasLoggedIn) showLoginScreen();
        showError(msg);
    }

    private void resetConnection() {
        if (handshakeTimer != null) { handshakeTimer.stop(); handshakeTimer = null; }
        for (Pending p : pending.values()) p.timer.stop();
        pending.clear();
        if (discardAction != null) discardAction.run();
        afterSecure = null; discardAction = null;
        session = null; transport = null;
        state = ConnectionState.DISCONNECTED;
        loggedIn = false;
        setBusy(false);
    }

    private void setBusy(boolean b) { busy = b; mainFrame.setBusy(b); }

    private static boolean isOk(Packet p) { return "OK".equals(p.getStatus()); }

    private static String errorText(String code) {
        return code == null ? "Lỗi không xác định" : ERRORS.getOrDefault(code, code);
    }

    private void showServerError(Packet p) { showError(errorText(p.getError())); }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(mainFrame, msg, "Lỗi", JOptionPane.ERROR_MESSAGE);
    }

    private void showInfo(String msg) {
        JOptionPane.showMessageDialog(mainFrame, msg, "Thông báo", JOptionPane.INFORMATION_MESSAGE);
    }
}