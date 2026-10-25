# 1. BUILD DỰ ÁN
mvn -q clean package

# 2. KHỞI ĐỘNG SERVER (MỞ CỬA SỔ MỚI)
java -jar target/securechat-server.jar

# 3. TEST PHASE 1 (NỀN TẢNG TCP)
$c = New-Object System.Net.Sockets.TcpClient("localhost", 8080)
$s = $c.GetStream()
$w = New-Object System.IO.StreamWriter($s); $w.AutoFlush = $true
$r = New-Object System.IO.StreamReader($s)

Write-Host "-> Gửi PING..."
$w.WriteLine('{"type":"PING","requestId":"req-01","payload":{}}')
Write-Host "<- Nhận: " -NoNewline; Write-Host $r.ReadLine() -ForegroundColor Green

Write-Host "-> Gửi Chuỗi rác (BAD_JSON)..."
$w.WriteLine('hello_world')
Write-Host "<- Nhận: " -NoNewline; Write-Host $r.ReadLine() -ForegroundColor Green

Write-Host "-> Gửi Type lạ (UNKNOWN_TYPE / HANDSHAKE_REQUIRED)..."
$w.WriteLine('{"type":"FOO","requestId":"req-02","payload":{}}')
Write-Host "<- Nhận: " -NoNewline; Write-Host $r.ReadLine() -ForegroundColor Green
$c.Close()

# 4. TEST PHASE 2 (XÁC THỰC AUTH)
$c2 = New-Object System.Net.Sockets.TcpClient("localhost", 8080)
$s2 = $c2.GetStream()
$w2 = New-Object System.IO.StreamWriter($s2); $w2.AutoFlush = $true
$r2 = New-Object System.IO.StreamReader($s2)

Write-Host "-> Gửi REGISTER (test@x.com)..."
$w2.WriteLine('{"type":"REGISTER","requestId":"r-reg","payload":{"email":"test@x.com","password":"Secret123","fullName":"Test User"}}')
Write-Host "<- Nhận: " -NoNewline; Write-Host $r2.ReadLine() -ForegroundColor Green

# Dừng lại để lấy OTP
Write-Host "`n>>> HÃY NHÌN SANG CỬA SỔ SERVER ĐỂ LẤY MÃ OTP (6 SỐ) <<<" -ForegroundColor Red
$otp = Read-Host "Nhập mã OTP vào đây"

Write-Host "`n-> Gửi VERIFY_OTP..."
$w2.WriteLine("{`"type`":`"VERIFY_OTP`",`"requestId`":`"r-otp`",`"payload`":{`"email`":`"test@x.com`",`"otp`":`"$otp`"}}")
Write-Host "<- Nhận: " -NoNewline; Write-Host $r2.ReadLine() -ForegroundColor Green

Write-Host "-> Gửi LOGIN..."
$w2.WriteLine('{"type":"LOGIN","requestId":"r-login","payload":{"email":"test@x.com","password":"Secret123"}}')
Write-Host "<- Nhận: " -NoNewline; Write-Host $r2.ReadLine() -ForegroundColor Green

Write-Host "-> Gửi LOGOUT..."
$w2.WriteLine('{"type":"LOGOUT","requestId":"r-logout","payload":{}}')
Write-Host "<- Nhận: " -NoNewline; Write-Host $r2.ReadLine() -ForegroundColor Green
$c2.Close()

# 5. HƯỚNG DẪN TEST PHASE 3
Write-Host "     java -cp target/securechat-server.jar com.securechat.tools.Phase3TestClient localhost 8080 server_identity.pub" -ForegroundColor Cyan