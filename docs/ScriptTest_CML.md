# 1. BUILD DỰ ÁN
mvn -q clean package

# 2. KHỞI ĐỘNG SERVER (MỞ CỬA SỔ MỚI)
java -jar target/securechat-server.jar
java -jar target/securechat-server.jar

# 3. TEST PHASE 1 (NỀN TẢNG TCP)

Write-Host "-> Gửi PING..."
{"type":"PING","requestId":"req-01","payload":{}}

Write-Host "-> Gửi Type lạ (UNKNOWN_TYPE / HANDSHAKE_REQUIRED)..."
{"type":"FOO","requestId":"req-02","payload":{}}

# 4. TEST PHASE 2 (XÁC THỰC AUTH)
Write-Host "-> Gửi REGISTER (test@x.com)..."
{"type":"REGISTER","requestId":"r-reg","payload":{"email":"test@x.com","password":"Secret123","fullName":"Test User"}}

# Dừng lại để lấy OTP

Write-Host "`n-> Gửi VERIFY_OTP..."
{`"type`":`"VERIFY_OTP`",`"requestId`":`"r-otp`",`"payload`":{`"email`":`"test@x.com`",`"otp`":`"$otp`"}}

Write-Host "-> Gửi LOGIN..."
{"type":"LOGIN","requestId":"r-login","payload":{"email":"test@x.com","password":"Secret123"}}'

{"type":"LOGOUT","requestId":"r-logout","payload":{}}'

# 5. HƯỚNG DẪN TEST PHASE 3
java -cp target/securechat-server.jar com.securechat.tools.Phase3TestClient localhost 8080 server_identity.pub