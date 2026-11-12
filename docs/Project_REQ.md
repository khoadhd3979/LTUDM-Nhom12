Chào bạn, tôi là Senior System Architect, Software Engineering Analyst, Cybersecurity Architect và Technical Researcher. 

Tôi đã đọc kỹ lưỡng toàn bộ 8 tài liệu bài giảng (C1 đến C6, JSON-API, An ninh mạng cơ bản) và bức ảnh chứa Đề cương chi tiết/Yêu cầu đồ án (Nhóm 12 - Đề tài 01: Ứng dụng Chat nhắn tin thời gian thực). 

Dưới đây là Báo cáo Phân tích Yêu cầu và Quyết định Kiến trúc Master cho dự án **SecureChat-Java**. Tôi tuân thủ nghiêm ngặt các nguyên tắc: Không code, phân định rõ nguồn tài liệu, trích xuất chính xác đề xuất của giảng viên, và phân loại rõ ràng các mức độ ưu tiên.

---

# BÁO CÁO PHÂN TÍCH VÀ ĐẶC TẢ KỸ THUẬT TỔNG THỂ (MASTER SPEC)

## PART 1 — DOCUMENT INVENTORY (Danh mục tài liệu)

Tổng số tài liệu đã nhận và đọc: **08** (07 file PDF, 01 file ảnh đề cương).

| STT | Tên file | Loại tài liệu | Số trang | Trạng thái đọc | Lỗi/Thiếu nội dung | Mức độ liên quan |
|---|---|---|---|---|---|---|
| 1 | Lập trình ứng dụng mạng - C1.pdf | Bài giảng | 53 | Đã đọc toàn bộ | Không | Tổng quan mạng, TCP/IP, Port, VPS |
| 2 | Lập trình ứng dụng mạng - C2.pdf | Bài giảng | 58 | Đã đọc toàn bộ | Không | TCP Socket, Client/Server, I/O Stream |
| 3 | Lập trình ứng dụng mạng - C3.pdf | Bài giảng | 23 | Đã đọc toàn bộ | Không | UDP Socket, Datagram, Encoding |
| 4 | Lập trình ứng dụng mạng - C4.pdf | Bài giảng | 40 | Đã đọc toàn bộ | Không | Đa luồng, ThreadPool, ExecutorService, Đồng bộ hóa |
| 5 | Lập trình ứng dụng mạng - C5.pdf | Bài giảng | 36 | Đã đọc toàn bộ | Không | Giao thức mạng, JSON, Length-prefix, OTP Email |
| 6 | Lập trình ứng dụng mạng - C6.pdf | Bài giảng | 35 | Đã đọc toàn bộ | Không | Lập trình phân tán, RMI, Socket vs RMI |
| 7 | Lập trình ứng dụng mạng - JSON-API (1).pdf | Bài giảng | 23 | Đã đọc toàn bộ | Không | Web API, JSON (org.json), HTTP Client |
| 8 | Lập trình ứng dụng mạng - An ninh mạng cơ bản.pdf | Bài giảng | 65 | Đã đọc toàn bộ | Không | Băm, PBKDF2, AES-GCM, RSA, Mã hóa lai, MITM |
| 9 | Đề cương & Yêu cầu đồ án (Ảnh) | Quy định môn học | N/A | Đã đọc toàn bộ | Không | Yêu cầu bắt buộc, Rubric, Đề tài 01, Cấm công nghệ |

---

## PART 2 — LECTURE KNOWLEDGE SYNTHESIS (Tổng hợp kiến thức)

Hệ thống kiến thức được giảng viên cung cấp rất chặt chẽ, đi từ nền tảng mạng đến lập trình Socket, xử lý đa luồng, thiết kế giao thức, bảo mật và triển khai.

1. **Mạng căn bản & Socket (C1, C2, C3):** 
   - Mô hình OSI/TCP-IP. Sự khác biệt giữa TCP (tin cậy, có kết nối, luồng byte) và UDP (không tin cậy, datagram, nhanh).
   - Lập trình TCP Client-Server sử dụng `Socket` và `ServerSocket`. Vòng đời: `accept()` -> `getInputStream()`/`getOutputStream()` -> `read()`/`write()` -> `close()`.
   - **Vấn đề cốt lõi:** TCP không giữ ranh giới thông điệp (C5 - Trang 23). Do đó cần giải pháp phân định (Delimiter, Length-prefix).
