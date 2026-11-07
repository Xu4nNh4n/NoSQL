# Bat replica set 1 node cho MongoDB cai may (service "MongoDB") de co transaction.
# Chay mot lan bang PowerShell quyen Administrator:
#   powershell -ExecutionPolicy Bypass -File scripts\mongo-local-replset.ps1
# Sau do dat MONGODB_INIT_REPLSET=true trong .env: lan khoi dong dau backend tu chay replSetInitiate.
param(
    [string]$ServiceName = "MongoDB",
    [string]$ReplSetName = "rs0"
)
$ErrorActionPreference = "Stop"

$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (-not $principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
    throw "Can mo PowerShell bang quyen Administrator."
}

$svc = Get-CimInstance Win32_Service -Filter "Name='$ServiceName'"
if (-not $svc) { throw "Khong thay service $ServiceName." }
if ($svc.PathName -notmatch '--config\s+"?([^"]+?\.cfg)"?(\s|$)') { throw "Service khong dung file --config: $($svc.PathName)" }
$cfg = $Matches[1]
Write-Host "File cau hinh: $cfg"

$text = Get-Content -Raw -Path $cfg
if ($text -match '(?m)^replication:\s*$') {
    Write-Host "Da co muc replication, giu nguyen."
} else {
    Copy-Item $cfg "$cfg.bak" -Force
    $text = $text -replace '(?m)^#replication:\s*$', "replication:`r`n  replSetName: $ReplSetName"
    if ($text -notmatch '(?m)^replication:\s*$') {
        $text = $text.TrimEnd() + "`r`n`r`nreplication:`r`n  replSetName: $ReplSetName`r`n"
    }
    Set-Content -Path $cfg -Value $text -Encoding ascii
    Write-Host "Da them replication.replSetName=$ReplSetName (ban cu: $cfg.bak)"
}

Restart-Service -Name $ServiceName
Write-Host "Da khoi dong lai $ServiceName. Dat MONGODB_INIT_REPLSET=true trong .env roi chay backend."
