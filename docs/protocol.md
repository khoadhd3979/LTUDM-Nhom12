# SecureChat Protocol v0.3 — Secure Transport

## Transport
- TCP, port `SC_PORT` (default 8080)
- **NDJSON UTF-8**: một JSON/dòng, kết thúc `\n`
- Frame tối đa 1,000,000 byte (không tính newline)
- Vượt → `FRAME_TOO_LARGE`, đóng kết nối

## Envelope
Request: `{ "type": "...", "requestId": "...", "payload": {...} }`
Response OK: `{ "type": "OK", "requestId": "...", "status": "OK", "payload": {...} }`
Response Error: `{ "type": "ERROR", "requestId": "...", "status": "ERROR", "error": "CODE" }`

## Handshake state machine (Client side)

| Bước | Client gửi | Client nhận | Client verify |
|---|---|---|---|
| 1 | `CLIENT_HELLO {version:"v1", clientNonce: b64(32B)}` | | |
| 2 | | `SERVER_HELLO {version, keyId, serverNonce, ephemeralPublicKey, signature}` | Pinned identity pubkey + RSA-PSS verify TBS |
| 3 | `KEY_EXCHANGE {encryptedSecret: b64(RSA-OAEP-SHA256(ephemeralPub, secret32B))}` | | |
| 4 | | `SECURE_MESSAGE seq=1` (plaintext = `HANDSHAKE_CONFIRM {confirm:"SERVER_CONFIRMED"}`) | AES-GCM decrypt, verify confirm value |
| 5 | `SECURE_MESSAGE seq=1` (plaintext = `HANDSHAKE_CONFIRM {confirm:"CLIENT_CONFIRMED"}`) | | |
| 6 | Now ESTABLISHED — application messages | | |

**TBS canonical (length-prefixed với uint32 BE cho mỗi field):**