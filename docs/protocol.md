# SecureChat Protocol v0.2

## Transport
- TCP, port `SC_PORT` (default 8080)
- **NDJSON UTF-8**: Mỗi frame là JSON một dòng, kết thúc bằng `\n`.
- Frame tối đa **1,000,000 byte** (không tính newline). Vượt → trả `FRAME_TOO_LARGE` và đóng kết nối.

## Envelope Structure
Request:
```json
{ "type": "...", "requestId": "...", "payload": { ... } }

Response OK:

JSON
{ "type": "OK", "requestId": "...", "status": "OK", "payload": { ... } }
Response Error:

JSON
{ "type": "ERROR", "requestId": "...", "status": "ERROR", "error": "CODE" }