2. **Xử lý đa luồng (C4):**
   - Mô hình Iterative Server (tuần tự) gây nghẽn cổ chai. Mô hình Concurrent Server (đa luồng) giải quyết vấn đề này.
   - Sử dụng `ThreadPool` (qua `ExecutorService` hoặc `ThreadPoolExecutor`) để tránh overhead tạo/hủy thread liên tục, giới hạn tài nguyên và quản lý task queue.
   - Vấn đề tranh chấp tài nguyên (Race Condition) khi nhiều thread truy cập dữ liệu chung -> cần `synchronized` hoặc `ConcurrentHashMap`.
3. **Truyền dữ liệu & Giao thức (C5):**
   - Serialization: Chuyển Object thành JSON (dùng org.json hoặc Gson/Jackson).
   - Encoding: Luôn sử dụng UTF-8. Phân biệt rõ số ký tự và số byte (quan trọng khi dùng Length-prefix).
   - Giao thức ứng dụng: Cần định nghĩa `type` (LOGIN, MSG, ERROR) và `data` (payload).
4. **Bảo mật (An ninh mạng):**
   - **Băm mật khẩu:** Không lưu plaintext. Dùng PBKDF2 + Salt (chống Rainbow Table).
   - **Mã hóa đối xứng:** AES-GCM (cung cấp cả bảo mật và toàn vẹn thông qua tag). Không dùng ECB, không lặp lại IV.
   - **Mã hóa bất đối xứng:** RSA-OAEP dùng để trao đổi khóa (mã hóa khóa phiên AES).
   - **Chống MITM & Replay Attack:** Ghim khóa Public Key của Server, thêm Sequence Number vào AAD (Additional Authenticated Data).
5. **Lập trình phân tán (C6):** Giới thiệu RMI, nhưng giảng viên định hướng dự án sẽ dùng Socket thuần để kiểm soát hoàn toàn giao thức.

---

## PART 3 — LECTURER RECOMMENDATIONS (Đề xuất cuối bài của Giảng viên)

| Bài học | Vấn đề | Các phương án được đề cập | Đề xuất cuối bài của giảng viên | Trang nguồn | Ứng dụng vào SecureChat |
|---|---|---|---|---|---|
| C4 | Quản lý Thread cho Server | `new Thread()`, ThreadPool | Dùng ThreadPool để tránh cạn kiệt tài nguyên và overhead. | C4 (Trang 19, 22) | Dùng `ExecutorService` (FixedThreadPool) cho Server. |
| C5 | Định dạng & Phân định thông điệp | Delimiter, Length-prefix, Fixed size | Dùng Length Prefixing + JSON là giải pháp tổng quát, linh hoạt. | C5 (Trang 24, 26) | Dùng Length-prefix (4 bytes) + JSON payload. |
| C5 | Gửi Email OTP | SMTP riêng, Relay, Email cá nhân | Dùng email cá nhân (Gmail) + App Password. | C5 (Trang 5, 8, 12) | Dùng Gmail SMTP + App Password cho OTP. |
| C6 | Công nghệ phân tán | Socket, RMI, REST, gRPC | **Đồ án vẫn dùng Socket** (áp dụng tư duy đa luồng an toàn, tách lỗi nghiệp vụ khỏi lỗi mạng). | C6 (Trang 33, 34) | Bắt buộc dùng Java Socket. |
| An Ninh | Băm mật khẩu | SHA-256, PBKDF2, bcrypt, Argon2 | PBKDF2 (có sẵn trong JDK) với Salt ngẫu nhiên. | An ninh (Trang 19, 20) | Dùng PBKDF2WithHmacSHA256, 600,000 vòng lặp. |
| An Ninh | Mã hóa dữ liệu | AES-ECB, AES-CBC, AES-GCM | AES-GCM (mã hóa + toàn vẹn), IV mới mỗi gói. | An ninh (Trang 26, 29, 31) | Mã hóa tin nhắn bằng AES/GCM/NoPadding, IV 12 bytes. |
| An Ninh | Trao đổi khóa & MITM | RSA, ECDH, Ghim khóa, CA | Mã hóa lai (RSA trao khóa phiên). Chống MITM bằng cách **ghim khóa (pinning)**. | An ninh (Trang 40, 47) | Server giữ RSA Private Key, Client cài sẵn RSA Public Key. |

