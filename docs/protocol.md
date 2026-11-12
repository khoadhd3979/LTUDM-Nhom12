# SecureChat Protocol v0.4 — Secure Transport & E2EE

> **v0.4 (M1 — Protocol Contract Repair):** Bổ sung §6 "Application Commands" — tài liệu hóa toàn bộ lệnh tầng ứng dụng theo source hiện hành, gồm lệnh mới `SEARCH_USER` và trường `msgType` cho lệnh chat. §1–§5 không thay đổi nội dung (chỉ đóng code fence bị thiếu ở cuối §5).

## 1. Transport & Framing
- **Protocol:** TCP Socket qua cổng `SC_PORT` (mặc định `8080`).
- **Data Format:** Newline-Delimited JSON (NDJSON), mã hóa UTF-8. Mỗi gói tin nằm trọn trên 1 dòng, kết thúc bằng `\n`.
- **Frame Limit:** Tối đa `1,000,000` bytes (1MB) không tính newline. Vượt quá $\rightarrow$ gửi `ERROR FRAME_TOO_LARGE` và đóng kết nối.

## 2. Attachment Chunking Rule (Quy tắc đính kèm File)
Do dữ liệu mã hóa bọc Base64 2 lần làm tăng dung lượng khoảng 1.78 lần, dung lượng file đính kèm thô (Raw File) mỗi packet không được vượt quá **256 KiB (262,144 bytes)**. File lớn hơn phải được chia nhỏ thành nhiều Chunk 256 KiB trước khi gửi.

## 3. Envelope Structure
- **Request Plaintext:** `{ "type": "...", "requestId": "...", "payload": {...} }`
- **Response OK:** `{ "type": "OK", "requestId": "...", "status": "OK", "payload": {...} }`
- **Response Error:** `{ "type": "ERROR", "requestId": "...", "status": "ERROR", "error": "CODE" }`

## 4. Handshake Sequence (State Machine)
1. `Client` $\rightarrow$ `Server`: `CLIENT_HELLO { version: "v1", clientNonce: b64(32B) }`
2. `Server` $\rightarrow$ `Client`: `SERVER_HELLO { version: "v1", keyId: "...", serverNonce: b64(32B), ephemeralPublicKey: b64(SPKI_DER), signature: b64(PSS_SIG) }`
3. `Client` Verify Signature $\rightarrow$ Gửi `KEY_EXCHANGE { encryptedSecret: b64(OAEP_CIPHERTEXT) }`
4. `Server` $\rightarrow$ `Client` (Encrypted, seq=1): `SECURE_MESSAGE` mang inner payload `HANDSHAKE_CONFIRM { confirm: "SERVER_CONFIRMED" }`
5. `Client` $\rightarrow$ `Server` (Encrypted, seq=1): `SECURE_MESSAGE` mang inner payload `HANDSHAKE_CONFIRM { confirm: "CLIENT_CONFIRMED" }`
6. Trang thái chuyển sang `ESTABLISHED`. Toàn bộ tin nhắn ứng dụng sau đó BẮT BUỘC gửi dưới dạng `SECURE_MESSAGE`.

### 4.1 Canonical TBS (To-Be-Signed) Specification
Chuỗi byte dùng để Server ký RSASSA-PSS và Client verify chữ ký (mỗi trường bọc uint32 Big-Endian độ dài):
`uint32BE(len) || bytes("SecureChat-Handshake-v1") || uint32BE(len) || bytes(keyId) || uint32BE(32) || clientNonce || uint32BE(32) || serverNonce || uint32BE(len) || ephemeralPublicKeyDER`

### 4.2 Key Derivation (HKDF-SHA256)
- `salt` = `clientNonce` || `serverNonce` (64 bytes)
- `info` = UTF-8 bytes `"SecureChat-Handshake-v1-HKDF"`
- `OKM` = `HKDF-SHA256(secret32B, salt, info, 64)`
- `c2sKey` = `OKM[0..31]` (AES-256 Key: Client $\rightarrow$ Server)
- `s2cKey` = `OKM[32..63]` (AES-256 Key: Server $\rightarrow$ Client)

## 5. Encrypted Envelope (`SECURE_MESSAGE`)
```json
{
  "type": "SECURE_MESSAGE",
  "requestId": "req-001",
  "status": "OK",
  "payload": {
    "sequence": 1,
    "nonce": "Base64_12_Bytes",
    "ciphertext": "Base64_Ciphertext_with_Tag"
  }
}
```

## 6. Application Commands

