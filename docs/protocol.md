# SecureChat Protocol — v0.1 (Phase 1)

## Khung truyền
- TCP, port 8080 (env SC_PORT).
- **Newline-delimited JSON (UTF-8)**: mỗi gói là một JSON một dòng, kết thúc bằng `\n`.
- Client và Server dùng BufferedReader.readLine() / PrintWriter.println().
- Frame tối đa 1_000_000 ký tự → vượt thì Server trả `ERROR FRAME_TOO_LARGE` và bỏ qua dòng.

## Cấu trúc gói
Request:
```json
{ "type": "PING", "requestId": "req-001", "payload": {} }