$ErrorActionPreference = "Stop"

$ProjectRoot = (Get-Location).Path
$ServerJar = Join-Path $ProjectRoot "securechat-server\target\securechat-server.jar"
$ServerHost = "127.0.0.1"
$ServerPort = 8080
$ClientCount = 5
$MultiClients = @()
$TestFailed = $false

function Write-Pass { param([string]$Message); Write-Host "[PASS] $Message" }
function Write-Fail { param([string]$Message); Write-Host "[FAIL] $Message" }

function Test-ServerPort {
    param([string]$HostName, [int]$Port)
    $Tcp = New-Object System.Net.Sockets.TcpClient
    try {
        $Async = $Tcp.BeginConnect($HostName, $Port, $null, $null)
        if (-not $Async.AsyncWaitHandle.WaitOne(1000, $false)) { return $false }
        $Tcp.EndConnect($Async)
        return $true
    } catch { return $false } finally { $Tcp.Close() }
}

function Read-TcpLine {
    param([System.IO.StreamReader]$Reader, [int]$TimeoutMs = 5000)
    $Task = $Reader.ReadLineAsync()
    if (-not $Task.Wait($TimeoutMs)) { throw "Timeout: Server không phản hồi trong $TimeoutMs ms." }
    return $Task.GetAwaiter().GetResult()
}

# 1. KIỂM TRA THƯ MỤC
Write-Host "=== KIỂM TRA THƯ MỤC DỰ ÁN ==="
if (-not (Test-Path (Join-Path $ProjectRoot "pom.xml"))) {
    throw "Không tìm thấy pom.xml. Hãy mở Terminal tại thư mục gốc dự án."
}
Write-Pass "Project root: $ProjectRoot"

# 2. BUILD MAVEN
Write-Host "=== BƯỚC 1: BUILD DỰ ÁN MAVEN ==="
& mvn clean verify
if ($LASTEXITCODE -ne 0) { throw "Maven build thất bại. Exit code: $LASTEXITCODE" }
Write-Pass "mvn clean verify thành công."
if (-not (Test-Path $ServerJar)) { throw "Không tìm thấy Server JAR: $ServerJar" }

# 3. KHỞI ĐỘNG SERVER
Write-Host "=== BƯỚC 2: KHỞI ĐỘNG SERVER JAR ==="
$PowerShellExe = (Get-Process -Id $PID).Path
$ServerCommand = "Set-Location '$ProjectRoot'; java -jar '$ServerJar'"
$ServerWindow = Start-Process -FilePath $PowerShellExe -ArgumentList "-NoExit", "-Command", $ServerCommand -PassThru
Write-Host "Server đang chạy trong cửa sổ mới (PID: $($ServerWindow.Id))."

# 4. KIỂM TRA PORT
Write-Host "=== BƯỚC 3: KIỂM TRA PORT SERVER ==="
$PortReady = $false
for ($i = 1; $i -le 30; $i++) {
    if (Test-ServerPort -HostName $ServerHost -Port $ServerPort) {
        $PortReady = $true
        break
    }
    Start-Sleep -Seconds 1
}
if (-not $PortReady) { throw "Server không mở port $ServerPort." }
Write-Pass "Server đang LISTEN tại ${ServerHost}:$ServerPort"

# 5. TEST BUG B
Write-Host "=== BƯỚC 4: TEST BUG B (SOCKET SỐNG SAU LỖI JSON) ==="
$BugBClient = New-Object System.Net.Sockets.TcpClient
try {
    $BugBClient.Connect($ServerHost, $ServerPort)
    $Stream = $BugBClient.GetStream()
    $Stream.ReadTimeout = 5000
    $Writer = New-Object System.IO.StreamWriter($Stream); $Writer.AutoFlush = $true
    $Reader = New-Object System.IO.StreamReader($Stream)

    Write-Host "Gửi JSON rác..."
    $Writer.WriteLine("this is garbage")
    $BadJsonResponse = Read-TcpLine -Reader $Reader
    Write-Host "Server response: $BadJsonResponse"
    if ($null -eq $BadJsonResponse -or $BadJsonResponse -notmatch "BAD_JSON") {
        throw "Không nhận được BAD_JSON."
    }
    Write-Pass "Server đã phản hồi BAD_JSON."

    Write-Host "Gửi PING..."
    $PingJson = '{"type":"PING","requestId":"bug-b-ping","payload":{}}'
    $Writer.WriteLine($PingJson)
    $PingResponse = Read-TcpLine -Reader $Reader
    Write-Host "Server response: $PingResponse"
    if ($null -eq $PingResponse -or $PingResponse -notmatch "PONG") {
        throw "Không nhận được PONG."
    }
    Write-Pass "Bug B: Socket vẫn sống sau lỗi JSON."
} catch {
    Write-Fail "Bug B: $($_.Exception.Message)"
    $TestFailed = $true
} finally {
    if ($BugBClient) { $BugBClient.Close() }
}

