# SecureChat Client Integration Guide — M3

> **Trạng thái:** SPEC cho đợt refactor Client sau M5. **Không sửa Client trong Phase 4.**
>
> **Nguồn sự thật:** Server tại `ddabb69` (M3-B CLOSED — PASS). Client tại `ddabb69` chưa gọi các command M3 (theo Handover Mục 6 — Client không đổi từ 8d66e8a).
>
> **Đối tượng:** Kỹ sư Client sẽ refactor sau M5. Tài liệu này **không** đảm bảo Client hiện tại đã hỗ trợ command nào.

## 1. Tổng quan trạng thái

| Hạng mục | Trạng thái tại ddabb69 |
|---|---|
| Server M3 | ✅ Đã triển khai 9 command M3 + 3 push M3 (nghiệm thu M3-B) |
| Client M3 | ❌ **Chưa gọi** bất kỳ command M3 nào. Handover Mục 6 ghi nhận một số literal đã có sẵn nhưng **gửi payload không khớp contract** hoặc **là dead code** |

### 1.1. Literal Client đã có sẵn (theo Handover Mục 6)

| Literal | Trạng thái thực tế | Đánh giá |
|---|---|---|
| `UPDATE_PROFILE {fullName, gender, dateOfBirth}` | Payload đúng contract | ⚠️ Có thể hoạt động nếu Client thực sự trigger gửi; cần verify sau M5 |
| `REMOVE_GROUP_MEMBER {groupName, memberEmail}` | **Payload SAI contract** — Server cần `{groupId, targetUserId}` | ❌ Sẽ nhận `INVALID_MESSAGE` |
| `LEAVE_GROUP {groupName}` | Server **không có** type này | ❌ `UNKNOWN_TYPE` (dead code) |
| `JOIN_GROUP {groupName}` | Server **không có** type này | ❌ `UNKNOWN_TYPE` (dead code) |
| `BLOCK_USER {target, targetType}` | Server **không có** type này | ❌ `UNKNOWN_TYPE` (dead code) |
| `ADD_FRIEND` | Server **không có** type này | ❌ `UNKNOWN_TYPE` (dead code) |
| `SEND_STICKER` | Server **không có** type này | ❌ `UNKNOWN_TYPE` (dead code); dùng `msgType=STICKER` qua `SEND_*_MESSAGE` |
| `FILE_CHUNK` | M4 — chưa hỗ trợ | ❌ `UNKNOWN_TYPE` |
| `MESSAGE_STATUS {target, isGroup, status:"SEEN"}` | **Đúng contract** legacy conversation-level | ✅ Hoạt động (nếu Client gửi) |
| Nhận `MESSAGE_STATUS {status}` | Có nhánh receive | ✅ Sẽ nhận push direct nếu peer mark read |
| Nhận `SYSTEM_BROADCAST {message}` | Có nhánh receive | ✅ Sẽ nhận push broadcast |
| Nhận `FRIEND_STATUS {email, status}` | Có nhánh receive | ⚠️ **Dead** — Server không bao giờ gửi type này |
| `sendChatMessage` hardcode `msgType="TEXT"` | — | ⚠️ Không gửi được STICKER dù Server hỗ trợ |

> **Cần xác nhận:** Handover Mục 6 mô tả trạng thái Client tại 8d66e8a. Guide này giả định Client tại ddabb69 giữ nguyên (Phase 4 không sửa Client). Nếu có thay đổi, các dòng trên cần được đối chiếu lại với source Client thật.

## 2. Ánh xạ command M3 — hiện trạng Client → contract Server

### 2.1. UPDATE_PROFILE

**Hiện trạng Client:** đã có literal, payload đúng.

**Contract Server (xem protocol.md §6.10.1):**

| Client gửi | Server yêu cầu | Ghi chú |
|---|---|---|
| `{fullName, gender, dateOfBirth}` | `{fullName, gender, dateOfBirth}` | ✅ Khớp |
| — | Không được gửi `email`, `password`, `status`, `isSystemAdmin` | Nếu Client vô tình gửi → `INVALID_FIELD` |