Toàn bộ lệnh trong mục này được gửi **bên trong `SECURE_MESSAGE`** sau khi kết nối đạt trạng thái `ESTABLISHED` (ngoại lệ: `PING` và các lệnh handshake ở §4). Cấu trúc packet bên trong tuân theo envelope ở §3.

### 6.1. Quy ước chung

- **Response thành công** của mọi lệnh dispatch đều có dạng `{ "type": "OK", "requestId": "...", "status": "OK", "payload": {...} }` (hằng `MessageType.OK`). Ngoại lệ duy nhất: `PING` trả `type: "PONG"`.
- **Response lỗi** có dạng `{ "type": "ERROR", "requestId": "...", "status": "ERROR", "error": "<CODE>" }`. Mã lỗi lấy từ `BusinessException.getCode()` của tầng service; lỗi SQL được ánh xạ thành `DB_ERROR`, lỗi runtime không dự kiến thành `INTERNAL_ERROR`.
- **Xác thực:** Trừ `REGISTER`, `VERIFY_OTP`, `LOGIN`, mọi lệnh đều yêu cầu session đã `LOGIN` thành công; chưa login → `UNAUTHENTICATED`. `senderId` luôn lấy từ session — Client **không** gửi và Server **không** đọc trường này từ payload.
- Server **bỏ qua** các trường payload không được liệt kê trong tài liệu của từng lệnh.
- `type` không thuộc danh sách lệnh → `UNKNOWN_TYPE`. Packet thiếu `type` → `MISSING_TYPE`.

### 6.2. Lệnh xác thực (Phase 2)

#### PING
- **Request:** `{}` — **Response OK:** `type: "PONG"`, payload `{ "ts": <epoch millis> }`.

#### REGISTER
- **Request:** `{ email, password, fullName, gender?, dateOfBirth? }` — `gender` thuộc `{MALE, FEMALE, OTHER}` (vắng → null); `dateOfBirth` dạng `yyyy-MM-dd` (vắng → null).
- **Response OK:** user payload (xem 6.2.1) với `status: "PENDING"`.
- **Error:** `ALREADY_LOGGED_IN`, `EMAIL_INVALID`, `PASSWORD_REQUIRED`, `PASSWORD_TOO_SHORT`, `PASSWORD_TOO_LONG` (> 72 byte UTF-8), `FULLNAME_REQUIRED`, `INVALID_GENDER`, `INVALID_DATE_OF_BIRTH`, `EMAIL_EXISTS`, `EMAIL_SEND_FAILED`, `DB_ERROR`.
- Mật khẩu hợp lệ: tối thiểu 8 ký tự. Sau khi đăng ký thành công, Server gửi OTP qua email (hạn 10 phút, tối đa 5 lần thử).

#### VERIFY_OTP
- **Request:** `{ email, otp }` — `otp` đúng 6 chữ số.
- **Response OK:** user payload với `status: "ACTIVE"`.
- **Error:** `ALREADY_LOGGED_IN`, `EMAIL_INVALID`, `OTP_INVALID_FORMAT`, `OTP_NOT_FOUND`, `OTP_INVALID`, `OTP_EXPIRED`, `OTP_ATTEMPTS_EXCEEDED`, `OTP_ALREADY_USED`, `ACCOUNT_NOT_PENDING`, `USER_NOT_FOUND`, `DB_ERROR`.

#### LOGIN
- **Request:** `{ email, password }`.
- **Response OK:** user payload với `status: "ACTIVE"`. Một tài khoản chỉ đăng nhập tại một kết nối tại một thời điểm.
- **Error:** `ALREADY_LOGGED_IN`, `EMAIL_INVALID`, `AUTH_FAILED` (sai email hoặc mật khẩu — không phân biệt), `ACCOUNT_PENDING`, `ACCOUNT_LOCKED`, `DB_ERROR`.

#### LOGOUT
- **Request:** `{}` — **Response OK:** payload rỗng `{}`.
- **Error:** `NOT_LOGGED_IN`.

#### 6.2.1. User payload
Dùng cho response của `REGISTER`, `VERIFY_OTP`, `LOGIN`:
```json
{ "userId": 42, "email": "user@example.com", "fullName": "Nguyen Van A",
  "status": "ACTIVE", "gender": "MALE", "dateOfBirth": "2004-05-01" }
```
`gender` và `dateOfBirth` chỉ xuất hiện khi có giá trị. Payload này **không** chứa `passwordHash` hay bất kỳ trường credential nào.