# 6. MULTI-CLIENT PRE-HANDSHAKE
Write-Host "=== BƯỚC 5: TEST MULTI-CLIENT PRE-HANDSHAKE ==="
$PendingConnections = @()
for ($i = 1; $i -le $ClientCount; $i++) {
    $Client = New-Object System.Net.Sockets.TcpClient
    try {
        $Async = $Client.BeginConnect($ServerHost, $ServerPort, $null, $null)
        $PendingConnections += [PSCustomObject]@{ Number = $i; Client = $Client; Async = $Async }
    } catch {
        Write-Fail "Không thể kết nối client $i : $($_.Exception.Message)"
        $Client.Close()
        $TestFailed = $true
    }
}

foreach ($Item in $PendingConnections) {
    try {
        if (-not $Item.Async.AsyncWaitHandle.WaitOne(5000, $false)) { throw "Connection timeout." }
        $Item.Client.EndConnect($Item.Async)
        $MultiClients += $Item.Client
        Write-Pass "Client $($Item.Number) connected."
    } catch {
        Write-Fail "Client $($Item.Number) thất bại: $($_.Exception.Message)"
        $Item.Client.Close()
        $TestFailed = $true
    }
}

if ($MultiClients.Count -eq $ClientCount) {
    Write-Pass "Multi-client thành công ($($MultiClients.Count)/$ClientCount)."
} else {
    Write-Fail "Multi-client thất bại ($($MultiClients.Count)/$ClientCount)."
    $TestFailed = $true
}

# 7. TEST BUG C
Write-Host "=== BƯỚC 6: TEST BUG C (SERVER SHUTDOWN) ==="
Write-Host "HÃY THỰC HIỆN: Chuyển sang cửa sổ Server nhấn Ctrl+C, chờ tắt xong rồi nhấn Enter tại đây."
Read-Host "Nhấn Enter sau khi Server đã shutdown..."

$ClosedCount = 0
for ($i = 0; $i -lt $MultiClients.Count; $i++) {
    $Client = $MultiClients[$i]
    try {
        $Stream = $Client.GetStream()
        $Stream.ReadTimeout = 5000
        $Buffer = New-Object byte[] 1
        $BytesRead = $Stream.Read($Buffer, 0, 1)
        if ($BytesRead -eq 0) {
            Write-Pass "Client $($i + 1): nhận EOF."
            $ClosedCount++
        } else {
            Write-Fail "Client $($i + 1): socket vẫn còn nhận dữ liệu."
        }
    } catch [System.IO.IOException] {
        Write-Pass "Client $($i + 1): socket đã đóng."
        $ClosedCount++
    } catch [System.ObjectDisposedException] {
        Write-Pass "Client $($i + 1): socket đã dispose."
        $ClosedCount++
    } catch {
        Write-Fail "Client $($i + 1): $($_.Exception.Message)"
    }
}

if ($ClosedCount -eq $MultiClients.Count -and $MultiClients.Count -gt 0) {
    Write-Pass "Bug C: Tất cả client đã ngắt kết nối an toàn."
} else {
    Write-Fail "Bug C: Chỉ $ClosedCount/$($MultiClients.Count) client ngắt kết nối."
    $TestFailed = $true
}

# CLEANUP & KẾT QUẢ
foreach ($Client in $MultiClients) { try { $Client.Close() } catch {} }

Write-Host "=== TỔNG KẾT TEST ==="
if ($TestFailed) {
    Write-Fail "Có bài test chưa đạt. Kiểm tra lại log phía trên."
} else {
    Write-Pass "TẤT CẢ BÀI TEST ĐÃ HOÀN THÀNH THÀNH CÔNG!"
}