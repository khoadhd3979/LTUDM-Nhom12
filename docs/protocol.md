# SecureChat Protocol v0.6 — Secure Transport & Application Commands

> **v0.6 (M4-B — File transfer relay):** Triển khai FILE_CHUNK relay phía Server (M4-B): stateless per-chunk, bổ sung senderId/senderEmail ở payload chuyển tiếp, không buffer/DB/migration. §6.9 viết lại thành contract FILE_CHUNK đã triển khai. §6.11 loại FILE_CHUNK khỏi deferral register. §1–§6.8 và §6.10 giữ nguyên nội dung so với v0.5.
>
> **v0.5 (M3 — Phase 4 Documentation):** Bổ sung tài liệu cho 9 command M3 đã triển khai và nghiệm thu trên server tại commit `ddabb69` (M3-B CLOSED — PASS): `UPDATE_PROFILE`, `REMOVE_GROUP_MEMBER`, `SET_GROUP_ROLE`, `MARK_READ`, `MESSAGE_STATUS`, `LOCK_USER`, `UNLOCK_USER`, `SYSTEM_BROADCAST`, `USER_STATS`. Mở rộng §6.7 (Push) với 3 push M3: `MESSAGE_STATUS {status:"SEEN"}`, `SYSTEM_BROADCAST {message}`, `ERROR {ACCOUNT_LOCKED}`. Mở rộng §6.8 (bảng lỗi). Thêm §6.10 (lệnh M3) và §6.11 (command không được hỗ trợ). §1–§6.6 và §6.9 giữ nguyên nội dung so với v0.4.
>
> **Nguồn sự thật:** Nội dung §6.7–§6.11 được trích từ source tại `c36d328` (= M3-B CLOSED + Phase 4 docs merged). Khi M3-A Design (v1 + Errata + Delta 2) khác source, tài liệu này mô tả theo source.

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
- **Response Error có payload (M3):** khi lỗi mang dữ liệu cấu trúc (chỉ xảy ra với `CANNOT_LOCK_LAST_ADMIN_OF_GROUP` ở M3), packet lỗi có thêm `payload: {"groupIds":[<long>,...]}`. Với các lỗi khác, `payload` là `{}`.

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
- **Response lỗi** có dạng `{ "type": "ERROR", "requestId": "...", "status": "ERROR", "error": "<CODE>" }`. Mã lỗi lấy từ `BusinessException.getCode()` của tầng service; lỗi SQL được ánh xạ thành `DB_ERROR`, lỗi runtime không dự kiến thành `INTERNAL_ERROR`. **M3:** riêng `CANNOT_LOCK_LAST_ADMIN_OF_GROUP` mang `payload: {"groupIds":[<long>,...]}` (xem §6.10.6).
- **Xác thực:** Trừ `REGISTER`, `VERIFY_OTP`, `LOGIN`, mọi lệnh đều yêu cầu session đã `LOGIN` thành công; chưa login → `UNAUTHENTICATED`. `senderId` luôn lấy từ session — Client **không** gửi và Server **không** đọc trường này từ payload.
- Server **bỏ qua** các trường payload không được liệt kê trong tài liệu của từng lệnh (trừ `UPDATE_PROFILE` — xem §6.10.1).
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
`gender` và `dateOfBirth` chỉ xuất hiện khi có giá trị. Payload này **không** chứa `passwordHash` hay bất kỳ trường credential nào, và **không** chứa `is_system_admin` — cờ system-admin không được expose qua API.

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

**Ghi chú M3:** `ADD_GROUP_MEMBER` giờ thực thi atomic (member + audit cùng transaction), precedence chuẩn: `GROUP_NOT_FOUND` → `NOT_GROUP_ADMIN` → `TARGET_USER_NOT_FOUND` → `ALREADY_GROUP_MEMBER`. Contract lỗi không đổi.

### 6.7. Push messages (Server → Client)

Push được gửi bên trong `SECURE_MESSAGE`, `requestId` có dạng `"push-<messageId>"`, `status: "OK"`. Payload là message payload (§6.5.1) **không có** trường `status`.

- **`PUSH_DIRECT_MESSAGE`:** gửi cho receiver khi có tin nhắn 1-1 mới và receiver đang online.
- **`PUSH_GROUP_MESSAGE`:** gửi cho mọi thành viên nhóm đang online, trừ người gửi.

Client phải xử lý push theo `messageType` (đặc biệt `STICKER` — hiển thị asset tương ứng với mã trong `content`).

