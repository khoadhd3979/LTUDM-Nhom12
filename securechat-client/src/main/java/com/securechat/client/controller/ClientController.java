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
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import javax.swing.Timer;

import static java.util.Map.entry;


public class ClientController implements NetworkManager.MessageListener {

    private static final int HANDSHAKE_TIMEOUT_MS = 10_000;
    private static final int REQUEST_TIMEOUT_MS = 15_000;
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm dd/MM");

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
            entry("ACCOUNT_LOCKED", "Tài khoản đã bị khóa."),
            entry("NOT_LOGGED_IN", "Bạn chưa đăng nhập."),
            entry("TIMEOUT", "Server không phản hồi. Vui lòng thử lại."),
            entry("SECURE_REQUIRED", "Kênh bảo mật đã thiết lập — vui lòng gửi lại trong SECURE_MESSAGE."),
            entry("HANDSHAKE_REQUIRED", "Cần hoàn tất bắt tay bảo mật trước khi gửi yêu cầu."),
            entry("SECURITY_ERROR", "Lỗi bảo mật phiên kết nối. Vui lòng kết nối lại."),
            entry("INVALID_STATE", "Trạng thái bắt tay không hợp lệ. Vui lòng kết nối lại."),
            entry("HELLO_INVALID", "Yêu cầu bắt tay sai định dạng."),
            entry("KEY_EXCHANGE_INVALID", "Trao đổi khóa thất bại. Vui lòng kết nối lại."),
            entry("KEY_CONFIRM_INVALID", "Xác nhận khóa phiên thất bại. Vui lòng kết nối lại."));

    private static final class Pending {
        final Consumer<Packet> callback;
        final Timer timer;
        Pending(Consumer<Packet> cb, Timer t) { callback = cb; timer = t; }
    }

    /** Một trang lịch sử (server trả DESC theo message_id). */
    public record HistoryPage(List<Map<String, Object>> messages, boolean hasMore, Long nextCursor) {}

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
    private final Map<String, Pending> pending = new ConcurrentHashMap<>();

    /** Khóa gửi: encodeOutbound dùng số thứ tự nên chỉ được gọi tuần tự. */
    private final Object sendLock = new Object();

    /** groupName -> groupId (nạp từ FETCH_MY_GROUPS). */
    private final Map<String, Long> groupIdByName = new HashMap<>();

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

    /** Trả về groupId của nhóm theo tên, hoặc null nếu chưa có. */
    public Long getGroupId(String groupName) { return groupIdByName.get(groupName); }

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
            loadMyGroups();
        } else if ("ACCOUNT_PENDING".equals(r.getError())) {
            pendingEmail = email;
            otpPanel.setEmail(email);
            showInfo("Tài khoản chưa kích hoạt. Vui lòng nhập mã OTP.");
            showOTPScreen();
        } else {
            showServerError(r);
        }
    }

    /** FETCH_MY_GROUPS -> nạp vào danh sách nhóm trên giao diện. */
    private void loadMyGroups() {
        request(MessageType.FETCH_MY_GROUPS, new HashMap<>(), res -> {
            if (!isOk(res)) { showServerError(res); return; }
            Map<String, Object> pl = res.getPayload();
            if (pl == null || !(pl.get("groups") instanceof List<?> list)) return;
            groupIdByName.clear();
            for (Object o : list) {
                if (o instanceof Map<?, ?> g) {
                    String name = String.valueOf(g.get("groupName"));
                    if (g.get("groupId") != null) groupIdByName.put(name, asLong(g.get("groupId")));
                    chatPanel.addGroupToList(name);
                }
            }
        });
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

    // ==============================================================================
    // CÁC CHỨC NĂNG CLIENT - PHASE 4
    // Lưu ý: các type không có trong MessageType (UPDATE_PROFILE, FILE_CHUNK, JOIN_GROUP,
    // LEAVE_GROUP, REMOVE_GROUP_MEMBER, BLOCK_USER, ADD_FRIEND, SEND_STICKER, MESSAGE_STATUS)
    // sẽ bị Server trả UNKNOWN_TYPE cho đến khi Server hỗ trợ.
    // ==============================================================================

    public void updateProfile(String fullName, String gender, String dob) {
        Map<String, Object> pl = new HashMap<>();
        pl.put("fullName", fullName);
        pl.put("gender", gender);
        pl.put("dateOfBirth", dob);
        sendQuietly(Packet.request("UPDATE_PROFILE", UUID.randomUUID().toString(), pl));
    }

    /** Gửi tin nhắn: chỉ hiển thị lên khung chat khi Server trả OK (Lỗi 1). */
    public void sendChatMessage(String target, boolean isGroup, String content) {
        String msgId = UUID.randomUUID().toString();
        Map<String, Object> pl = new HashMap<>();
        pl.put("messageId", msgId);
        pl.put(isGroup ? "groupName" : "receiver", target);
        pl.put("msgType", "TEXT");
        pl.put("content", content);

        String type = isGroup ? MessageType.SEND_GROUP_MESSAGE : MessageType.SEND_DIRECT_MESSAGE;
        request(type, pl, res -> {
            if (isOk(res)) {
                chatPanel.appendMessage((isGroup ? "[Nhóm " : "[Tới ") + target + "]: " + content);
            } else {
                chatPanel.appendMessage("[Lỗi gửi tin]: " + errorText(res.getError()));
            }
        });
    }

    /** Gửi file ở luồng nền (SwingWorker), không làm đơ EDT. */
    public void sendFile(String target, boolean isGroup, File file) {
        SwingWorker<Void, Void> worker = new SwingWorker<>() {
            @Override
            protected Void doInBackground() throws Exception {
                java.util.List<byte[]> chunks = com.securechat.client.network.FileChunkUtils.splitFileIntoChunks(
                        file, com.securechat.client.network.FileChunkUtils.DEFAULT_CHUNK_SIZE);

                String fileId = UUID.randomUUID().toString();
                int total = chunks.size();

                SwingUtilities.invokeLater(() ->
                        chatPanel.appendMessage("[File]: Đang gửi file '" + file.getName() + "' (" + total + " chunk)..."));

                for (int i = 0; i < total; i++) {
                    Map<String, Object> pl = new HashMap<>();
                    pl.put("fileId", fileId);
                    pl.put("fileName", file.getName());
                    pl.put("chunkIndex", i);
                    pl.put("totalChunks", total);
                    pl.put("data", Base64.getEncoder().encodeToString(chunks.get(i)));
                    pl.put(isGroup ? "groupName" : "receiver", target);

                    // sendRaw có khóa + chỉ ném exception (không đụng Swing) -> an toàn ở luồng nền.
                    // Lỗi sẽ dừng vòng lặp và đi qua get() trong done().
                    sendRaw(Packet.request("FILE_CHUNK", UUID.randomUUID().toString(), pl));
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                    chatPanel.appendMessage("[File]: Đã gửi xong file '" + file.getName() + "'.");
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    chatPanel.appendMessage("[Lỗi gửi file]: Bị gián đoạn.");
                } catch (java.util.concurrent.ExecutionException e) {
                    Throwable c = e.getCause() != null ? e.getCause() : e;
                    System.err.println("[Lỗi gửi file]: " + c);
                    chatPanel.appendMessage("[Lỗi gửi file]: " + c.getMessage());
                }
            }
        };
        worker.execute();
    }

    private byte[] lastCompleteFileBytes;
    private String lastCompleteFileName;

    // Hàm lưu file khi bấm nút trên giao diện
    public void saveLastReceivedFile(java.awt.Component parent) {
        if (lastCompleteFileBytes == null || lastCompleteFileName == null) {
            showInfo("Chưa có file đính kèm nào mới nhận được để lưu.");
            return;
        }
        javax.swing.JFileChooser fc = new javax.swing.JFileChooser();
        fc.setSelectedFile(new java.io.File(lastCompleteFileName));
        if (fc.showSaveDialog(parent) == javax.swing.JFileChooser.APPROVE_OPTION) {
            try {
                java.nio.file.Files.write(fc.getSelectedFile().toPath(), lastCompleteFileBytes);
                showInfo("Đã lưu file thành công tại: " + fc.getSelectedFile().getAbsolutePath());
            } catch (Exception e) {
                showError("Không thể lưu file: " + e.getMessage());
            }
        }
    }

    public void sendSticker(String target, boolean isGroup, String stickerCode) {
        Map<String, Object> pl = new HashMap<>();
        pl.put("messageId", UUID.randomUUID().toString());
        pl.put(isGroup ? "groupName" : "receiver", target);
        pl.put("msgType", "STICKER");
        pl.put("content", stickerCode);
        chatPanel.appendMessage("[Bạn gửi Sticker]: " + stickerCode);
        sendQuietly(Packet.request("SEND_STICKER", UUID.randomUUID().toString(), pl));
    }

    // ----- Nhóm: chỉ cập nhật UI khi Server trả OK -----

    public void createGroup(String name) {
        request(MessageType.CREATE_GROUP, new HashMap<>(Map.of("groupName", name)), res -> {
            if (isOk(res)) loadMyGroups();          // lấy lại danh sách để có groupId thật
            else showServerError(res);
        });
    }

    public void joinGroup(String name) {
        request("JOIN_GROUP", new HashMap<>(Map.of("groupName", name)), res -> {
            if (isOk(res)) loadMyGroups();
            else showServerError(res);
        });
    }

    public void leaveGroup(String name) {
        request("LEAVE_GROUP", new HashMap<>(Map.of("groupName", name)), res -> {
            if (isOk(res)) {
                groupIdByName.remove(name);
                chatPanel.removeGroupFromList(name);
            } else {
                showServerError(res);
            }
        });
    }

    public void addGroupMember(String grp, String email) {
        request(MessageType.ADD_GROUP_MEMBER,
                new HashMap<>(Map.of("groupName", grp, "memberEmail", email)), res -> {
                    if (isOk(res)) chatPanel.appendMessage("[Nhóm " + grp + "]: Đã thêm " + email + " vào nhóm.");
                    else showServerError(res);
                });
    }

    public void removeGroupMember(String grp, String email) {
        request("REMOVE_GROUP_MEMBER",
                new HashMap<>(Map.of("groupName", grp, "memberEmail", email)), res -> {
                    if (isOk(res)) chatPanel.appendMessage("[Nhóm " + grp + "]: Đã xóa " + email + " khỏi nhóm.");
                    else showServerError(res);
                });
    }

    public void blockTarget(String target, boolean isGroup) {
        sendQuietly(Packet.request("BLOCK_USER", UUID.randomUUID().toString(),
                Map.of("target", target, "targetType", isGroup ? "GROUP" : "USER")));
    }

    public void addFriend(String friendEmail) {
        Map<String, Object> pl = new HashMap<>();
        pl.put("friendEmail", friendEmail);
        chatPanel.appendMessage("[Bạn bè]: Đã gửi yêu cầu thêm '" + friendEmail + "' vào danh bạ.");
        sendQuietly(Packet.request("ADD_FRIEND", UUID.randomUUID().toString(), pl));
    }

    public void markConversationSeen(String target, boolean isGroup) {
        sendQuietly(Packet.request("MESSAGE_STATUS", UUID.randomUUID().toString(),
                Map.of("target", target, "isGroup", isGroup, "status", "SEEN")));
    }

    // ------------------------------------------------------------------
    // Lịch sử tin nhắn - Cursor Pagination (Lỗi 2)
    // ------------------------------------------------------------------

    /**
     * Tải lịch sử chat riêng. before = null cho trang đầu; các trang sau truyền nextCursor.
     * onPage được gọi trên EDT. Danh sách messages vẫn ở thứ tự DESC như Server trả.
     */
    public void loadMessageHistory(String target, boolean isGroupChat) {
        if (isGroupChat) {
            Long groupId = getGroupId(target);
            if (groupId != null) {
                fetchGroupHistory(groupId, null, this::showHistoryPage);
            } else {
                chatPanel.appendMessage("[Lỗi]: Không thể tải lịch sử, chưa có thông tin nhóm " + target);
            }
        } else {
            Map<String, Object> pl = new HashMap<>();
            pl.put("target", target);
            fetchHistory(MessageType.FETCH_DIRECT_HISTORY, pl, null, this::showHistoryPage);
        }
    }
    public void fetchDirectHistory(long peerUserId, Long before, Consumer<HistoryPage> onPage) {
        Map<String, Object> pl = new HashMap<>();
        pl.put("userId", peerUserId);
        fetchHistory(MessageType.FETCH_DIRECT_HISTORY, pl, before, onPage);
    }

    /** Tải lịch sử nhóm. Xem fetchDirectHistory. */
    public void fetchGroupHistory(long groupId, Long before, Consumer<HistoryPage> onPage) {
        Map<String, Object> pl = new HashMap<>();
        pl.put("groupId", groupId);
        fetchHistory(MessageType.FETCH_GROUP_HISTORY, pl, before, onPage);
    }

    /** Giữ chữ ký cũ cho code giao diện đang gọi; tạm hiển thị thẳng lên chatPanel. */
    public void fetchDirectHistory(String targetUserId, Long beforeMessageId) {
        fetchDirectHistory(asLong(targetUserId), beforeMessageId, this::showHistoryPage);
    }

    public void fetchGroupHistory(String groupId, Long beforeMessageId) {
        fetchGroupHistory(asLong(groupId), beforeMessageId, this::showHistoryPage);
    }

    @SuppressWarnings("unchecked")
    private void fetchHistory(String type, Map<String, Object> pl, Long before, Consumer<HistoryPage> onPage) {
        if (before != null) pl.put("beforeMessageId", before);
        request(type, pl, res -> {
            if (!isOk(res)) { showServerError(res); return; }
            Map<String, Object> p = res.getPayload() == null ? Map.of() : res.getPayload();
            List<Map<String, Object>> msgs = p.get("messages") instanceof List<?> l
                    ? (List<Map<String, Object>>) l : List.of();
            boolean hasMore = Boolean.TRUE.equals(p.get("hasMore"));
            Long next = p.get("nextCursor") == null ? null : asLong(p.get("nextCursor"));
            onPage.accept(new HistoryPage(msgs, hasMore, next));
        });
    }

    /** Hiển thị mặc định: đảo DESC -> ASC rồi in từng tin. ChatPanel nên tự prepend + giữ JScrollBar. */
    private void showHistoryPage(HistoryPage page) {
        List<Map<String, Object>> asc = new ArrayList<>(page.messages());
        Collections.reverse(asc);
        for (Map<String, Object> m : asc) {
            String sender = str(m, "senderName", "senderEmail", "sender", "senderId");
            String time = fmtTime(m.get("sentAt"));
            chatPanel.appendMessage("[" + time + "] " + sender + ": " + str(m, "content"));
        }
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

    /**
     * Gửi thô: có khóa để số thứ tự (sequence) mã hóa luôn khớp thứ tự gửi lên socket.
     * Chỉ ném exception, không đụng tới Swing -> gọi được từ mọi luồng.
     */
    private void sendRaw(Packet inner) throws Exception {
        if (transport == null || state != ConnectionState.SECURE_READY) {
            throw new IOException("Chưa có kênh bảo mật tới Server.");
        }
        synchronized (sendLock) {
            network.sendMessage(transport.encodeOutbound(inner));
        }
    }

    /** Dùng cho request-response trên EDT: lỗi sẽ hiện hộp thoại / ngắt kết nối. */
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
            sendRaw(inner);
            return true;
        } catch (Exception e) {
            fail("Lỗi gửi dữ liệu: " + e.getMessage());
            return false;
        }
    }

    /** Gửi không chờ phản hồi. Mọi lỗi đều được log ra console và báo lên chatPanel. */
    private void sendQuietly(Packet inner) {
        try {
            sendRaw(inner);
        } catch (IOException | GeneralSecurityException | IllegalStateException e) {
            reportSendError(e);
        } catch (RuntimeException e) {
            reportSendError(e);
        } catch (Exception e) {
            reportSendError(e);
        }
    }

    private void reportSendError(Exception e) {
        System.err.println("[Lỗi gửi mạng]: " + e);
        e.printStackTrace();
        String msg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
        SwingUtilities.invokeLater(() -> chatPanel.appendMessage("[Lỗi gửi mạng]: " + msg));
    }

    @Override
    public void onMessageReceived(String messageJson) {
        // Luôn xử lý trên EDT: thứ tự gói được giữ nguyên và thao tác Swing/pending an toàn.
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> onMessageReceived(messageJson));
            return;
        }
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
            synchronized (sendLock) {
                network.sendMessage(transport.encodeOutbound(confirm));
            }

            if (handshakeTimer != null) handshakeTimer.stop();
            state = ConnectionState.SECURE_READY;
            session.setState(SecureSession.State.ESTABLISHED);

            Runnable a = afterSecure;
            afterSecure = null; discardAction = null;
            if (a != null) a.run();            // gửi tiếp thao tác đang chờ (LOGIN/REGISTER/...)
            else setBusy(false);
            return;
        }

        String type = inner.getType() != null ? inner.getType() : "";

        // 1) GÓI PUSH do Server chủ động gửi: xử lý TRƯỚC và KHÔNG ghép với request đang chờ.
        switch (type) {
            case MessageType.PUSH_DIRECT_MESSAGE -> { handlePushDirect(inner); return; }
            case MessageType.PUSH_GROUP_MESSAGE -> { handlePushGroup(inner); return; }
            case "MESSAGE_STATUS" -> {
                Map<String, Object> pl = inner.getPayload();
                if (pl != null) chatPanel.appendMessage("[Trạng thái tin nhắn]: " + pl.get("status"));
                return;
            }
            case "FRIEND_STATUS" -> {
                Map<String, Object> pl = inner.getPayload();
                if (pl != null) chatPanel.addOrUpdateFriend((String) pl.get("email"), (String) pl.get("status"));
                return;
            }
            case "SYSTEM_BROADCAST" -> {
                Map<String, Object> pl = inner.getPayload();
                if (pl != null) chatPanel.appendMessage("📢 [HỆ THỐNG]: " + pl.get("message"));
                return;
            }
            default -> { /* không phải PUSH: là phản hồi request-response */ }
        }

        // 2) Phản hồi OK/ERROR: ghép theo requestId
        Pending x = inner.getRequestId() == null ? null : pending.remove(inner.getRequestId());
        if (x != null) {
            x.timer.stop();
            setBusy(false);
            x.callback.accept(inner);
        } else if (MessageType.ERROR.equals(type) || "ERROR".equals(inner.getStatus())) {
            if (loggedIn) chatPanel.appendMessage("[Server báo lỗi]: " + errorText(inner.getError()));
            else showServerError(inner);
        }
    }

    private void handlePushDirect(Packet inner) {
        Map<String, Object> pl = inner.getPayload();
        if (pl == null) return;
        String sender = str(pl, "senderName", "senderEmail", "sender", "senderId");
        String time = fmtTime(pl.get("sentAt"));
        chatPanel.appendMessage("[" + time + "] [Từ " + sender + "]: " + str(pl, "content"));
    }

    private void handlePushGroup(Packet inner) {
        Map<String, Object> pl = inner.getPayload();
        if (pl == null) return;
        String sender = str(pl, "senderName", "senderEmail", "sender", "senderId");
        String group = str(pl, "groupName", "groupId");
        String time = fmtTime(pl.get("sentAt"));
        chatPanel.appendMessage("[" + time + "] [Nhóm " + group + "] " + sender + ": " + str(pl, "content"));
    }

    @Override
    public void onConnectionLost() {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(this::onConnectionLost);
            return;
        }
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
        groupIdByName.clear();
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

    /** Lấy giá trị chuỗi đầu tiên khác null trong các key; rỗng nếu không có. */
    private static String str(Map<String, Object> m, String... keys) {
        for (String k : keys) {
            Object v = m.get(k);
            if (v != null) return String.valueOf(v);
        }
        return "";
    }

    /** sentAt dạng ISO-8601 không có múi giờ (yyyy-MM-ddTHH:mm:ss) -> LocalDateTime -> chuỗi hiển thị. */
    private static String fmtTime(Object v) {
        if (v == null) return "";
        try {
            return LocalDateTime.parse(String.valueOf(v)).format(TIME_FMT);
        } catch (DateTimeParseException e) {
            return String.valueOf(v);
        }
    }

    public static long asLong(Object v) {
        if (v == null) return 0L;
        return v instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(v));
    }
}