### 6.3. SEARCH_USER (M1)

Tra cứu user `ACTIVE` bằng email chính xác. Dùng để Client resolve `email → userId` trước khi gửi `SEND_DIRECT_MESSAGE` hoặc `ADD_GROUP_MEMBER`.

- **Authorization:** Bắt buộc đã đăng nhập. Chưa login → `UNAUTHENTICATED`.
- **Request:**
```json
{ "type": "SEARCH_USER", "requestId": "s-1",
  "payload": { "email": "user@example.com" } }
```
Server trim + lowercase email trước khi truy vấn.
- **Response OK** (chỉ khi user tồn tại **và** `status = 'ACTIVE'`):
```json
{ "type": "OK", "requestId": "s-1", "status": "OK",
  "payload": { "userId": 42, "email": "user@example.com", "fullName": "Nguyen Van A" } }
```
- **Privacy — bắt buộc:** Response chỉ chứa đúng 3 trường `userId`, `email`, `fullName`. **Không** trả `password_hash`, `status`, `date_of_birth`, `gender`, `created_at`, `updated_at`, OTP. User tồn tại nhưng `PENDING` hoặc `LOCKED` → trả `USER_NOT_FOUND` (không phân biệt "không tồn tại" với "tồn tại nhưng không ACTIVE" — tránh lộ trạng thái tài khoản).
- **Error:** `UNAUTHENTICATED`, `EMAIL_INVALID`, `USER_NOT_FOUND`, `DB_ERROR`.
- **Rate-limit:** Chưa triển khai ở M1 (ghi nhận P2).

### 6.4. msgType (M1)

Áp dụng cho `SEND_DIRECT_MESSAGE` và `SEND_GROUP_MESSAGE`:

| Giá trị | Ý nghĩa | Trạng thái M1 |
|---|---|---|
| `TEXT` | Tin nhắn văn bản thường; `content` là chuỗi hiển thị trực tiếp | ✅ Hỗ trợ |
| `STICKER` | Sticker; `content` là **mã sticker** (opaque string do Client quy ước để map sang asset hiển thị). Server lưu nguyên văn, không parse | ✅ Hỗ trợ |
| `FILE` | Tin nhắn đính kèm file qua lệnh chat | ❌ Chưa hỗ trợ — trả `INVALID_MESSAGE_TYPE` |

Quy tắc:
- Client vắng `msgType` hoặc gửi rỗng → Server mặc định `TEXT`.
- Giá trị được chuẩn hóa không phân biệt hoa/thường (`"sticker"` → `"STICKER"`); response luôn trả dạng uppercase trong trường `messageType`.
- Giá trị ngoài `{TEXT, STICKER}` → `INVALID_MESSAGE_TYPE`, tin nhắn **không** được lưu.
- File transfer **không** đi qua `msgType = FILE` — xem §6.9.
- Server coi `content` là opaque string — không parse markdown/HTML.

### 6.5. Lệnh chat (Phase 4)

#### SEND_DIRECT_MESSAGE
- **Request:**
```json
{ "type": "SEND_DIRECT_MESSAGE", "requestId": "r-1",
  "payload": { "receiverId": 42, "content": "Hello", "msgType": "TEXT" } }
```

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `receiverId` | long | ✅ | Resolve từ `SEARCH_USER` nếu Client chỉ có email. Thiếu/rỗng/≤ 0 → `RECEIVER_NOT_FOUND` |
| `content` | string | ✅ | Sau trim phải có 1..4000 ký tự |
| `msgType` | string | ❌ | Theo §6.4 |

- **Response OK:**
```json
{ "type": "OK", "requestId": "r-1", "status": "OK",
  "payload": { "messageId": 1001, "senderId": 7, "receiverId": 42,
               "content": "Hello", "messageType": "TEXT",
               "sentAt": "2026-11-11T10:15:30.123", "status": "SENT" } }
```
- `status` (`SENT` | `DELIVERED`): `SENT` = đã lưu DB; `DELIVERED` = receiver đang online và push đã gửi thành công (Server cập nhật sau khi push). Server **không** set `READ` ở M1.
- **Error:** `UNAUTHENTICATED`, `RECEIVER_SELF`, `RECEIVER_NOT_FOUND`, `CONTENT_INVALID`, `INVALID_MESSAGE_TYPE`, `DB_ERROR`.

#### SEND_GROUP_MESSAGE
- **Request:**
```json
{ "type": "SEND_GROUP_MESSAGE", "requestId": "r-2",
  "payload": { "groupId": 5, "content": "Hi team", "msgType": "TEXT" } }
```