#### MESSAGE_STATUS (push, M3)
**Áp dụng: chỉ direct.** Server gửi push này tới **peer/sender** (người gửi tin nhắn) khi người nhận đánh dấu đã xem thành công qua một trong hai đường:
- `MARK_READ` direct (§6.10.4) thành công (mỗi tin nhắn đơn lẻ), hoặc
- `MESSAGE_STATUS` direct (§6.10.5) thành công **và** có ≥ 1 tin được đánh dấu.

- **Type:** `MESSAGE_STATUS` (giống type Client dùng cho command — phân biệt qua hướng: C→S là command, S→C là push).
- **Payload:** `{ "status": "SEEN" }` (không có field nào khác).
- **Request ID:** `"push-read-<messageId>"` (sau MARK_READ) hoặc `"push-seen-<actorId>"` (sau MESSAGE_STATUS).
- **Group:** **không** có push MESSAGE_STATUS. `MARK_READ` group và `MESSAGE_STATUS` group không phát push.

#### SYSTEM_BROADCAST (push, M3)
Server gửi push này tới mọi session online **trừ người gửi broadcast** (handler của admin được so sánh identity `==` và bị loại).

- **Type:** `SYSTEM_BROADCAST` (cùng type với command C→S; phân biệt qua hướng).
- **Payload:** `{ "message": "<nội dung broadcast>" }`. Không có `PUSH_SYSTEM_BROADCAST`.
- **Request ID:** `"push-broadcast-<nanoTime>"`.
- **Partial failure:** một handler nhận lỗi không ảnh hưởng các handler khác.

#### ERROR {ACCOUNT_LOCKED} (push, M3)
Khi admin lock một tài khoản đang online, sau khi commit transaction lock thành công, server:

1. Gửi packet `ERROR` với `requestId: null`, `error: "ACCOUNT_LOCKED"` tới handler của target (best-effort — lỗi gửi không ảnh hưởng bước 2).
2. **Đóng socket** của target (vô điều kiện, kể cả khi bước 1 fail).

Đây **không phải type mới** — sử dụng type `ERROR` chuẩn. Client khi nhận ERROR {ACCOUNT_LOCKED} khi đang online phải đăng xuất bắt buộc và hiển thị lý do (xem Client Integration Guide).

### 6.8. Bảng mã lỗi tổng hợp (mở rộng M3)

