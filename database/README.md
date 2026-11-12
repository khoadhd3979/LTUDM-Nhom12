# SecureChatDB — Database Documentation (M2-B)

## Tổng quan

- **DBMS:** SQL Server (>= 2019).
- **Database name:** `SecureChatDB`.
- **Schema file:** `database/schema_mssql.sql` — tạo 9 bảng + indexes.
- **Reset script:** `database/00_drop_database_reset.sql` — thao tác **thủ công**, không tự động chạy.
- **KHÔNG** bảo toàn dữ liệu từ database trước M2-B. Sau khi chạy schema mới trên DB rỗng, mọi tài khoản BCrypt cũ **không đăng nhập được** — phải đăng ký lại.

## 9 bảng

| # | Bảng | Vai trò | PK | FK tiêu biểu |
|---|---|---|---|---|
| 1 | `users` | Tài khoản + hash password + trạng thái + presence | `id` | — |
| 2 | `email_otp` | OTP xác thực email | `id` | `user_id → users.id` CASCADE |
| 3 | `groups` | Nhóm chat | `group_id` | `created_by → users.id` |
| 4 | `group_members` | Thành viên + role | `(group_id, user_id)` | `group_id → groups` CASCADE, `user_id → users` |
| 5 | `attachments` | Metadata file đính kèm | `attachment_id` | `uploader_id → users.id` |
| 6 | `direct_messages` | Tin 1-1 | `message_id` | sender/receiver → users; attachment → attachments |
| 7 | `group_messages` | Tin nhóm | `message_id` | sender → users; group → groups CASCADE; attachment → attachments |
| 8 | `group_message_receipts` | Trạng thái per-member cho tin nhóm | `(message_id, user_id)` | message → group_messages CASCADE; user → users (KHÔNG cascade từ group_members) |
| 9 | `activity_logs` | Audit log | `log_id` | actor → users (nullable) |
### Users — cột bổ sung M3-B

| Cột | Kiểu | Default | Ghi chú |
|---|---|---|---|
| `is_system_admin` | `BIT NOT NULL` | `0` | Đánh dấu system administrator. Cấp qua `BootstrapAdmin` tool (thủ công 1 lần). Không cấp qua API. |

## Cột nhạy cảm

- **`users.password_hash`** — `NVARCHAR(255)`. Format: `pbkdf2_sha256$600000$<salt_b64>$<dk_b64>` (90 chars).
- **`email_otp.otp_hash`** — `NVARCHAR(100)`. Format: `pbkdf2_sha256$100000$<salt_b64>$<dk_b64>` (90 chars).

## Constraints và Indexes

- **UNIQUE:** `users.email`, `attachments.stored_filename`.
- **CHECK:** `CK_users_gender/status`, `CK_dm_type/status/self/content_len/file_consistency/read_at_consistency`, `CK_gmsg_type/content_len/file_consistency`, `CK_attachment_size/hash_len`, `CK_receipt_read_after_delivered`, `CK_gm_role`, `CK_groups_name_len`.
- **Indexes:** 5 cũ (OTP, DM sender/receiver, GM group, group_members user) + 7 mới (`IX_users_last_seen`, `IX_attachments_uploader`, `IX_attachments_hash`, `IX_receipts_user_unread` filtered, 3 index cho `activity_logs`).

## Khởi tạo DB từ đầu

### Bước 1 — Cấu hình env

```powershell
$env:SC_DB_URL      = "jdbc:sqlserver://localhost:1433;databaseName=SecureChatDB;encrypt=true;trustServerCertificate=true"
$env:SC_DB_USER     = "sa"
$env:SC_DB_PASSWORD = "<your-password>"