| Trường | Kiểu | Bắt buộc | Ghi chú |
|---|---|---|---|
| `groupId` | long | ✅ | Resolve từ `FETCH_MY_GROUPS` nếu Client chỉ có tên nhóm. Thiếu/rỗng/≤ 0 → `GROUP_NOT_FOUND` |
| `content` | string | ✅ | Sau trim phải có 1..4000 ký tự |
| `msgType` | string | ❌ | Theo §6.4 |

- **Response OK:** như message payload ở 6.5.1 nhưng **không có** trường `status` (bảng `group_messages` không có cột status).
- **Error:** `UNAUTHENTICATED`, `GROUP_NOT_FOUND`, `NOT_GROUP_MEMBER`, `CONTENT_INVALID`, `INVALID_MESSAGE_TYPE`, `DB_ERROR`.

#### 6.5.1. Message payload
Dùng cho response của `SEND_*`, phần tử của `messages[]` trong `FETCH_*_HISTORY`, và push (§6.7):
```json
{ "messageId": 1001, "senderId": 7, "receiverId": 42,
  "content": "Hello", "messageType": "TEXT",
  "sentAt": "2026-11-11T10:15:30.123", "status": "SENT" }
```
`receiverId` chỉ có với tin nhắn 1-1, `groupId` chỉ có với tin nhắn nhóm. `status` chỉ có khi ngữ cảnh bao gồm trạng thái (response của `SEND_DIRECT_MESSAGE` và `FETCH_DIRECT_HISTORY`); push và tin nhắn nhóm không có `status`. `sentAt` là ISO-8601 local datetime.

#### FETCH_DIRECT_HISTORY
- **Request:** `{ otherUserId: long, beforeMessageId?: long, limit?: int }`. `otherUserId` thiếu/≤ 0 → `RECEIVER_NOT_FOUND`.
- **Response OK:** `{ messages: [ <message payload>, ... ], hasMore: boolean, nextCursor: long | null }` — `messages` sắp xếp giảm dần theo `messageId` (mới nhất trước); `nextCursor` là `messageId` nhỏ nhất của trang hiện tại, dùng làm `beforeMessageId` cho trang tiếp theo (`null` khi trang rỗng).
- **Phân trang:** `limit` mặc định 30, được chuẩn hóa về khoảng [1, 100] (≤ 0 → 30, > 100 → 100).
- **Error:** `UNAUTHENTICATED`, `RECEIVER_NOT_FOUND`, `DB_ERROR`.

#### FETCH_GROUP_HISTORY
- **Request:** `{ groupId: long, beforeMessageId?: long, limit?: int }`. `groupId` thiếu/≤ 0 → `GROUP_NOT_FOUND`.
- **Response OK:** giống `FETCH_DIRECT_HISTORY`; phần tử `messages[]` không có `status`.
- **Error:** `UNAUTHENTICATED`, `GROUP_NOT_FOUND`, `NOT_GROUP_MEMBER`, `DB_ERROR`.

### 6.6. Lệnh nhóm (Phase 4)

#### CREATE_GROUP
- **Request:** `{ groupName: string }` — sau trim phải có 1..100 ký tự.
- **Response OK:** `{ groupId, groupName, role: "ADMIN", createdBy }` — người tạo tự động là ADMIN của nhóm (tạo nhóm và thêm admin là một transaction).
- **Error:** `UNAUTHENTICATED`, `GROUP_NAME_INVALID`, `DB_ERROR`.

#### ADD_GROUP_MEMBER
- **Request:** `{ groupId: long, targetUserId: long }` — `targetUserId` resolve từ `SEARCH_USER` nếu Client chỉ có email. Thiếu/≤ 0 ở một trong hai trường → `INVALID_MESSAGE`.
- **Response OK:** `{ groupId, userId, role: "MEMBER" }`.
- Chỉ ADMIN của nhóm mới thêm được thành viên.
- **Error:** `UNAUTHENTICATED`, `INVALID_MESSAGE`, `GROUP_NOT_FOUND`, `NOT_GROUP_ADMIN`, `TARGET_USER_NOT_FOUND`, `ALREADY_GROUP_MEMBER`, `DB_ERROR`.

#### FETCH_MY_GROUPS
- **Request:** `{}`.
- **Response OK:** `{ groups: [ { groupId, groupName, createdBy, createdAt?, role? }, ... ] }` — `createdAt` (ISO-8601) và `role` chỉ xuất hiện khi có giá trị.
- **Error:** `UNAUTHENTICATED`, `DB_ERROR`.