| Code | Ngữ cảnh | M3 |
|---|---|---|
| `UNAUTHENTICATED` | Chưa đăng nhập mà gọi lệnh cần xác thực | ✓ |
| `ALREADY_LOGGED_IN` | Đã đăng nhập / tài khoản đang online nơi khác | baseline |
| `NOT_LOGGED_IN` | `LOGOUT` khi chưa đăng nhập | baseline |
| `UNKNOWN_TYPE` | `type` không thuộc danh sách lệnh | ✓ |
| `MISSING_TYPE` | Packet thiếu trường `type` | baseline |
| `INVALID_MESSAGE` | Payload thiếu trường bắt buộc / sai định dạng chung | ✓ (M3 dùng thêm cho `MESSAGE_STATUS`, `MARK_READ`, `LOCK_USER`, `UNLOCK_USER`, `SYSTEM_BROADCAST`) |
| `DB_ERROR` | Lỗi SQL Server / DAO | ✓ |
| `INTERNAL_ERROR` | Lỗi runtime không dự kiến | baseline |
| `FRAME_TOO_LARGE` | Frame vượt 1,000,000 byte (kèm đóng socket) | baseline |
| `EMAIL_INVALID`, `USER_NOT_FOUND` | SEARCH_USER / xác thực | baseline + ✓ (M3 `LOCK_USER`) |
| `INVALID_MESSAGE_TYPE` | `msgType` ngoài `{TEXT, STICKER}` | baseline |
| `RECEIVER_NOT_FOUND`, `RECEIVER_SELF` | Chat 1-1 | baseline |
| `CONTENT_INVALID` | Nội dung trống hoặc > 4000 ký tự | baseline |
| `GROUP_NOT_FOUND`, `NOT_GROUP_MEMBER`, `NOT_GROUP_ADMIN` | Nhóm | ✓ |
| `GROUP_NAME_INVALID` | Tên nhóm trống hoặc > 100 ký tự | baseline |
| `TARGET_USER_NOT_FOUND`, `ALREADY_GROUP_MEMBER` | Thêm thành viên | ✓ |
| **`TARGET_NOT_IN_GROUP`** | `REMOVE_GROUP_MEMBER` / `SET_GROUP_ROLE` — target không phải thành viên nhóm | ✓ M3 |
| **`LAST_ADMIN_REQUIRED`** | `REMOVE_GROUP_MEMBER` / `SET_GROUP_ROLE` — không thể xóa / hạ quyền ADMIN cuối | ✓ M3 |
| **`INVALID_ROLE`** | `SET_GROUP_ROLE` — `newRole` không thuộc `{ADMIN, MEMBER}` | ✓ M3 |
| **`INVALID_FIELD`** | `UPDATE_PROFILE` — payload chứa field ngoài `{fullName, gender, dateOfBirth}` | ✓ M3 |
| **`MESSAGE_NOT_FOUND`** | `MARK_READ` direct — `messageId` không tồn tại | ✓ M3 |
| **`NOT_MESSAGE_RECIPIENT`** | `MARK_READ` — actor không phải người nhận (direct) hoặc không có receipt (group) | ✓ M3 |
| **`AMBIGUOUS_GROUP_NAME`** | `MESSAGE_STATUS` group — resolve tên nhóm ra ≥ 2 kết quả | ✓ M3 |
| **`NOT_SYSTEM_ADMIN`** | `LOCK_USER` / `UNLOCK_USER` / `SYSTEM_BROADCAST` / `USER_STATS` — actor không phải system admin | ✓ M3 |
| **`USER_ALREADY_LOCKED`** | `LOCK_USER` — target đã ở trạng thái LOCKED | ✓ M3 |
| **`USER_NOT_LOCKED`** | `UNLOCK_USER` — target không ở trạng thái LOCKED | ✓ M3 |
| **`CANNOT_LOCK_SELF`** | `LOCK_USER` — actor = target | ✓ M3 |
| **`CANNOT_LOCK_LAST_ADMIN_OF_GROUP`** | `LOCK_USER` — target là ACTIVE ADMIN cuối của ≥ 1 group. **Payload chứa `{"groupIds":[<long>,...]}`** (sorted ASC). | ✓ M3 |
| **`ACCOUNT_LOCKED`** | Push (ERROR) tới target đang online khi bị lock. Không phải error code trả về cho requester của `LOCK_USER`. | ✓ M3 |
| `AUTH_FAILED`, `ACCOUNT_PENDING`, `ACCOUNT_LOCKED` (login), `ACCOUNT_NOT_PENDING` | Xác thực | baseline |
| `EMAIL_EXISTS`, `EMAIL_SEND_FAILED` | Đăng ký | baseline |
| `PASSWORD_REQUIRED`, `PASSWORD_TOO_SHORT`, `PASSWORD_TOO_LONG`, `FULLNAME_REQUIRED`, `INVALID_GENDER`, `INVALID_DATE_OF_BIRTH` | Validate đăng ký | baseline |
| `FULLNAME_INVALID` | `UPDATE_PROFILE` — họ tên không hợp lệ | ✓ M3 |
| `OTP_INVALID_FORMAT`, `OTP_NOT_FOUND`, `OTP_INVALID`, `OTP_EXPIRED`, `OTP_ATTEMPTS_EXCEEDED`, `OTP_ALREADY_USED` | OTP | baseline |

> **Ghi chú:** `INVALID_STATUS` không tồn tại trong source tại `ddabb69`. `MESSAGE_STATUS` với `status != "SEEN"` trả `INVALID_MESSAGE`.

### 6.9. FILE_CHUNK — File transfer relay (M4-B)

Server relay **stateless per-chunk**: mỗi packet `FILE_CHUNK` là một đơn vị độc lập, Server validate + resolve đích + đóng gói lại packet rồi gửi qua secure channel của **phiên người nhận** (`sendSecure` của handler đích). Không buffer, không lưu file, không DB, không migration, không trạng thái transfer, không ACK, không packet hoàn tất.

#### 6.9.1. Request (Client → Server)

Gửi bên trong `SECURE_MESSAGE`, yêu cầu đã đăng nhập (`UNAUTHENTICATED` nếu chưa).