---

## PART 4 — FULL REQUIREMENTS SPECIFICATION (Đặc tả Yêu cầu)

*(Trích xuất từ Ảnh Đề cương & Yêu cầu đồ án)*

| ID | Yêu cầu | Nguồn | Priority | Loại | Thành phần | Cách nghiệm thu |
|---|---|---|---|---|---|---|
| **REQ-GEN-01** | Ngôn ngữ Java, Client-Server. Giao diện Client dùng Swing/JavaFX. | Ảnh (Mục 2.1) | MANDATORY | Chung | Toàn hệ thống | Chạy thực tế, có giao diện. |
| **REQ-GEN-02** | Giao tiếp mạng trực tiếp bằng Java Socket (TCP/UDP). | Ảnh (Mục 2.2) | MANDATORY | Mạng | Network Layer | Sử dụng `java.net.Socket`, `ServerSocket`. |
| **REQ-GEN-03** | **Cấm:** WebSocket, Netty, gRPC, Apache MINA, KryoNet, Java RMI, Spring, REST API. | Ảnh (Mục 2.2) | MANDATORY | Mạng | Network Layer | Không có dependency cấm trong `pom.xml`. |
| **REQ-GEN-04** | Server phục vụ nhiều Client, dùng ThreadPool (`ExecutorService`). | Ảnh (Mục 2.3) | MANDATORY | Đa luồng | Server | Kiểm tra code Server, chạy >50 client cùng lúc. |
| **REQ-GEN-05** | Đồng bộ hóa dữ liệu dùng chung (`synchronized`, `ConcurrentHashMap`). | Ảnh (Mục 2.3) | MANDATORY | Đa luồng | BLL/DAO | Kiểm thử tải, không lỗi race condition. |
| **REQ-GEN-06** | **Bảo mật:** Mã hóa lai (RSA + AES). Mật khẩu dùng PBKDF2 + Salt. | Ảnh (Mục 2.4) | MANDATORY | Bảo mật | Security Layer | Kiểm tra DB thấy hash, Wireshark thấy ciphertext. |
| **REQ-GEN-07** | **CSDL:** SQL Server/MySQL/PostgreSQL, dùng JDBC, Transaction, Indexing. | Ảnh (Mục 2.5) | MANDATORY | CSDL | DAO Layer | Truy vấn DB, kiểm tra tính toàn vẹn dữ liệu. |
| **REQ-GEN-08** | Triển khai Server trên VPS/Cloud, Client kết nối qua Internet. | Ảnh (Mục 4.1) | MANDATORY | Triển khai | Hạ tầng | Chạy Server trên VPS, Client ở máy khác kết nối. |
| **REQ-FUN-01** | Đăng ký, Đăng nhập, Cập nhật thông tin cá nhân. | Ảnh (Đề tài 01) | MANDATORY | Chức năng | Client/Server | Demo thao tác. |
| **REQ-FUN-02** | Xác thực email bằng OTP (hạn 10 phút). | Ảnh (Đề tài 01) | MANDATORY | Chức năng | Server/Mail | Nhận mail OTP, nhập thành công. |
| **REQ-FUN-03** | Tạo nhóm, thêm/xóa thành viên, quản trị nhóm. | Ảnh (Đề tài 01) | MANDATORY | Chức năng | Client/Server | Demo tạo nhóm, phân quyền. |
| **REQ-FUN-04** | Chat 1-1 và Chat nhóm (thời gian thực). | Ảnh (Đề tài 01) | MANDATORY | Chức năng | Client/Server | Gửi/nhận tin nhắn tức thời. |
| **REQ-FUN-05** | Gửi file đính kèm (<1MB) qua kênh đã mã hóa, gửi sticker. | Ảnh (Đề tài 01) | MANDATORY | Chức năng | Client/Server | Chọn file, gửi, bên kia tải về. |
| **REQ-FUN-06** | Hiển thị trạng thái (đã gửi, đã nhận, đã xem), trạng thái online/offline. | Ảnh (Đề tài 01) | MANDATORY | Chức năng | Client/Server | Trạng thái thay đổi theo thời gian thực. |
| **REQ-FUN-07** | Lưu lịch sử chat, thống kê số người dùng, ghi log, khóa user, broadcast. | Ảnh (Đề tài 01) | MANDATORY | Chức năng | Server/DB | Truy vấn DB thấy lịch sử, Admin gửi thông báo. |
| **REQ-BONUS-01** | Cài đặt CI/CD (GitHub Actions) để tự động build và kiểm tra. | Ảnh (Mục 9.4) | BONUS | Bonus | DevOps | Xem file workflow `.yml`. |
| **REQ-GIT-01** | Repo GitHub có >3 tuần commit, có README, `.gitignore`, chia nhánh. | Ảnh (Mục 3.1) | MANDATORY | Quy trình | Github | Kiểm tra lịch sử commit. |

