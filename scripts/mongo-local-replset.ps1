# Bật replica set 1 node cho MongoDB cài máy (service "MongoDB") để có transaction.
# Chạy một lần bằng PowerShell quyền Administrator:
#   powershell -ExecutionPolicy Bypass -File scripts\mongo-local-replset.ps1
# Sau đó đặt MONGODB_INIT_REPLSET=true trong .env: lần khởi động đầu backend tự chạy replSetInitiate.
param(
    [string]$ServiceName = "MongoDB",
    [string]$ReplSetName = "rs0"
)
$ErrorActionPreference = "Stop"

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Cần mở PowerShell bằng quyền Administrator."
}

$svc = Get-CimInstance Win32_Service -Filter "Name='$ServiceName'"
if (-not $svc) { throw "Không thấy service $ServiceName." }
if ($svc.PathName -notmatch '--config\s+"?([^"]+?\.cfg)"?(\s|$)') { throw "Service không dùng file --config: $($svc.PathName)" }
$cfg = $Matches[1]
Write-Host "File cấu hình: $cfg"

$text = Get-Content -Raw -Path $cfg
if ($text -match '(?m)^replication:\s*$') {
    Write-Host "Đã có mục replication, giữ nguyên."
} else {
    Copy-Item $cfg "$cfg.bak" -Force
    $text = $text -replace '(?m)^#replication:\s*$', "replication:`r`n  replSetName: $ReplSetName"
    if ($text -notmatch '(?m)^replication:\s*$') {
        $text = $text.TrimEnd() + "`r`n`r`nreplication:`r`n  replSetName: $ReplSetName`r`n"
    }
    Set-Content -Path $cfg -Value $text -Encoding ascii
    Write-Host "Đã thêm replication.replSetName=$ReplSetName (bản cũ: $cfg.bak)"
}

Restart-Service -Name $ServiceName
Write-Host "Đã khởi động lại $ServiceName. Đặt MONGODB_INIT_REPLSET=true trong .env rồi chạy backend."