**Request mẫu:**
```json
{ "type":"UPDATE_PROFILE", "requestId":"u-1",
  "payload": { "fullName":"Nguyen Van A", "gender":"MALE", "dateOfBirth":"2000-05-01" } }
```

**Response mẫu (OK):**
```json
{ "type":"OK", "requestId":"u-1", "status":"OK",
  "payload": { "userId":42, "email":"user@example.com", "fullName":"Nguyen Van A",
               "status":"ACTIVE", "gender":"MALE", "dateOfBirth":"2000-05-01" } }
```

**Response mẫu (lỗi điển hình):**
```json
{ "type":"ERROR", "requestId":"u-1", "status":"ERROR", "error":"INVALID_FIELD", "payload":{} }
```

**Sửa ở refactor M5+:** nếu Client đang gửi đúng payload → không cần sửa. Nếu Client gộp với form gửi thêm field → loại bỏ field thừa.

### 2.2. REMOVE_GROUP_MEMBER

**Hiện trạng Client:** đã có literal với payload `{groupName, memberEmail}` — **SAI contract**.

**Contract Server (xem protocol.md §6.10.2):**

| Client hiện gửi | Server yêu cầu | Hành động refactor |
|---|---|---|
| `groupName` (string) | `groupId` (long > 0) | Client phải resolve `groupName → groupId` qua `FETCH_MY_GROUPS` (khớp exact; ≥2 → không gửi) |
| `memberEmail` (string) | `targetUserId` (long > 0) | Client phải resolve `email → userId` qua `SEARCH_USER` |

**Request mẫu:**
```json
{ "type":"REMOVE_GROUP_MEMBER", "requestId":"r-1",
  "payload": { "groupId": 5, "targetUserId": 42 } }
```

**Response mẫu (OK):**
```json
{ "type":"OK", "requestId":"r-1", "status":"OK",
  "payload": { "groupId": 5, "removedUserId": 42 } }
```

**Response mẫu (lỗi last-admin):**
```json
{ "type":"ERROR", "requestId":"r-1", "status":"ERROR",
  "error":"LAST_ADMIN_REQUIRED", "payload":{} }
```

**Self-leave:** dùng cùng command với `targetUserId` = userId chính mình. **Không** có `LEAVE_GROUP`.

### 2.3. SET_GROUP_ROLE

**Hiện trạng Client:** **chưa có literal** (dead-code cũ không có type này).

**Contract Server (xem protocol.md §6.10.3):**

| Client cần gửi | Ghi chú |
|---|---|
| `groupId` (long > 0) | Resolve từ group list |
| `targetUserId` (long > 0) | Resolve từ email nếu UI chọn theo email |
| `newRole` (`"ADMIN"` / `"MEMBER"`) | Upper-case trước khi gửi để khớp |

**Request mẫu:**
```json
{ "type":"SET_GROUP_ROLE", "requestId":"sr-1",
  "payload": { "groupId": 5, "targetUserId": 42, "newRole": "ADMIN" } }
```

**Response mẫu (OK):**
```json
{ "type":"OK", "requestId":"sr-1", "status":"OK",
  "payload": { "groupId": 5, "targetUserId": 42, "newRole": "ADMIN" } }
```

**Response mẫu (idempotent — role không đổi):** cũng trả `OK`; server không cập nhật DB và không ghi audit. Client không phân biệt được idempotent với thay đổi thật — coi cả hai là OK.

### 2.4. MARK_READ (canonical — message-level)

**Hiện trạng Client:** **chưa có literal** cho command này. Client dùng `MESSAGE_STATUS` (conversation-level) thay thế.

**Contract Server (xem protocol.md §6.10.4):**

**Request mẫu (direct):**
```json
{ "type":"MARK_READ", "requestId":"mr-1",
  "payload": { "messageType":"DIRECT", "messageId": 1001 } }
```