---

## PART 5 — TECHNICAL DECISION RECORDS (Quyết định Kỹ thuật)

### TDR-01: Cryptographic Suite (Bộ giải pháp bảo mật)
*   **Vấn đề:** Đảm bảo tính bí mật, toàn vẹn và xác thực cho tin nhắn.
*   **Các lựa chọn:** AES-CBC, AES-GCM, RSA, ECDH, Diffie-Hellman.
*   **Ý kiến giảng viên:** [LECTURER RECOMMENDATION] Dùng mô hình mã hóa lai: RSA-OAEP trao khóa phiên AES, AES-GCM mã hóa dữ liệu. Băm mật khẩu dùng PBKDF2. Chống MITM bằng Ghim khóa (Pinning).
*   **Quyết định:** Áp dụng đúng mô hình mã hóa lai của giảng viên.
*   **Cấu hình chính xác:**
    *   **Password Hashing:** `PBKDF2WithHmacSHA256`, Salt 16 bytes (`SecureRandom`), 600,000 iterations, Key length 256 bits.
    *   **Key Exchange:** `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`, Key size 2048 bits.
    *   **Symmetric Encryption:** `AES/GCM/NoPadding`, Key size 256 bits, IV 12 bytes (`SecureRandom`), Tag 128 bits.
    *   **Frame Structure:** `[Seq 8 bytes][IV 12 bytes][Ciphertext][Tag 16 bytes]`. `Seq` được đưa vào AAD để chống replay.
*   **Lý do:** Đáp ứng đúng yêu cầu môn học và thực tiễn bảo mật. AES-GCM cung cấp AEAD (Authenticated Encryption with Associated Data).

### TDR-02: Network Protocol và Data Format
*   **Vấn đề:** TCP là luồng byte, không có ranh giới. Cần định dạng dữ liệu để dễ parse.
*   **Ý kiến giảng viên:** [LECTURER RECOMMENDATION] Dùng Length-prefix + JSON.
*   **Quyết định:** Dùng Length-prefix (4 bytes) để phân định Frame. Payload bên trong là JSON (sử dụng thư viện Jackson).
*   **Cấu hình:**
    *   Gửi: `out.writeInt(jsonString.getBytes(UTF_8).length)` -> `out.write(jsonString.getBytes(UTF_8))`.
    *   Nhận: `int len = in.readInt()` -> `in.readFully(byteArray)` -> `new String(byteArray, UTF_8)`.
*   **Lý do:** Hỗ trợ gửi file nhị phân (ảnh/sticker) dễ dàng bằng cách Base64 trong JSON hoặc gửi frame nhị phân riêng.

### TDR-03: Threading Model
*   **Vấn đề:** Server phục vụ hàng trăm Client.
*   **Ý kiến giảng viên:** [LECTURER RECOMMENDATION] Dùng ThreadPool (`ExecutorService`), tránh `new Thread()`.
*   **Quyết định:** Sử dụng `Executors.newFixedThreadPool(50)` cho Server. Sử dụng `ConcurrentHashMap` để lưu trữ danh sách Client đang online (`Map<String, ClientHandler>`).
*   **Lý do:** Giới hạn tài nguyên, tái sử dụng thread, đảm bảo an toàn luồng.