```json
{ "type": "FILE_CHUNK", "requestId": "req-chunk-0",
  "payload": {
    "fileId": "f-2f3e...-uuid",
    "fileName": "bao-cao.pdf",
    "chunkIndex": 0,
    "totalChunks": 8,
    "data": "Base64(chunk_bytes)",
    "receiver": "peer@example.com"
  } }
```

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `fileId` | string | ✅ | Không rỗng, không blank. Không giới hạn định dạng (Client dùng UUID) |
| `fileName` | string | ✅ | Không rỗng, không blank |
| `chunkIndex` | int | ✅ | ∈ `[0, totalChunks)` |
| `totalChunks` | int | ✅ | ∈ `[1, 128]` (128 × 8 KiB = 1 MiB — trần file REQ-FUN-05) |
| `data` | string | ✅ | Base64 **hợp lệ** (chỉ ký tự alphabet + `=`). Sau khi decode: **1..262,144 byte** (trần §2) |
| `receiver` | string | ✅ **XOR** `groupName` | Email người nhận. Đúng một trong hai field phải có mặt và không blank; cả hai hoặc không có → `INVALID_MESSAGE` |
| `groupName` | string | ✅ **XOR** `receiver` | Tên nhóm |

Field khác trong payload (kể cả `senderId`/`senderEmail` nếu Client gửi) → **bỏ qua**, không đọc, không dùng. Danh tính sender luôn lấy từ session.

#### 6.9.2. Response (Server → Client gửi)

```json
{ "type": "OK", "requestId": "req-chunk-0", "status": "OK", "payload": {} }
```

Payload rỗng `{}`. Client **không** chờ ACK per-chunk theo kiểu tương tác — packet OK chỉ mang tính xác nhận dispatch đã xử lý; nếu Client gửi fire-and-forget, có thể bỏ qua response.

#### 6.9.3. Relay packet (Server → Client nhận)

Mỗi người nhận online nhận **một** packet `FILE_CHUNK` độc lập qua `sendSecure` của handler mình, bọc trong `SECURE_MESSAGE`.

**Direct:**
```json
{ "type": "FILE_CHUNK", "requestId": "push-file-f-2f3e...-0", "status": "OK",
  "payload": {
    "fileId": "f-2f3e...-uuid",
    "fileName": "bao-cao.pdf",
    "chunkIndex": 0,
    "totalChunks": 8,
    "data": "Base64(chunk_bytes)",
    "senderId": 42,
    "senderEmail": "alice@example.com"
  } }
```

**Group** (bổ sung `groupId` + `groupName` canonical từ DB):
```json
{ "type": "FILE_CHUNK", "requestId": "push-file-f-2f3e...-0", "status": "OK",
  "payload": {
    "fileId": "f-2f3e...-uuid",
    "fileName": "bao-cao.pdf",
    "chunkIndex": 0,
    "totalChunks": 8,
    "data": "Base64(chunk_bytes)",
    "senderId": 42,
    "senderEmail": "alice@example.com",
    "groupId": 5,
    "groupName": "Team Alpha"
  } }
```

- Năm field `fileId`, `fileName`, `chunkIndex`, `totalChunks`, `data` giữ nguyên **byte** từ request Client (đặc biệt `data` không decode/re-encode ở chiều relay).
- `senderId` là user id từ session; `senderEmail` từ bản ghi user của sender.
- `requestId` format: `"push-file-" + fileId + "-" + chunkIndex`.
- Nhánh direct **không** có `receiver` trong relay payload; nhánh group **có** `groupName` canonical (không phải tên Client gửi lên nếu khác hoa/thường).
- Relay payload không chứa field định danh sender khác.

#### 6.9.4. Hành vi

- **Direct, người nhận offline:** sender nhận `RECEIVER_NOT_FOUND`. Không có buffer, không có "gửi lại sau" — Client phải gửi lại toàn bộ file nếu muốn.
- **Direct, email không resolve user ACTIVE:** `USER_NOT_FOUND`.
- **Group, không thành viên nào online:** sender nhận `OK` với payload `{}`. Không lỗi.
- **Group, tên nhóm không tồn tại:** `GROUP_NOT_FOUND`. **Tên nhóm trùng ≥ 2 kết quả:** `AMBIGUOUS_GROUP_NAME` — Server không tự chọn nhóm; vì `FILE_CHUNK` không nhận `groupId`, lỗi này chỉ hết khi các nhóm của actor không còn trùng tên.
- **Partial fanout failure** (group): nếu `sendSecure` tới một thành viên fail (socket đóng giữa chừng), các thành viên còn lại vẫn nhận packet. Sender vẫn nhận `OK`.
- **Không ACK, không completion packet.** Client nhận không cần (và không có) packet "file hoàn tất"; phải tự ghép chunk theo `fileId` + `chunkIndex` (tối đa `totalChunks`). Nếu thiếu chunk (mất mạng, offline) → Client phải báo lỗi; Server không phát hiện hộ.
- **Không audit:** catalog activity log M3 không có sự kiện file; chunk-rate sẽ gây spam → không ghi `activity_logs`.
- **Không log data:** log debug (nếu có) chỉ chứa `senderId`, `fileId`, `chunkIndex`, đích — tuyệt đối không log `data`.
- **Session-identity:** `senderId`/`senderEmail` lấy từ `getCurrentUserId()` + DTO session; Client **không** được gửi và Server **không** đọc `senderId`/`senderEmail` từ payload.

