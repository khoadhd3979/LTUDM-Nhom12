# 1. BUILD DỰ ÁN (Multi-Module Maven)
mvn clean package

# 2. KHỞI ĐỘNG SERVER (Mở Terminal 1)
java -jar securechat-server/target/securechat-server.jar

# 3. KHỞI ĐỘNG CLIENT SWING GUI (Mở Terminal 2)
java -jar securechat-client/target/securechat-client.jar

# 4. TEST CLI THỦ CÔNG PHASE 1 (Nền tảng TCP NDJSON)
Write-Host "-> Gửi PING..."
{"type":"PING","requestId":"req-01","payload":{}}

Write-Host "-> Gửi Type lạ (UNKNOWN_TYPE / HANDSHAKE_REQUIRED)..."
{"type":"FOO","requestId":"req-02","payload":{}}

# 5. TEST CLI THỦ CÔNG PHASE 2 (Xác thực Auth)
Write-Host "-> Gửi REGISTER (test@x.com)..."
{"type":"REGISTER","requestId":"r-reg","payload":{"email":"test@x.com","password":"Secret123","fullName":"Test User"}}

# Dừng lại để lấy OTP
Write-Host "`n-> Gửi VERIFY_OTP..."
{"type":"VERIFY_OTP","requestId":"r-otp","payload":{"email":"test@x.com","otp":"123456"}}

Write-Host "-> Gửi LOGIN..."
{"type":"LOGIN","requestId":"r-login","payload":{"email":"test@x.com","password":"Secret123"}}

Write-Host "-> Gửi LOGOUT..."
{"type":"LOGOUT","requestId":"r-logout","payload":{}}

# 6. TEST SECURE TRANSPORT CLI (Phase 3 Reference Client)
java -cp securechat-server/target/securechat-server.jar com.securechat.tools.Phase3TestClient localhost 8080 server_identity.pub