### TDR-04: Database
*   **Vấn đề:** Lưu trữ dữ liệu người dùng, nhóm, tin nhắn.
*   **Yêu cầu:** SQL Server (theo prompt).
*   **Quyết định:** Sử dụng SQL Server kết hợp JDBC.
*   **Lý do:** Đúng bối cảnh đồ án. Đảm bảo Transaction cho các thao tác gửi tin nhắn nhóm.

---

## PART 6 — TARGET ARCHITECTURE (Kiến trúc Mục tiêu)

Hệ thống được thiết kế theo mô hình **Client-Server** thuần túy, phân tầng rõ ràng.

### 6.1. Module Structure (Maven Multi-module)
*   `securechat-common`: Chứa DTO (Data Transfer Object), các hằng số giao thức (MessageType, ErrorCode), tiện ích Crypto (dùng chung).
*   `securechat-server`: Chứa logic Server, kết nối DB, xử lý nghiệp vụ.
*   `securechat-client`: Chứa GUI (Swing/JavaFX), kết nối mạng, xử lý sự kiện UI.

### 6.2. Package Structure
*   **Client:** 
    *   `ui`: Chứa các form (Login, Chat, Contact).
    *   `network`: `SocketClient`, `ServerListener` (luồng đọc).
    *   `controller`: Xử lý sự kiện UI.
*   **Server:**
    *   `network`: `SocketServer`, `ClientHandler` (Runnable).
    *   `dao`: `UserDAO`, `MessageDAO`, `GroupDAO`.
    *   `bll`: `AuthService`, `ChatService`.
    *   `security`: `CryptoUtil`, `KeyManager`.
    *   `db`: `DatabaseConnection`.

### 6.3. Network & Security Architecture
1.  **Khởi tạo kết nối:** Client tạo `Socket` tới Server.
2.  **Bắt tay (Handshake):** 
    *   Client sinh khóa phiên AES (`KeyGenerator.getInstance("AES")`).
    *   Client mã hóa khóa AES bằng Public Key của Server (`RSA/ECB/OAEPWithSHA-256AndMGF1Padding`).
    *   Client gửi khóa đã mã hóa cho Server.
    *   Server giải mã bằng Private Key để lấy khóa phiên.
3.  **Giao tiếp:** Các gói tin gửi đi được đóng gói `[Seq][IV][Ciphertext][Tag]`.
4.  **Đa luồng:** Main thread của Server chỉ gọi `accept()`. Mỗi khi có Client, đẩy `ClientHandler` vào `ThreadPool`.

---

## PART 7 — DATABASE AND DATA DESIGN (Thiết kế CSDL)

*   **Hệ quản trị:** SQL Server.
*   **Lý do:** Yêu cầu đề bài, hỗ trợ tốt JDBC, Transaction.

**Sơ đồ khái niệm (ERD):**
*   **Users:** `id` (PK), `username`, `password_hash`, `email`, `is_verified`, `status`, `created_at`.
*   **Groups:** `id` (PK), `name`, `creator_id` (FK -> Users.id), `created_at`.
*   **GroupMembers:** `group_id` (FK), `user_id` (FK), `role` (Admin/Member). PK phức hợp `(group_id, user_id)`.
*   **Messages:** `id` (PK), `sender_id` (FK), `receiver_id` (FK, nullable nếu chat nhóm), `group_id` (FK, nullable nếu chat 1-1), `content` (NVARCHAR(MAX)), `file_url` (NVARCHAR(255)), `status` (SENT/DELIVERED/SEEN), `created_at`.
*   **Friends:** `user_id1` (FK), `user_id2` (FK), `status` (PENDING/ACCEPTED).

**Quy tắc Toàn vẹn & Transaction:**
*   Sử dụng Transaction khi tạo nhóm (thêm Group và thêm GroupMembers).
*   Đánh Index trên `Messages.created_at` để tối ưu load lịch sử chat.
*   Ràng buộc NOT NULL, UNIQUE (username, email).
*   Dùng PreparedStatement để chống SQL Injection.