**Request mẫu (group):**
```json
{ "type":"MARK_READ", "requestId":"mr-2",
  "payload": { "messageType":"GROUP", "messageId": 2002 } }
```

**Response mẫu (direct OK):**
```json
{ "type":"OK", "requestId":"mr-1", "status":"OK",
  "payload": { "messageId":1001, "messageType":"DIRECT", "status":"READ" } }
```

**Response mẫu (group OK):**
```json
{ "type":"OK", "requestId":"mr-2", "status":"OK",
  "payload": { "messageId":2002, "messageType":"GROUP" } }
```

> ⚠️ **Không có `readAt`** trong response. Đây là khác biệt so với M3-A Design v1 (Design có thể kỳ vọng trả readAt). Server tại ddabb69 không trả.

**Lỗi điển hình:**
```json
{ "type":"ERROR", "requestId":"mr-1", "status":"ERROR", "error":"NOT_MESSAGE_RECIPIENT", "payload":{} }
```

### 2.5. MESSAGE_STATUS (legacy — conversation-level)

**Hiện trạng Client:** đã có `markConversationSeen(target, isGroup)` gửi qua `sendQuietly`. Payload `{target, isGroup, status:"SEEN"}` — **khớp contract**.

**Contract Server (xem protocol.md §6.10.5):**

**Request mẫu (direct — target là email peer):**
```json
{ "type":"MESSAGE_STATUS", "requestId":"ms-1",
  "payload": { "target":"peer@example.com", "isGroup": false, "status":"SEEN" } }
```

**Request mẫu (group — target là tên nhóm):**
```json
{ "type":"MESSAGE_STATUS", "requestId":"ms-2",
  "payload": { "target":"Team Alpha", "isGroup": true, "status":"SEEN" } }
```

**Response mẫu (direct OK):**
```json
{ "type":"OK", "requestId":"ms-1", "status":"OK",
  "payload": { "markedCount": 3, "peerUserId": 42 } }
```

**Response mẫu (group OK):**
```json
{ "type":"OK", "requestId":"ms-2", "status":"OK",
  "payload": { "markedCount": 10, "groupId": 5 } }
```

**Response mẫu (group ambiguous):**
```json
{ "type":"ERROR", "requestId":"ms-2", "status":"ERROR", "error":"AMBIGUOUS_GROUP_NAME", "payload":{} }
```

**Field mapping — quan trọng:**

| Client hiện gửi | Server yêu cầu | Ghi chú |
|---|---|---|
| `target` = email (direct) | ✅ đúng | Server chạy `SEARCH_USER` (fail → `USER_NOT_FOUND`) |
| `target` = tên nhóm (group) | ⚠️ chấp nhận nhưng **nên** chuyển sang `MARK_READ` group với `groupId` | `MESSAGE_STATUS` group là legacy; nếu tên trùng → `AMBIGUOUS_GROUP_NAME`, không tự chọn |
| `isGroup` boolean | ✅ đúng | — |
| `status` = `"SEEN"` | ✅ chỉ chấp nhận `"SEEN"` | Giá trị khác → `INVALID_MESSAGE` |

### 2.6. LOCK_USER / UNLOCK_USER / SYSTEM_BROADCAST / USER_STATS

**Hiện trạng Client:** không có UI admin. **Chưa có literal** cho 4 command này.

**Contract Server:** xem protocol.md §6.10.6 – §6.10.9.

**Điểm cần lưu ý khi xây UI admin:**

1. **LOCK_USER** — response lỗi `CANNOT_LOCK_LAST_ADMIN_OF_GROUP` mang `payload.groupIds` (array of long). UI phải đọc và hiển thị danh sách group_id cho admin biết user bị chặn vì là admin cuối của những group nào.

   **Request mẫu:**
   ```json
   { "type":"LOCK_USER", "requestId":"lk-1",
     "payload": { "targetUserId": 42, "reason": "spam" } }
   ```
   **Response lỗi có details:**
   ```json
   { "type":"ERROR", "requestId":"lk-1", "status":"ERROR",
     "error":"CANNOT_LOCK_LAST_ADMIN_OF_GROUP",
     "payload": { "groupIds": [5, 12] } }
   ```

