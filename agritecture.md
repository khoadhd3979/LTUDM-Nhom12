# Kiến trúc Thư mục Dự án SecureChat-Java (Multi-Module Maven)

```text
Secure_Real-time_Messaging_App/
├── pom.xml                               # Root Parent POM (packaging: pom)
├── AGENTS.md                             # Quy chuẩn kỹ thuật bất biến cho AI và Dev
├── README.md                             # Hướng dẫn tổng quan dự án
├── WorkFlow.md                           # Quy trình làm việc nhóm & Git branching
├── .gitignore                            # Quy tắc bỏ qua file rác, cache, secrets
├── .env.example                          # Mẫu cấu hình môi trường (placeholders)
├── server_identity.pub                   # Khóa công khai của Server (Key Pinning)
│
├── database/                             # Thư mục CSDL chuẩn của dự án
│   ├── schema_mssql.sql                  # Schema CSDL SQL Server (Phase 1 & 2)
│   └── README.md                         # Tài liệu CSDL & mẫu thiết kế Phase 4
│
├── docs/                                 # Tài liệu kỹ thuật
│   ├── protocol.md                       # Đặc tả giao thức NDJSON & Secure Transport
│   ├── ScriptTest_CML.md                 # Hướng dẫn test lệnh dòng lệnh
│   └── PACKAGE_REFACTOR_REPORT.md        # Báo cáo chi tiết tái cấu trúc mã nguồn
│
├── securechat-common/                    # MODULE CHUNG (securechat-common.jar)
│   ├── pom.xml                           # Thư viện: Jackson Databind
│   └── src/main/java/com/securechat/
│       ├── common/protocol/
│       │   ├── MessageType.java          # Hằng số định danh gói tin
│       │   ├── Packet.java               # Đối tượng khung truyền tin
│       │   └── ProtocolSerializer.java   # Serialize/Deserialize Jackson NDJSON
│       └── security/
│           ├── AESUtil.java              # Mã hóa AES-GCM & derive nonce
│           ├── HKDFUtil.java             # HKDF-SHA256 chuẩn RFC 5869
│           ├── RSAUtil.java              # RSA OAEP & RSASSA-PSS
│           ├── SecureSession.java        # Quản lý trạng thái và sequence phiên
│           └── SecurityUtils.java        # Tiện ích sinh OTP & ghép byte mảng
│
├── securechat-server/                    # MODULE SERVER (securechat-server.jar)
│   ├── pom.xml                           # Thư viện: MSSQL, jBCrypt, Jakarta Mail, Dotenv, Slf4j
│   └── src/main/java/com/securechat/
│       ├── server/
│       │   ├── ServerMain.java           # Điểm khởi chạy Server chính
│       │   ├── ClientHandler.java        # Xử lý kết nối client đa luồng
│       │   ├── ClientConnectionManager.java # Quản lý các phiên kết nối online
│       │   └── BoundedLineReader.java    # Đọc NDJSON giới hạn kích thước frame (1MB)
│       ├── bll/
│       │   ├── AuthService.java          # Nghiệp vụ đăng ký, OTP, đăng nhập, đổi pass
│       │   └── UserBLL.java              # Nghiệp vụ tài khoản người dùng
│       ├── dao/
│       │   ├── DBConnection.java         # Quản lý kết nối SQL Server Facade
│       │   ├── UserDAO.java              # Truy vấn bảng dbo.users
│       │   └── EmailOtpDAO.java          # Truy vấn bảng dbo.email_otp
│       ├── dto/
│       │   └── UserDTO.java              # Model dữ liệu người dùng
│       ├── config/
│       │   └── AppConfig.java            # Đọc cấu hình từ .env hoặc biến môi trường
│       ├── validation/
│       │   └── InputValidator.java       # Kiểm tra hợp lệ dữ liệu backend
│       ├── utils/
│       │   └── MailUtils.java            # Gửi mã xác thực OTP qua SMTP Gmail
│       ├── exception/
│       │   └── BusinessException.java    # Biểu diễn lỗi nghiệp vụ hệ thống
│       ├── security/
│       │   ├── PasswordHasher.java       # Hash & verify mật khẩu với BCrypt
│       │   ├── ServerIdentityKeyManager.java # Quản lý khóa RSA của Server
│       │   └── SecureTransport.java      # Giao vận mã hóa phía Server (S2C out, C2S in)
│       └── tools/
│           └── Phase3TestClient.java     # CLI Test Client kiểm thử bắt tay bảo mật
│
└── securechat-client/                    # MODULE CLIENT (securechat-client.jar)
    ├── pom.xml                           # Thư viện: securechat-common, slf4j
    └── src/
        ├── main/resources/
        │   └── server_identity.pub       # Public key đóng gói sẵn trong JAR client
        └── main/java/com/securechat/client/
            ├── ClientMain.java           # Điểm khởi chạy Client chính (Swing EDT)
            ├── controller/
            │   └── ClientController.java # Điều phối luồng giao diện và mạng
            ├── gui/
            │   ├── MainFrame.java        # Khung cửa sổ chính chứa CardLayout
            │   ├── LoginPanel.java       # Màn hình đăng nhập
            │   ├── RegisterPanel.java    # Màn hình đăng ký
            │   ├── OTPPanel.java         # Màn hình nhập mã xác thực OTP
            │   └── ChatPanel.java        # Màn hình chat trực tuyến
            ├── network/
            │   ├── NetworkManager.java   # Socket client đa luồng & callback EDT
            │   └── FileChunkUtils.java   # Cắt nhỏ file truyền qua mạng (Phase 4)
            ├── security/
            │   └── SecureTransport.java  # Giao vận mã hóa phía Client (C2S out, S2C in)
            └── validation/
                └── InputValidator.java   # Kiểm tra tính hợp lệ dữ liệu trên giao diện
```