---

## PART 8 — SECURITY BASELINE (Chuẩn Bảo mật)

Mọi mã nguồn phải tuân thủ các quy tắc sau, nếu vi phạm sẽ bị trừ điểm A3 (Bảo mật):

1.  **Password:** Không bao giờ lưu plaintext. Chỉ lưu `iterations:salt:hash` (PBKDF2).
2.  **Encryption at Rest (DB):** Không cần mã hóa toàn bộ DB, nhưng các trường nhạy cảm (nếu có) nên được bảo vệ.
3.  **Encryption in Transit:** 100% tin nhắn, file, sticker gửi qua Socket phải được mã hóa AES-GCM. Không gửi plaintext.
4.  **Key Management:** 
    *   Server Private Key lưu trong file `server_private.key` (được `.gitignore`).
    *   Client Public Key được nhúng sẵn trong source code (`server_public.key`).
    *   Khóa phiên (Session Key) được sinh ngẫu nhiên mỗi lần kết nối.
5.  **Replay Attack:** Mọi gói tin phải có `Sequence Number` (tăng dần). Server từ chối các gói tin có `Seq <= lastRecvSeq`.
6.  **MITM:** Tuyệt đối không nhận Public Key từ Server qua mạng lúc runtime. Client phải dùng Public Key cài sẵn (Pinning).
7.  **OTP:** OTP có hạn (10 phút), giới hạn số lần thử (5 lần), xóa khỏi bộ nhớ/DB sau khi dùng.

---

## PART 9 — BONUS ROADMAP (Lộ trình Điểm cộng)

*   **Must have (Bắt buộc đạt 8-9đ):** Hoàn thiện 17 chức năng, Socket, Mã hóa lai, CSDL, Triển khai VPS.
*   **Should have (Nên có):** 
    *   **CI/CD:** GitHub Actions tự động build (`mvn clean package`). (+0.5đ)
    *   **Git chất lượng:** Commit đều đặn, chia nhánh rõ ràng, có Pull Request. (+0.5đ)
*   **Could have (Có thể có nếu còn thời gian):**
    *   **Monitoring:** Ghi log hệ thống ra file (SLF4J + Logback) và có giao diện xem log cho Admin.
    *   **Tài liệu:** Vẽ biểu đồ tuần tự (Sequence Diagram) cho quá trình Handshake và gửi tin nhắn.
*   **Không chạy theo Bonus mà bỏ quên Core:** Ưu tiên số 1 là chức năng Chat và Mã hóa phải hoạt động mượt mà.

---

## PART 10 — MASTER PROJECT STANDARD

Dưới đây là tài liệu chuẩn `SECURECHAT_MASTER_TECHNICAL_SPEC.md` (Dùng làm kim chỉ nam cho giai đoạn code).