#### 6.9.5. Mã lỗi

| Code | Điều kiện |
|---|---|
| `UNAUTHENTICATED` | Chưa đăng nhập |
| `INVALID_MESSAGE` | Bất kỳ validate payload thất bại (thiếu field, sai kiểu, `totalChunks`/`chunkIndex` ngoài range, Base64 không hợp lệ, `data` decode ra 0 hoặc > 262144 byte, thiếu/có cả hai trong `receiver`/`groupName`) |
| `USER_NOT_FOUND` | Direct: email không resolve user ACTIVE |
| `RECEIVER_NOT_FOUND` | Direct: người nhận offline |
| `GROUP_NOT_FOUND` | Group: tên nhóm không khớp nhóm nào của actor |
| `AMBIGUOUS_GROUP_NAME` | Group: tên nhóm khớp ≥ 2 nhóm của actor |
| `DB_ERROR` | Lỗi SQL / DAO khi resolve |

### 6.10. Lệnh M3 (C→S)

Tất cả lệnh dưới đây **yêu cầu đã LOGIN** (nếu không → `UNAUTHENTICATED`). `actorId` luôn lấy từ session — Client không gửi.

#### 6.10.1. UPDATE_PROFILE

**Quyền:** bất kỳ user đã LOGIN. Actor chỉ sửa profile của chính mình.

**Request:**
```json
{ "type": "UPDATE_PROFILE", "requestId": "u-1",
  "payload": { "fullName": "Nguyen Van A", "gender": "MALE", "dateOfBirth": "2000-05-01" } }
```

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `fullName` | string | ✅ | Trim, 1..120 ký tự |
| `gender` | string | ✅ (có thể null) | `{MALE, FEMALE, OTHER}` hoặc null. Case-insensitive, chuẩn hóa uppercase |
| `dateOfBirth` | string | ✅ (có thể null) | `yyyy-MM-dd`, ≤ hôm nay |

**Payload restriction:** chỉ 3 field trên được phép có mặt. Bất kỳ field khác (kể cả `email`, `password`, `status`, `isSystemAdmin`) → `INVALID_FIELD`. **Khác với các command khác** — Server **không** silently ignore field thừa.

**Response OK:**
```json
{ "type": "OK", "requestId": "u-1", "status": "OK",
  "payload": { "userId": 42, "email": "user@example.com", "fullName": "Nguyen Van A",
               "status": "ACTIVE", "gender": "MALE", "dateOfBirth": "2000-05-01" } }
```

**Side effects:** transaction ghi `UPDATE dbo.users` + `INSERT activity_logs` (action_type `PROFILE_UPDATED`) cùng connection. Rollback toàn bộ nếu một trong hai fail.

**Error:** `UNAUTHENTICATED`, `INVALID_FIELD`, `FULLNAME_REQUIRED`, `FULLNAME_INVALID`, `INVALID_GENDER`, `INVALID_DATE_OF_BIRTH`, `DB_ERROR`.

#### 6.10.2. REMOVE_GROUP_MEMBER

**Quyền:** group ADMIN, **hoặc** actor = target (tự rời nhóm). Không phải self và không phải admin → `NOT_GROUP_ADMIN`.

**Request:**
```json
{ "type": "REMOVE_GROUP_MEMBER", "requestId": "r-1",
  "payload": { "groupId": 5, "targetUserId": 42 } }
```

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `groupId` | long | ✅ | > 0. Thiếu/≤ 0 → `INVALID_MESSAGE` |
| `targetUserId` | long | ✅ | > 0. Thiếu/≤ 0 → `INVALID_MESSAGE` |

**Response OK:**
```json
{ "type": "OK", "requestId": "r-1", "status": "OK",
  "payload": { "groupId": 5, "removedUserId": 42 } }
```