2. **UNLOCK_USER** — payload chỉ `{targetUserId}`.

3. **SYSTEM_BROADCAST** — payload `{message}` (1..1000 ký tự). Client phải log `recipientCount`, `successCount`, `failedCount` để admin biết có bao nhiêu client nhận được.

4. **USER_STATS** — payload rỗng. Response 6 key (xem protocol.md §6.10.9). Không có metric nào khác tại ddabb69.

## 3. Xử lý push phía Client (việc tương lai — sau M5)

Client hiện tại (theo Handover Mục 6) đã có một số nhánh nhận push. Khi refactor, các nhánh cần được hoàn thiện như sau:

### 3.1. Nhận push `MESSAGE_STATUS {status:"SEEN"}`

- **Áp dụng:** chỉ **direct**. Nhóm không có push MESSAGE_STATUS.
- **Hành động:** cập nhật trạng thái "đã xem" cho tất cả tin trong hội thoại direct tương ứng (peer gửi push sau khi đánh dấu toàn bộ tin chưa đọc là SEEN).
- **Nguồn push:** peer (người nhận tin của mình) sau `MARK_READ` direct hoặc `MESSAGE_STATUS` direct thành công với `markedCount > 0`.
- **Client hiện tại:** đã có nhánh receive `MESSAGE_STATUS {status}` (Handover Mục 6) — có thể hoạt động khi Server push tới; cần verify UI cập nhật đúng conversation.

### 3.2. Nhận push `SYSTEM_BROADCAST {message}`

- **Hành động:** hiển thị thông báo hệ thống (dialog/banner/tray). Không cần trả lời.
- **Client hiện tại:** đã có nhánh receive (Handover Mục 6) — có thể hoạt động; cần verify UI show đúng.

### 3.3. Nhận push `ERROR {ACCOUNT_LOCKED}` khi đang online

- **Hành động:** đăng xuất bắt buộc — đóng socket, quay về màn hình login, hiển thị lý do "Tài khoản đã bị khóa bởi admin".
- **Lưu ý:** Server gửi packet này qua `sendSecure` như mọi traffic sau handshake — tức inner packet `ERROR` với `requestId: null`, `error: "ACCOUNT_LOCKED"` nằm bên trong `SECURE_MESSAGE`. Gửi best-effort, sau đó server đóng socket vô điều kiện.
- **Client hiện tại:** Handover Mục 6 **không ghi nhận** nhánh receive ERROR {ACCOUNT_LOCKED}. → **Cần thêm** trong refactor.
- **Race:** nếu socket đã đóng trước khi client kịp nhận, client sẽ thấy EOF; vẫn phải xử lý đăng xuất (nhưng không có lý do cụ thể — hiển thị lý do generic "mất kết nối").

### 3.4. Nhánh receive đã có nhưng **không có nguồn** — `FRIEND_STATUS`

- Client hiện có nhánh receive `FRIEND_STATUS {email, status}` nhưng Server tại ddabb69 **không bao giờ** gửi type này (xem protocol.md §6.11).
- **Refactor:** xóa nhánh này (dead code) hoặc để nguyên nếu không ảnh hưởng; ghi chú rõ trong refactor ticket.

## 4. File/method Client dự kiến phải sửa trong đợt refactor sau M5

> **Chỉ liệt kê — không sửa trong Phase 4.** Dựa trên cấu trúc Client tại 8d66e8a (Handover Mục 6) — cần verify file path thực tế tại ddabb69 khi mở ticket refactor.

