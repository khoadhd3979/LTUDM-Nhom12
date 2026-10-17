# Quy trình Làm việc nhóm & Phân công Dự án (SecureChat-Java)

Tài liệu này quy định luồng làm việc với Git/GitHub, cấu trúc thư mục chuẩn và chi tiết phân công công việc cho từng giai đoạn của dự án.

---

## 1. Cấu trúc Thư mục Dự kiến

Dự án được chia thành 2 module độc lập (Client và Server) nằm chung trong một Repository để dễ quản lý chung một CSDL và giao thức chuẩn.

```text
Secure_Real-time_Messaging_App/
│
├── securechat-server/          # CÔNG VIỆC CỦA KHOA (Chuẩn Maven)
│   ├── .env                    # (Khoa tự tạo) Chứa biến môi trường cá nhân (KHÔNG GỬI LÊN GIT)
│   ├── .env.example            # Chứa các biến mẫu để Sự biết cấu hình (Gửi lên Git)
│   ├── pom.xml                 # Cấu hình Maven (Thư viện: SQL Server, Jackson, BCrypt, Dotenv)
│   └── src/main/java/com/securechat/
│       ├── bll/                # Nghiệp vụ: UserBLL, GroupBLL
│       ├── common/protocol/    # Giao thức chung: Packet, MessageType, ProtocolSerializer
│       ├── config/             # Cấu hình: AppConfig (đọc từ .env)
│       ├── dao/                # Truy vấn CSDL: UserDAO, DBConnection (Facade)
│       ├── dto/                # Ánh xạ CSDL: UserDTO
│       ├── security/           # Bảo mật: SecurityUtils (BCrypt)
│       ├── server/             # Mạng TCP: ServerMain, ClientHandler
│       └── utils/              # Tiện ích: MailUtils
│
├── securechat-client/          # CÔNG VIỆC CỦA SỰ (Nên dùng Maven để tái sử dụng thư viện chung)
│   ├── pom.xml                 # Cấu hình Maven (Jackson để parse JSON)
│   └── src/main/java/com/securechat/client/
│       ├── controller/         # Xử lý sự kiện từ GUI
│       ├── gui/                # Giao diện Java Swing
│       ├── network/            # Socket Client, gửi nhận JSON theo dòng (NDJSON)
│       └── ...
│
├── database/                   # Chứa script CSDL
│   └── schema_mssql.sql        # File tạo bảng SQL Server
│
├── _legacy/                    # Nơi chứa các file code cũ, rác, code backup (Không biên dịch)
├── .gitignore                  # Bỏ qua thư mục target/, .env
└── docs/                       # Tài liệu (PROTOCOL.md, WORKFLOW.md)

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

**Chiến lược:** Xây dựng luồng nhắn tin thời gian thực (text thô) trước, sau đó tích hợp mã hóa (RSA/AES) vào sau.

| Giai đoạn | Khoa (Server & Database) `feature/khoa-server-db` | Sự (Client UI & Payload) `feature/su-client-gui` |
| --- | --- | --- |
| **1. Nền tảng & DB** | Thiết kế CSDL (bảng User, Message). Viết DTO/DAO/BLL. Tích hợp API Email OTP. | Dùng Swing vẽ form Đăng nhập/Đăng ký. Validate dữ liệu. Tạo Socket Client kết nối. |
| **2. Đa luồng & Chat 1-1** | Dựng Server Multithreading quản lý danh sách Client. Viết hàm lưu/tải lịch sử chat. | Vẽ UI Dashboard & Khung chat. Gửi/nhận tin nhắn text thô qua Socket và render lên UI. |
| **3. Mở rộng & File** | Xử lý logic tạo nhóm, thành viên. Tạo luồng stream nhận, lưu file đính kèm/sticker. | Vẽ UI tạo nhóm. Viết thuật toán phân mảnh gói dữ liệu (packet) để gửi file < 1MB. |
| **4. Ghép Mã hóa** | Cài đặt thuật toán RSA (Sinh khóa / Cấp phát khóa phiên). | Đóng gói hàm gửi/nhận qua class `SecurityUtils` (Mã hóa đối xứng AES). |
| **5. Trạng thái & UI** | Xử lý logic Broadcast báo hiệu Online/Offline. Ghi Log hệ thống. API Block user. | Thiết kế UI tải tin nhắn cũ. Cập nhật trạng thái "đã xem". Chức năng chặn (Block). |

---

## 6. Quy chuẩn Giao tiếp Dữ liệu (Packet Concept)

Luồng dữ liệu giữa Client và Server thống nhất dùng chuỗi phân cách theo cú pháp chuẩn để tiện phân tích (parse) và đắp mã hóa sau này.

* **Cú pháp dự kiến:** `[ACTION]|[SENDER]|[DATA1]|[DATA2]`
* *Ví dụ Đăng nhập:* `LOGIN|su@gmail.com|123456`
* *Ví dụ Gửi tin:* `CHAT_P2P|su@gmail.com|khoa@gmail.com|Hello bro`

```

```