**Last-admin invariant:** nếu target là ACTIVE ADMIN cuối cùng của group → `LAST_ADMIN_REQUIRED`. Áp dụng cả remove bởi admin khác lẫn self-leave.

**Transaction:** `lockMemberUserIds` + `lockActiveAdminIds` với UPDLOCK/HOLDLOCK; xóa member + audit `GROUP_MEMBER_REMOVED` cùng connection.

**Error:** `UNAUTHENTICATED`, `INVALID_MESSAGE`, `TARGET_NOT_IN_GROUP`, `NOT_GROUP_ADMIN`, `LAST_ADMIN_REQUIRED`, `DB_ERROR`.

#### 6.10.3. SET_GROUP_ROLE

**Quyền:** group ADMIN.

**Request:**
```json
{ "type": "SET_GROUP_ROLE", "requestId": "sr-1",
  "payload": { "groupId": 5, "targetUserId": 42, "newRole": "ADMIN" } }
```

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `groupId` | long | ✅ | > 0 |
| `targetUserId` | long | ✅ | > 0 |
| `newRole` | string | ✅ | `{ADMIN, MEMBER}` (case-insensitive, chuẩn hóa uppercase) |

**Response OK:**
```json
{ "type": "OK", "requestId": "sr-1", "status": "OK",
  "payload": { "groupId": 5, "targetUserId": 42, "newRole": "ADMIN" } }
```

**Idempotent:** nếu `newRole` = role hiện tại của target → **OK ngay, không audit**. Demote ADMIN cuối → `LAST_ADMIN_REQUIRED`.

**Transaction:** khóa member list + check actor role + check idempotent + check last-admin (nếu demote) + update + audit `GROUP_ROLE_CHANGED` cùng connection.

**Error:** `UNAUTHENTICATED`, `INVALID_MESSAGE`, `INVALID_ROLE`, `TARGET_NOT_IN_GROUP`, `NOT_GROUP_ADMIN`, `LAST_ADMIN_REQUIRED`, `DB_ERROR`.

#### 6.10.4. MARK_READ (canonical — message-level)

**Quyền:** user đã LOGIN. Direct: chỉ receiver. Group: user có receipt.

**Request:**
```json
{ "type": "MARK_READ", "requestId": "mr-1",
  "payload": { "messageType": "DIRECT", "messageId": 1001 } }
```

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `messageType` | string | ✅ | `{DIRECT, GROUP}` (case-insensitive). Không thuộc → `INVALID_MESSAGE` |
| `messageId` | long | ✅ | > 0. Thiếu/≤ 0 → `INVALID_MESSAGE` |

**Response OK — DIRECT:**
```json
{ "type": "OK", "requestId": "mr-1", "status": "OK",
  "payload": { "messageId": 1001, "messageType": "DIRECT", "status": "READ" } }
```

> **Khác v0.4 design:** response **không** chứa `readAt`. Chỉ trả `status` từ DTO sau refetch. Không dùng `sentAt` thay thế.

**Response OK — GROUP:**
```json
{ "type": "OK", "requestId": "mr-2", "status": "OK",
  "payload": { "messageId": 2002, "messageType": "GROUP" } }
```

**Side effects:**
- DIRECT thành công → update `direct_messages.status='READ'`, `read_at=NOW`, push `MESSAGE_STATUS {status:"SEEN"}` về sender (nếu online).
- GROUP thành công → update receipt `delivered_at = COALESCE(delivered_at, NOW)`, `read_at = NOW`. **Không push.**
- Idempotent: gọi lại khi đã READ → trả DTO refetch (status `READ`), không push lại.

**Error:** `UNAUTHENTICATED`, `INVALID_MESSAGE`, `MESSAGE_NOT_FOUND`, `NOT_MESSAGE_RECIPIENT`, `DB_ERROR`.

#### 6.10.5. MESSAGE_STATUS (legacy — conversation-level)

**Quyền:** user đã LOGIN. Đánh dấu toàn bộ tin chưa đọc trong hội thoại.

**Request:**
```json
{ "type": "MESSAGE_STATUS", "requestId": "ms-1",
  "payload": { "target": "peer@example.com", "isGroup": false, "status": "SEEN" } }
```

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `target` | string | ✅ | Direct: email peer (dùng `SEARCH_USER` semantics). Group: tên nhóm (trim, khớp chính xác) |
| `isGroup` | boolean | ✅ | false = direct, true = group |
| `status` | string | ✅ | Chỉ chấp nhận `"SEEN"`. Giá trị khác → `INVALID_MESSAGE` |