| Nhóm | Đối tượng dự kiến | Hành động |
|---|---|---|
| Command sender | Nơi dựng `REMOVE_GROUP_MEMBER {groupName, memberEmail}` | Sửa payload → `{groupId, targetUserId}`; thêm bước resolve `groupName → groupId` (qua FETCH_MY_GROUPS) và `email → userId` (qua SEARCH_USER) |
| Command sender | Nơi dựng `LEAVE_GROUP {groupName}` | Thay bằng `REMOVE_GROUP_MEMBER` với `targetUserId` = userId chính mình. Xóa literal LEAVE_GROUP |
| Command sender | Nơi dựng `JOIN_GROUP {groupName}` | Xóa (Server không hỗ trợ) |
| Command sender | Nơi dựng `BLOCK_USER {target, targetType}` | Xóa (khác LOCK_USER) |
| Command sender | Nơi dựng `ADD_FRIEND` | Xóa (không triển khai) |
| Command sender | Nơi dựng `SEND_STICKER` | Xóa literal; nếu UI sticker cần, chuyển sang `SEND_*_MESSAGE` với `msgType="STICKER"` |
| Command sender | `sendChatMessage` (hardcode `msgType="TEXT"`) | Thêm tham số `msgType`, truyền `"TEXT"` hoặc `"STICKER"` theo UI |
| Command sender | `markConversationSeen(target, isGroup)` | (a) Giữ làm legacy cho conversation-level bulk; (b) **Bổ sung** `markMessageRead(messageType, messageId)` gọi `MARK_READ` message-level cho từng tin (canonical) |
| Command sender (mới) | UI Profile | Gọi `UPDATE_PROFILE` với đúng 3 field |
| Command sender (mới) | UI Group admin | Gọi `SET_GROUP_ROLE` với `{groupId, targetUserId, newRole}` |
| Command receiver | Xử lý push `MESSAGE_STATUS {status}` | Đã có nhánh; verify UI cập nhật đúng conversation direct |
| Command receiver | Xử lý push `SYSTEM_BROADCAST {message}` | Đã có nhánh; verify UI show notification |
| Command receiver | Xử lý `ERROR {ACCOUNT_LOCKED}` | **Thêm nhánh** — đăng xuất + show lý do |
| Command receiver | Nhánh `FRIEND_STATUS` | Xóa (dead) hoặc để nguyên với ghi chú |
| Command sender (mới) | UI Admin (nếu có) | 4 command `LOCK_USER`, `UNLOCK_USER`, `SYSTEM_BROADCAST`, `USER_STATS` — chưa có UI, cần thêm |
| Command sender (mới) | Chỗ dựng `FILE_CHUNK` | **Giữ** nhưng biết rằng sẽ nhận `UNKNOWN_TYPE` tới M4; hoặc gate sau feature flag |
| Error handling | Catch `CANNOT_LOCK_LAST_ADMIN_OF_GROUP` | Đọc `payload.groupIds` (array), hiển thị danh sách group |
| Error handling | Catch `AMBIGUOUS_GROUP_NAME` | Yêu cầu user chọn group khác / dùng groupId trực tiếp |

## 5. Nguyên tắc tương thích

1. **Server M3 là additive** — mọi command Phase 1–4 và M1 giữ nguyên contract. Client cũ (không gọi command M3) không bị phá vỡ.
2. **Client cũ gửi type M3** (ví dụ `MESSAGE_STATUS` đã có) → Server xử lý như bình thường nếu payload đúng. Nếu payload sai (như `REMOVE_GROUP_MEMBER {groupName, memberEmail}`) → Server trả lỗi validation (`INVALID_MESSAGE` hoặc lỗi resolve), Client cũ **không** crash.
3. **Push M3 (MESSAGE_STATUS, SYSTEM_BROADCAST) chỉ gửi tới client đang online** — client cũ (không có nhánh receive) sẽ nhận packet lạ; nên bỏ qua an toàn (không crash). Client cũ **không bị ép** xử lý push M3.
4. **ACCOUNT_LOCKED** — client cũ nhận ERROR này có thể không hiểu; cần đảm bảo fallback = đóng socket như mất kết nối bình thường. Server vẫn đóng socket → client cũ vẫn disconnect dù không hiểu lý do.
5. **Không xóa** bất kỳ literal cũ nào ở phía Client (trừ dead code đã xác nhận) cho tới khi test E2E M5 xác nhận.