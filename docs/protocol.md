# SecureChat Protocol v0.3 — Secure Transport & E2EE

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