**Response OK — DIRECT:**
```json
{ "type": "OK", "requestId": "ms-1", "status": "OK",
  "payload": { "markedCount": 3, "peerUserId": 42 } }
```

**Response OK — GROUP:**
```json
{ "type": "OK", "requestId": "ms-2", "status": "OK",
  "payload": { "markedCount": 10, "groupId": 5 } }
```

**Group target resolution:** `findGroupsForUser(actorId)` → lọc theo tên chính xác sau trim. 0 kết quả → `GROUP_NOT_FOUND`. ≥ 2 kết quả → `AMBIGUOUS_GROUP_NAME` (không tự chọn). `groups.group_name` không có UNIQUE constraint.

**Side effects:**
- DIRECT: bulk update các tin chưa đọc từ peer. **Chỉ khi `markedCount > 0`** mới push `MESSAGE_STATUS {status:"SEEN"}` về peer.
- GROUP: bulk update receipt chưa đọc của actor trong group. **Không push.**

**Error:** `UNAUTHENTICATED`, `INVALID_MESSAGE`, `EMAIL_INVALID`, `USER_NOT_FOUND`, `GROUP_NOT_FOUND`, `AMBIGUOUS_GROUP_NAME`, `NOT_GROUP_MEMBER`, `DB_ERROR`.

#### 6.10.6. LOCK_USER

**Quyền:** system admin (ACTIVE + `is_system_admin=1`).

**Request:**
```json
{ "type": "LOCK_USER", "requestId": "lk-1",
  "payload": { "targetUserId": 42, "reason": "spam" } }
```

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `targetUserId` | long | ✅ | > 0. Thiếu/≤ 0 → `INVALID_MESSAGE` |
| `reason` | string | ❌ | Không validation bắt buộc. Null/blank được chấp nhận (chỉ dùng cho audit metadata) |

**Response OK:**
```json
{ "type": "OK", "requestId": "lk-1", "status": "OK",
  "payload": { "targetUserId": 42, "status": "LOCKED" } }
```

**Response lỗi — `CANNOT_LOCK_LAST_ADMIN_OF_GROUP`:**
```json
{ "type": "ERROR", "requestId": "lk-1", "status": "ERROR",
  "error": "CANNOT_LOCK_LAST_ADMIN_OF_GROUP",
  "payload": { "groupIds": [5, 12] } }
```

- `groupIds` là JSON array các group_id bị chặn, **sorted ASC**, kiểu `List<Long>`.
- Nằm trong `payload` (không có key `details` ở cấp ngoài cùng).

**Kiểm tra trước transaction:** `CANNOT_LOCK_SELF` (actor = target).

**Trong transaction:** khóa `group_members` của target với UPDLOCK/HOLDLOCK (sorted ASC by group_id → tránh deadlock). Với mỗi group mà target là admin: đếm ACTIVE ADMIN. Nếu group nào có đúng 1 và là target → gom `group_id` vào blocked list. Nếu blocked non-empty → rollback + throw `CANNOT_LOCK_LAST_ADMIN_OF_GROUP` với details.

Nếu pass: update `users.status='LOCKED'` + audit `USER_LOCKED` cùng connection → commit.

**Sau commit (ngoài transaction):** nếu target đang online → gửi ERROR {ACCOUNT_LOCKED} (best-effort) rồi close socket.

**Error:** `UNAUTHENTICATED`, `INVALID_MESSAGE`, `NOT_SYSTEM_ADMIN`, `CANNOT_LOCK_SELF`, `USER_NOT_FOUND`, `USER_ALREADY_LOCKED`, `CANNOT_LOCK_LAST_ADMIN_OF_GROUP`, `DB_ERROR`.

#### 6.10.7. UNLOCK_USER

**Quyền:** system admin.

**Request:**
```json
{ "type": "UNLOCK_USER", "requestId": "ul-1",
  "payload": { "targetUserId": 42 } }
```

| Field | Kiểu | Bắt buộc |
|---|---|---|
| `targetUserId` | long | ✅ (> 0) |

**Response OK:**
```json
{ "type": "OK", "requestId": "ul-1", "status": "OK",
  "payload": { "targetUserId": 42, "status": "ACTIVE" } }
```

**Side effects:** update status + audit `USER_UNLOCKED` cùng transaction.

**Error:** `UNAUTHENTICATED`, `INVALID_MESSAGE`, `NOT_SYSTEM_ADMIN`, `USER_NOT_FOUND`, `USER_NOT_LOCKED`, `DB_ERROR`.

