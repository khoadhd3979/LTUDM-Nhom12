# Đồ án: SecureChat-Java - Ứng dụng Nhắn tin Thời gian thực

> Hệ thống ứng dụng chat nhắn tin thời gian thực qua mạng, hỗ trợ đa luồng và mã hóa dữ liệu.
>
> **Mô hình:** Client - Server qua Socket TCP (Multi-Module Maven)
> **Ngôn ngữ:** Java 17+ (Giao diện Swing)
> **Quy mô nhóm:** 2 thành viên (Khoa & Sự)

---

## 📑 Mục lục

1. [1. Kiến trúc tổng thể & Công nghệ (⭐ Quan trọng nhất)](#1-kiến-trúc-tổng-thể--công-nghệ)
2. [2. Luồng bảo mật & Mã hóa dữ liệu (⭐ Quan trọng nhất)](#2-luồng-bảo-mật--mã-hóa-dữ-liệu)
3. [3. Thiết kế Cơ sở dữ liệu (CSDL) (⭐ Quan trọng)](#3-thiết-kế-cơ-sở-dữ-liệu-csdl)
4. [4. Tính năng cốt lõi (Client & Server) (⭐ Quan trọng)](#4-tính-năng-cốt-lõi-client--server)
5. [5. Phân công công việc & Git Workflow (🔥 Thiết yếu cho nhóm)](#5-phân-công-công-việc--git-workflow)
6. [6. Hướng dẫn cài đặt & Setup môi trường (🛠 Cần thiết)](#6-hướng-dẫn-cài-đặt--setup-môi-trường)
7. [7. Lộ trình phát triển (📅 Quản lý tiến độ)](#7-lộ-trình-phát-triển)
8. [8. Tính năng nâng cao (Điểm cộng) (🌟 Mở rộng)](#8-tính-năng-nâng-cao-điểm-cộng)

---

## 1. Kiến trúc tổng thể & Công nghệ

Dự án sử dụng kiến trúc **Client-Server** giao tiếp qua mạng TCP NDJSON, được tổ chức theo mô hình **Maven Multi-Module**:
* `securechat-common`: Chứa giao thức mạng (`Packet`, `MessageType`, `ProtocolSerializer`) và thuật toán mật mã dùng chung (`AESUtil`, `RSAUtil`, `HKDFUtil`, `SecureSession`).
* `securechat-server`: Backend TCP Server đa luồng, xử lý xác thực, CSDL SQL Server và mã hóa.
* `securechat-client`: Frontend Java Swing GUI, điều phối luồng giao diện bất đồng bộ với EDT.

---

## 2. Luồng bảo mật & Mã hóa dữ liệu

Yêu cầu kỹ thuật bắt buộc của hệ thống là bảo mật kênh truyền và dữ liệu:
* **Bảo vệ mật khẩu:** Mật khẩu người dùng được lưu ở dạng hash BCrypt, tuyệt đối không lưu plaintext.
* **Mã hóa lai (Hybrid Encryption):**
    * Sử dụng mã hóa bất đối xứng (RSA 2048) và chữ ký số RSASSA-PSS để xác thực danh tính Server (Key Pinning) và trao đổi khóa phiên.
    * Sử dụng mã hóa đối xứng (AES-256-GCM) để mã hóa toàn bộ dữ liệu trao đổi (`SECURE_MESSAGE`).
* **Kiểm tra đầu vào:** Toàn bộ dữ liệu đầu vào trên GUI và Backend đều được validate chặt chẽ theo đặc tả giao thức.

---

## 3. Thiết kế Cơ sở dữ liệu (CSDL)

Hệ thống lưu trữ trên Server tối thiểu gồm các thực thể:
* **Tài khoản người dùng (`dbo.users`):** Username (địa chỉ email), password hash, họ tên, giới tính, ngày sinh, trạng thái.
* **Mã xác thực OTP (`dbo.email_otp`):** Mã OTP băm BCrypt, thời hạn 10 phút, mục đích đăng ký/kích hoạt tài khoản.
* **Nhóm & Tin nhắn (Phase 4):** Lịch sử chat 1-1, chat nhóm, đính kèm file phân mảnh.

---

## 4. Tính năng cốt lõi (Client & Server)

### Chức năng phía Client (GUI)
* **Quản lý tài khoản:** Đăng ký tài khoản, nhập mã OTP xác thực email, đăng nhập bảo mật, đăng xuất an toàn.
* **Giao diện trực quan:** CardLayout mượt mà chuyển đổi Login, Register, OTP, và Chat.
* **Bảo mật kênh truyền:** Xác thực chữ ký Server khi kết nối, mã hóa toàn bộ gói tin đi/đến.

### Chức năng phía Server
* **Kết nối đa luồng:** Phục vụ nhiều client đồng thời với `ClientConnectionManager`.
* **Framing an toàn:** Giới hạn frame 1MB chống tấn công tràn bộ nhớ (`BoundedLineReader`).
* **Gửi OTP Email:** Gửi mã xác thực qua SMTP Gmail khi kích hoạt `SC_MAIL_ENABLED=true`.

---

## 5. Phân công công việc & Git Workflow

Dự án được chia cho **2 thành viên** nhằm tối ưu việc quản lý database và giao diện:
* **Khoa (Backend Server, Security & Database):** Nhánh `feature/khoa-server-db`
* **Sự (Client UI, Logic xử lý & Packet payload):** Nhánh `feature/su-client-gui`

---

## 6. Hướng dẫn cài đặt & Setup môi trường

### Công cụ yêu cầu
* **Java JDK:** Phiên bản 17 hoặc mới hơn
* **Apache Maven:** 3.8+
* **Database:** Microsoft SQL Server (hoặc SQL Server Express / Docker)
* **IDE Khuyên dùng:** IntelliJ IDEA / VS Code / Eclipse

### Thiết lập môi trường & Chạy ứng dụng
1. **Clone repository:**
   ```bash
   git clone <repo_url>
   cd Secure_Real-time_Messaging_App
   ```
2. **Cấu hình Cơ sở Dữ liệu:**
   - Chạy script `database/schema_mssql.sql` trên SQL Server để tạo database `SecureChatDB` cùng bảng `dbo.users` và `dbo.email_otp`.
   - Sao chép file `.env.example` thành `.env` và cập nhật thông tin kết nối (`SC_DB_USER`, `SC_DB_PASSWORD`).
3. **Build dự án với Maven:**
   ```bash
   mvn clean package
   ```
   Lệnh này biên dịch toàn bộ 3 module (`securechat-common`, `securechat-server`, `securechat-client`) và sinh ra 2 file JAR độc lập.
4. **Khởi động Server:**
   ```bash
   java -jar securechat-server/target/securechat-server.jar
   ```
5. **Khởi động Client (Java Swing GUI):**
   ```bash
   java -jar securechat-client/target/securechat-client.jar
   ```

---

## 7. Lộ trình phát triển

* **Phase 1:** Setup cấu trúc thư mục, TCP socket foundation, NDJSON framing (1MB limit).
* **Phase 2:** Triển khai CSDL `dbo.users`, `dbo.email_otp`, BCrypt, gửi Email OTP, UI Đăng ký / Xác thực OTP / Đăng nhập.
* **Phase 3:** Mã hóa lai: RSA-2048, RSASSA-PSS chữ ký định danh, HKDF, AES-256-GCM (`SECURE_MESSAGE`).
* **Phase 4:** Nhắn tin 1-1, chat nhóm, phân mảnh và truyền file đính kèm (`FileChunkUtils`).
* **Phase 5:** Broadcast Online/Offline, Block user, quản lý danh sách bạn bè.