```markdown
# SECURECHAT_MASTER_TECHNICAL_SPEC.md

## 1. REQUIREMENTS OVERVIEW
- **Core:** Chat 1-1, Chat Group, Gửi file (<1MB), OTP Email, Trạng thái tin nhắn.
- **Platform:** Java 17, Java Swing (Client), Maven Multi-module.
- **Network:** TCP Socket thuần (Không WebSocket, RMI, Netty).
- **Database:** SQL Server.

## 2. ARCHITECTURE & THREADING
- **Model:** Client-Server. Server chạy đa luồng.
- **Threading:** `ExecutorService` (FixedThreadPool = 50) cho Server. Client dùng 1 luồng chính (UI) và 1 luồng phụ (Nhận tin nhắn).
- **Synchronization:** Sử dụng `ConcurrentHashMap<String, ClientHandler>` để quản lý kết nối. Dùng `synchronized` khi ghi file log hoặc cập nhật tài nguyên dùng chung.

## 3. NETWORK PROTOCOL (WIRE FORMAT)
- **Frame Format:** `[4 bytes Length][N bytes Payload]`.
- **Payload Format:** JSON (Jackson Databind). Encoding: `UTF-8`.
- **Handshake:**
  1. Client gửi `[RSA Encrypted AES Key]`.
  2. Server giải mã, lưu `sessionKey` vào `ClientHandler`.
- **Secure Message Frame (Sau Handshake):**
  `[Seq (8 bytes)] [IV (12 bytes)] [Ciphertext] [Tag (16 bytes)]`
  *(Gửi đi dưới dạng Length-Prefix)*
- **Replay Protection:** Server kiểm tra `Seq > lastReceivedSeq`. Nếu sai -> Ngắt kết nối.

## 4. CRYPTOGRAPHIC SUITE (CHUẨN BẢO MẬT)
- **Password Hashing:** `PBKDF2WithHmacSHA256`, Salt 16 bytes, Iterations 600,000.
- **Symmetric Encryption:** `AES/GCM/NoPadding`, Key 256 bits, IV 12 bytes (Sinh mới mỗi frame).
- **Asymmetric (Handshake):** `RSA/ECB/OAEPWithSHA-256AndMGF1Padding`, Key 2048 bits.
- **Key Pinning:** Client lưu trữ `server_public.key` tĩnh trong resource.

## 5. DATABASE SCHEMA (SQL Server)
- `Users(Id, Username, PasswordHash, Email, IsVerified, Status)`
- `Messages(Id, SenderId, ReceiverId, GroupId, Content, FileUrl, Status, CreatedAt)`
- `Groups(Id, Name, CreatorId)`
- `GroupMembers(GroupId, UserId, Role)`
- *Bắt buộc dùng PreparedStatement. Các thao tác tạo nhóm/kết bạn phải dùng Transaction.*

## 6. CODING CONVENTIONS
- **Java:** CamelCase cho biến/phương thức, PascalCase cho class. Tuân thủ OOP.
- **Exception:** Bắt và xử lý `IOException` cẩn thận. Phân biệt lỗi mạng (mất kết nối) và lỗi nghiệp vụ (sai mật khẩu).
- **Security:** Không hardcode mật khẩu DB. Đọc từ `application.properties`. Thêm file chứa key vào `.gitignore`.

## 7. TESTING STRATEGY
- **Unit Test:** Test `CryptoUtil` (mã hóa/giải mã), `OtpService`.
- **Integration Test:** Test luồng Client-Server, đảm bảo JSON parse đúng.
- **Load Test:** Dùng công cụ hoặc viết script giả lập 50 client kết nối cùng lúc, kiểm tra không sập Server.

## 8. DEPLOYMENT
- **Server:** Chạy trên VPS (Ubuntu). Mở port (VD: 5000).
- **Client:** Chạy trên máy Windows/Mac. Trỏ IP về VPS.
- **Firewall:** Đảm bảo port được mở trên VPS (Security Group / UFW).
```

---

## 9. QUALITY GATE & KẾT LUẬN

*   **Đã đọc toàn bộ PDF chưa?** Rồi. (Đã xem từ trang đầu đến trang cuối của 7 file).
*   **Đã xem các trang cuối của từng bài học chưa?** Rồi. (Trích xuất chuẩn xác các trang tổng kết).
*   **Đã trích xuất đề xuất của giảng viên chưa?** Rồi (Bảng PART 3).
*   **Có nhầm lẫn giữa yêu cầu bắt buộc và đề xuất không?** Đã phân định rõ trong PART 4.
*   **Có lựa chọn thuật toán sai mục đích không?** Không (Băm dùng PBKDF2, Mã hóa dùng AES-GCM + RSA).
*   **Có chọn giải pháp trái quy định môn học không?** Không. Tuân thủ tuyệt đối việc dùng Socket thuần, không dùng RMI/WebSocket.
*   **Có bỏ sót điểm cộng không?** Đã đưa CI/CD và Git rules vào.
*   **Có nguồn và số trang cho từng nhận định quan trọng không?** Có.

**Kết luận:** Tài liệu `SECURECHAT_MASTER_TECHNICAL_SPEC.md` đã sẵn sàng. Giai đoạn phân tích yêu cầu và quyết định kiến trúc hoàn tất. Bạn có thể tiến hành giai đoạn tiếp theo là **Setup Project & Coding** dựa trên đặc tả kỹ thuật này. Không tự ý thay đổi kiến trúc đã chốt.