#### 6.10.8. SYSTEM_BROADCAST

**Quyền:** system admin.

**Request:**
```json
{ "type": "SYSTEM_BROADCAST", "requestId": "bc-1",
  "payload": { "message": "Server bảo trì lúc 22:00" } }
```

| Field | Kiểu | Bắt buộc | Ràng buộc |
|---|---|---|---|
| `message` | string | ✅ | Trim, 1..1000 ký tự. Vi phạm → `INVALID_MESSAGE` |

**Response OK:**
```json
{ "type": "OK", "requestId": "bc-1", "status": "OK",
  "payload": { "recipientCount": 15, "successCount": 14, "failedCount": 1 } }
```

- `recipientCount` = `successCount + failedCount` (đã loại trừ caller).
- `successCount` = số handler `sendSecure` trả `true`.
- `failedCount` = số handler lỗi/không gửi được.
- Partial failure semantics: handler lỗi không ảnh hưởng handler khác.

**Side effects:** fanout qua `snapshotOnlineHandlers()`; loại trừ handler của actor (so sánh `==`). Audit `SYSTEM_BROADCAST` ghi **sau** fanout bằng connection riêng (best-effort, lỗi audit chỉ log WARN).

**Push type:** `SYSTEM_BROADCAST` với payload `{ "message": "..." }`.

**Error:** `UNAUTHENTICATED`, `INVALID_MESSAGE`, `NOT_SYSTEM_ADMIN`, `DB_ERROR`.

#### 6.10.9. USER_STATS

**Quyền:** system admin.

**Request:**
```json
{ "type": "USER_STATS", "requestId": "us-1", "payload": {} }
```

**Response OK:**
```json
{ "type": "OK", "requestId": "us-1", "status": "OK",
  "payload": {
    "totalUsers": 120, "activeUsers": 100, "pendingUsers": 15, "lockedUsers": 5,
    "totalGroups": 30, "onlineUsers": 42
  } }
```

| Key | Nguồn |
|---|---|
| `totalUsers` | `COUNT(*) users` |
| `activeUsers` | `COUNT(*) users WHERE status='ACTIVE'` |
| `pendingUsers` | `COUNT(*) users WHERE status='PENDING'` |
| `lockedUsers` | `COUNT(*) users WHERE status='LOCKED'` |
| `totalGroups` | `COUNT(*) groups` |
| `onlineUsers` | `ClientConnectionManager.onlineCount()` |

> **Đối chiếu §5.2.8 (M3-A):** Bộ 6 metric này khớp response contract của §5.2.8. Tên khóa metric số nhóm là `totalGroups` theo quyết định chốt P-10 ngày 2026-11-13 (bản draft §5.2.8 ghi `groupsCount` — đã được thay thế bởi quyết định này).

**Error:** `UNAUTHENTICATED`, `NOT_SYSTEM_ADMIN`, `DB_ERROR`.

### 6.11. Command không được hỗ trợ (deferral register)

Các type dưới đây **không** có handler phía Server tại thời điểm viết (c36d328), **trừ** `FILE_CHUNK` (đã hỗ trợ ở M4-B — xem §6.9). Nếu Client gửi type không có handler, Server trả `UNKNOWN_TYPE`.

| Type | Lý do | Command thay thế / ghi chú |
|---|---|---|
| `JOIN_GROUP` | OD-4 — không có cơ chế group public / tự tham gia | Không có. Admin phải `ADD_GROUP_MEMBER` |
| `LEAVE_GROUP` | Không có command riêng | Dùng `REMOVE_GROUP_MEMBER` với `targetUserId` = chính actor (self-leave; vẫn kiểm tra last-admin) |
| `ADD_FRIEND` | Social friend request chưa duyệt | Không có |
| `FRIEND_STATUS` | Chưa duyệt — Server **không** gửi push này | Không có |
| `BLOCK_USER` | Khác `LOCK_USER` (user-level block vs system-admin lock); chưa triển khai | Không có. `LOCK_USER` chỉ dành cho system-admin |
| `SEND_STICKER` | Không có command riêng | Dùng `SEND_DIRECT_MESSAGE` / `SEND_GROUP_MESSAGE` với `msgType = "STICKER"` |
| ~~`FILE_CHUNK`~~ | ✅ **Đã hỗ trợ tại M4-B** — xem §6.9 | Dùng `FILE_CHUNK` với payload theo §6.9.1 |
