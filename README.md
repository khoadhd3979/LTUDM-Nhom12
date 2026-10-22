# Đồ án: SecureChat-Java - Ứng dụng Nhắn tin Thời gian thực

> Hệ thống ứng dụng chat nhắn tin thời gian thực qua mạng, hỗ trợ đa luồng và mã hóa dữ liệu.
>
> **Mô hình:** Client - Server qua Socket TCP/UDP
> **Ngôn ngữ:** Java (Giao diện Swing)
> **Quy mô nhóm:** 2 thành viên

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

Dự án sử dụng kiến trúc **Client-Server** giao tiếp qua mạng (LAN hoặc Internet).

* **Ngôn ngữ & Giao diện:** Java, giao diện đồ họa (GUI) trực quan sử dụng Java Swing.
* **Xử lý mạng:** Socket TCP hoặc UDP.
* **Đa luồng (Multithreading):** Server bắt buộc xử lý đa luồng để phục vụ nhiều client cùng lúc.
* **Database:** SQL (Sử dụng kiến trúc DAO/BLL/DTO để quản lý truy vấn).
* **Xác thực:** Gửi mã OTP qua Email (thời hạn 10 phút) để kích hoạt tài khoản.

---

## 2. Luồng bảo mật & Mã hóa dữ liệu

Yêu cầu kỹ thuật bắt buộc của hệ thống là bảo mật kênh truyền và dữ liệu:
* **Bảo vệ mật khẩu:** Mật khẩu người dùng được lưu ở dạng hash (băm), tuyệt đối không lưu rỗng.
* **Mã hóa lai (Hybrid Encryption):**
    * Sử dụng mã hóa bất đối xứng (vd: RSA) để trao đổi khóa phiên một cách an toàn.
    * Sử dụng mã hóa đối xứng (vd: AES) để mã hóa toàn bộ nội nội dung tin nhắn và file đính kèm truyền qua lại giữa Client-Server.
    * *Lưu ý:* Mã hóa áp dụng ở tầng kênh truyền, Server vẫn xử lý và lưu trữ dữ liệu bình thường sau khi giải mã.
* **Kiểm tra đầu vào:** Toàn bộ dữ liệu đầu vào trên GUI phải được validate (kiểm tra) để không phát sinh lỗi khi chạy.

---

## 3. Thiết kế Cơ sở dữ liệu (CSDL)

Hệ thống lưu trữ trên Server tối thiểu gồm các thực thể:
* **Tài khoản người dùng:** Username (là địa chỉ email), password (hash), họ tên, giới tính, ngày sinh.
* **Nhóm & Thành viên:** Quản lý danh sách các nhóm chat và mapping thành viên trong nhóm.
* **Tin nhắn:** Lưu toàn bộ lịch sử tin nhắn 1-1, tin nhắn nhóm, file đính kèm, sticker.
* **Danh bạ & Trạng thái:** Danh sách bạn bè, danh sách bị chặn (block list).
* **Nhật ký (Log):** Log hoạt động của hệ thống (đăng nhập/xuất, tạo nhóm, đăng ký).

---

## 4. Tính năng cốt lõi (Client & Server)

### Chức năng phía Client (GUI)
* **Quản lý tài khoản:** Đăng ký, đăng nhập, cập nhật thông tin.
* **Nhắn tin thời gian thực:** Chat 1-1 và chat nhóm (tạo nhóm, thêm/mời/xóa thành viên bởi admin).
* **File đính kèm:** Gửi file (<1MB) và sticker qua kênh truyền đã mã hóa.
* **Trạng thái tin nhắn:** Hiển thị trạng thái đã gửi / đã nhận / đã xem.
* **Danh sách bạn bè:** Lưu người đã trò chuyện, tự động thông báo online/offline khi trạng thái thay đổi.
* **Lịch sử:** Tải tin nhắn gần nhất khi đăng nhập, hỗ trợ phân trang để tải tin cũ.
* **Chặn (Block):** Chặn nhận tin nhắn từ người/nhóm cụ thể.

