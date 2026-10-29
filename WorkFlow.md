# Quy trình Làm việc nhóm & Phân công Dự án (SecureChat-Java)

Tài liệu này quy định luồng làm việc với Git/GitHub, cấu trúc thư mục chuẩn và chi tiết phân công công việc cho từng giai đoạn của dự án.

---

## 1. Cấu trúc Thư mục Chuẩn (Multi-Module Maven)

Dự án sử dụng mô hình Maven Multi-Module gồm 3 module độc lập nằm chung một Repository: `securechat-common` (giao thức và crypto dùng chung), `securechat-server` (Backend do Khoa phụ trách) và `securechat-client` (Frontend Swing do Sự phụ trách).

```text
Secure_Real-time_Messaging_App/
│
├── pom.xml                     # Parent POM quản lý versions và dependencyManagement
├── AGENTS.md                   # Quy chuẩn kỹ thuật bất biến
├── README.md                   # Hướng dẫn tổng quan
├── .env.example                # Mẫu biến môi trường (Gửi lên Git, không chứa bí mật)
├── server_identity.pub         # Khóa công khai Server để Client ghim khóa (Key Pinning)
│
├── securechat-common/          # MODULE DÙNG CHUNG (Giao thức & Crypto)
│   ├── pom.xml
│   └── src/main/java/com/securechat/
│       ├── common/protocol/    # Packet, MessageType, ProtocolSerializer
│       └── security/           # AESUtil, HKDFUtil, RSAUtil, SecureSession, SecurityUtils
│
├── securechat-server/          # MODULE SERVER (Khoa phụ trách)
│   ├── pom.xml                 # Cấu hình Maven Server (MSSQL, jBCrypt, Jakarta Mail, Dotenv)
│   └── src/main/java/com/securechat/
│       ├── server/             # ServerMain, ClientHandler, ClientConnectionManager
│       ├── bll/                # AuthService, UserBLL
│       ├── dao/                # UserDAO, EmailOtpDAO, DBConnection
│       ├── dto/                # UserDTO
│       ├── config/             # AppConfig
│       ├── validation/         # InputValidator backend
│       ├── utils/              # MailUtils
│       ├── exception/          # BusinessException
│       ├── security/           # PasswordHasher, ServerIdentityKeyManager, SecureTransport
│       └── tools/              # Phase3TestClient
│
├── securechat-client/          # MODULE CLIENT (Sự phụ trách)
│   ├── pom.xml                 # Cấu hình Maven Client (Shade ra securechat-client.jar)
│   └── src/
│       ├── main/resources/
│       │   └── server_identity.pub  # Đóng gói kèm JAR
│       └── main/java/com/securechat/client/
│           ├── ClientMain.java # Điểm khởi chạy Client chính
│           ├── controller/     # ClientController
│           ├── gui/            # MainFrame, LoginPanel, RegisterPanel, OTPPanel, ChatPanel
│           ├── network/        # NetworkManager, FileChunkUtils
│           ├── security/       # SecureTransport (C2S/S2C)
│           └── validation/     # InputValidator frontend
│
├── database/                   # Chứa script và tài liệu CSDL
│   ├── schema_mssql.sql        # File tạo bảng SQL Server (Phase 1 & 2)
│   └── README.md               # Tài liệu CSDL & mẫu thiết kế Phase 4
│
├── .gitignore                  # Bỏ qua thư mục target/, *.class, .env, *.key
└── docs/                       # Tài liệu (protocol.md, ScriptTest_CML.md, ...)
```

---

## 2. Chiến lược Quản lý Nhánh (Branching)

Tuyệt đối **KHÔNG** code trực tiếp hoặc push thẳng lên nhánh `main`.

* **`main`**: Nhánh trung tâm chứa code ổn định, đã qua kiểm thử.
* **`feature/khoa-server-db`**: Nhánh làm việc độc lập của Khoa (Backend Server, Database, RSA).
* **`feature/su-client-gui`**: Nhánh làm việc độc lập của Sự (Client GUI, Xử lý giao diện, AES).

---

## 3. Quy trình làm việc hằng ngày (Daily Workflow)

Mỗi khi bắt đầu hoặc kết thúc một buổi code, thực hiện theo vòng lặp sau trên máy tính cá nhân:

1. **Đảm bảo đang ở đúng nhánh:** `git checkout feature/ten-nhanh-cua-ban`
2. **Lưu thay đổi (Add):** `git add .`
3. **Ghi chú (Commit):** `git commit -m "Mô tả code vừa làm (VD: Thêm DAO cho User)"`
4. **Đẩy code (Push):** `git push origin feature/ten-nhanh-cua-ban`

---

## 4. Quy trình Gộp Code vào nhánh Main (Pull Request)

Khi một chức năng đã chạy thông suốt, tiến hành gộp vào nhánh chung:

1. Lên trang GitHub, chuyển sang nhánh của bạn, bấm **Compare & pull request**.
2. Đợi người còn lại kiểm tra. Bấm **Merge pull request** để gộp vào `main`.
3. **Cập nhật máy cá nhân:** Cả 2 thành viên kéo code mới nhất về để code không bị lỗi thời:
```bash
git checkout main
git pull origin main
git checkout feature/ten-nhanh-cua-ban
```

---

## 5. Phân công Công việc Chi tiết

**Chiến lược:** Xây dựng luồng đăng ký/OTP/đăng nhập và bắt tay mã hóa trước (Phase 1-3), sau đó tích hợp nhắn tin và chia sẻ file (Phase 4-5).

| Giai đoạn | Khoa (Server & Database) `feature/khoa-server-db` | Sự (Client UI & Payload) `feature/su-client-gui` |
| --- | --- | --- |
| **Phase 1: Nền tảng TCP & NDJSON** | Dựng TCP Server đa luồng, framing 1MB, ping-pong, cấu hình .env. | Kết nối TCP, gửi/nhận NDJSON bất đồng bộ không nghẽn EDT. |
| **Phase 2: Auth & Email OTP** | CSDL `dbo.users`, `dbo.email_otp`, băm BCrypt, gửi mail OTP qua Gmail SMTP. | Vẽ UI LoginPanel, RegisterPanel, OTPPanel, validate dữ liệu đầu vào. |
| **Phase 3: Secure Transport** | RSA 2048 ephemeral keypair, RSASSA-PSS chữ ký định danh, HKDF, AES-256-GCM. | Xác minh chữ ký định danh Server (Key Pinning), bắt tay trao đổi khóa, bọc SECURE_MESSAGE. |
| **Phase 4: Chat 1-1, Nhóm & File** | Bảng `dbo.messages`, `dbo.groups`, lưu trữ và broadcast tin nhắn, nhận chunk file 256 KiB. | Hoàn thiện ChatPanel, gửi tin nhắn, chia nhỏ file bằng `FileChunkUtils`, hiển thị lịch sử chat. |
| **Phase 5: Trạng thái & Bảo mật nâng cao** | Broadcast Online/Offline, Block user, quản lý danh sách bạn bè. | Danh sách bạn bè/nhóm trực tuyến, cập nhật trạng thái đã xem, giao diện chặn người dùng. |