### 6.7. Push messages (Server → Client)

Push được gửi bên trong `SECURE_MESSAGE`, `requestId` có dạng `"push-<messageId>"`, `status: "OK"`. Payload là message payload (§6.5.1) **không có** trường `status`.

- **`PUSH_DIRECT_MESSAGE`:** gửi cho receiver khi có tin nhắn 1-1 mới và receiver đang online.
- **`PUSH_GROUP_MESSAGE`:** gửi cho mọi thành viên nhóm đang online, trừ người gửi.

Client phải xử lý push theo `messageType` (đặc biệt `STICKER` — hiển thị asset tương ứng với mã trong `content`).

### 6.8. Bảng mã lỗi tổng hợp

| Code | Ngữ cảnh |
|---|---|
| `UNAUTHENTICATED` | Chưa đăng nhập mà gọi lệnh cần xác thực |
| `ALREADY_LOGGED_IN` | Đã đăng nhập mà gọi REGISTER/VERIFY_OTP/LOGIN, hoặc tài khoản đang online ở kết nối khác |
| `NOT_LOGGED_IN` | LOGOUT khi chưa đăng nhập |
| `UNKNOWN_TYPE` | `type` không thuộc danh sách lệnh |
| `MISSING_TYPE` | Packet thiếu trường `type` |
| `INVALID_MESSAGE` | Payload thiếu trường bắt buộc (ADD_GROUP_MEMBER) |
| `DB_ERROR` | Lỗi SQL Server / tầng DAO |
| `INTERNAL_ERROR` | Lỗi runtime không dự kiến |
| `FRAME_TOO_LARGE` | Frame vượt 1,000,000 byte (kèm đóng kết nối) |
| `EMAIL_INVALID` | Email rỗng hoặc sai định dạng |
| `USER_NOT_FOUND` | SEARCH_USER không tìm thấy user ACTIVE khớp email |
| `INVALID_MESSAGE_TYPE` | `msgType` ngoài `{TEXT, STICKER}` (gồm `FILE` ở M1) |
| `RECEIVER_NOT_FOUND` / `RECEIVER_SELF` | Người nhận không tồn tại / gửi cho chính mình |
| `CONTENT_INVALID` | Nội dung trống hoặc quá 4000 ký tự sau trim |
| `GROUP_NOT_FOUND` / `NOT_GROUP_MEMBER` / `NOT_GROUP_ADMIN` | Nhóm không tồn tại / không phải thành viên / không phải ADMIN |
| `GROUP_NAME_INVALID` | Tên nhóm trống hoặc quá 100 ký tự sau trim |
| `TARGET_USER_NOT_FOUND` / `ALREADY_GROUP_MEMBER` | User đích không tồn tại / đã là thành viên nhóm |
| `AUTH_FAILED`, `ACCOUNT_PENDING`, `ACCOUNT_LOCKED`, `ACCOUNT_NOT_PENDING` | Đăng nhập / xác thực OTP |
| `EMAIL_EXISTS`, `EMAIL_SEND_FAILED` | Đăng ký |
| `PASSWORD_REQUIRED`, `PASSWORD_TOO_SHORT`, `PASSWORD_TOO_LONG`, `FULLNAME_REQUIRED`, `INVALID_GENDER`, `INVALID_DATE_OF_BIRTH` | Validate đầu vào đăng ký |
| `OTP_INVALID_FORMAT`, `OTP_NOT_FOUND`, `OTP_INVALID`, `OTP_EXPIRED`, `OTP_ATTEMPTS_EXCEEDED`, `OTP_ALREADY_USED` | Xác thực OTP |

### 6.9. File transfer (planned — M4)

**Chưa triển khai phía Server ở M1.** Ghi nhận để tránh nhầm lẫn:

- File transfer **không** dùng `msgType = FILE` trên các lệnh chat (§6.4).
- Client hiện đã có code gửi packet `FILE_CHUNK` (chunk Base64, kích thước chunk mặc định 8 KiB theo `FileChunkUtils`, nằm trong trần 256 KiB/packet ở §2) — Server **chưa có handler** cho `FILE_CHUNK` ở M1 nên packet này hiện nhận `UNKNOWN_TYPE`.
- Contract chi tiết sẽ chốt ở M4 với **baseline là protocol `FILE_CHUNK` Client hiện có**; không thiết kế bộ packet thay thế.