### Chức năng phía Server
* **Thống kê:** Thống kê tổng số người dùng và số lượng đang online.
* **Quản trị:** Khóa (block) người dùng bằng lệnh hoặc giao diện.
* **Broadcast:** Gửi thông báo hệ thống đến toàn bộ người dùng.

---

## 5. Phân công công việc & Git Workflow

Dự án được chia cho **2 thành viên** nhằm tối ưu việc quản lý database và giao diện.

### Thành viên 1: Khoa (Backend Server, Security & Database)
* **Nhiệm vụ:**
    * Thiết kế CSDL SQL và xây dựng các lớp DTO/DAO/BLL.
    * Khởi tạo kiến trúc Server đa luồng (Multithreading) quản lý các kết nối Socket.
    * Cài đặt thuật toán Hash mật khẩu và mã hóa lai RSA/AES trao đổi khóa.
    * Tích hợp API gửi Email chứa mã OTP (thời hạn 10 phút).
    * Xây dựng chức năng thống kê, ghi log và quản lý block trên Server.
* **Branch làm việc:** `feature/khoa-server-db`

### Thành viên 2: Sự (Client UI, Logic xử lý & Packet payload)
* **Nhiệm vụ:**
    * Thiết kế toàn bộ Client GUI bằng Java Swing.
    * Xử lý logic luồng màn hình: Đăng ký/Đăng nhập -> Dashboard -> Khung chat 1-1/Nhóm.
    * Xử lý chia nhỏ gói dữ liệu để gửi file đính kèm dưới 1MB qua Socket.
    * Render trạng thái tin nhắn (đã gửi/nhận/xem) và xử lý UI phân trang lịch sử chat.
    * Lắng nghe Broadcast từ Server để cập nhật trạng thái online/offline của danh sách bạn bè.
* **Branch làm việc:** `feature/su-client-gui`

---

## 6. Hướng dẫn cài đặt & Setup môi trường

### Công cụ yêu cầu
* **Java JDK:** Phiên bản 17+
* **Database:** MySQL hoặc SQL Server.
* **IDE:** IntelliJ IDEA hoặc Eclipse.
* **Thư viện bổ sung:** `java.mail` (Gửi OTP Email), JDBC Driver (Kết nối SQL).

### Thiết lập môi trường
1. **Clone project:** `git clone <repo_url>`
2. **Chạy Database:** Import file `database/schema.sql` vào hệ quản trị CSDL của bạn. Cấu hình lại connection string trong thư mục DAO.
3. **Khởi động Server:** Chạy class `ServerMain.java`. Đảm bảo port đang được mở.
4. **Khởi động Client:** Chạy class `ClientMain.java`. Nhập IP của Server (dùng `localhost` hoặc `127.0.0.1` nếu test cục bộ) để kết nối.

---

## 7. Lộ trình phát triển

* **Tuần 1:** Setup cấu trúc thư mục, thiết kế UI Swing cơ bản, tạo CSDL, test kết nối Socket đơn luồng.
* **Tuần 2:** Triển khai DAO/BLL và hệ thống mã hóa RSA/AES. Gắn API Email OTP và hoàn thiện luồng đăng nhập.
* **Tuần 3:** Nâng cấp Server lên Multithreading. Triển khai nhắn tin 1-1 và nhắn tin nhóm thời gian thực, truyền file đính kèm.
* **Tuần 4:** Hoàn thiện trạng thái tin nhắn, phân trang, ghép tính năng Broadcast, fix bug và làm báo cáo. 

---

## 8. Tính năng nâng cao (Điểm cộng)

Nếu dư thời gian, nhóm sẽ tích hợp các tính năng tính điểm cộng:
* Báo hiệu "đang nhập..." (typing indicator).
* Thu hồi/xóa tin nhắn hai phía.
* Tìm kiếm trong lịch sử tin nhắn, ghim tin nhắn, thả cảm xúc (reaction).
* Thông báo đẩy khi có tin mới lúc cửa sổ chat không được focus.
* Triển khai Server thực tế ra VPS / Internet.

```
