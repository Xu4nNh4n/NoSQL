$ErrorActionPreference = 'Stop'
$repo = Split-Path -Parent $PSScriptRoot
Set-Location $repo

function Check-Native { if ($LASTEXITCODE -ne 0) { throw "Command failed: $LASTEXITCODE" } }
function Wait-Backend {
    for ($attempt = 0; $attempt -lt 40; $attempt++) {
        try {
            $login = Invoke-RestMethod 'http://127.0.0.1:8080/api/auth/login' -Method Post -ContentType 'application/json' -Body (@{username='admin';password='123456'} | ConvertTo-Json) -TimeoutSec 5
            return @{Authorization="Bearer $($login.accessToken)"}
        } catch { Start-Sleep -Seconds 2 }
    }
    throw 'Backend did not become ready; check local startup log.'
}

foreach ($target in @(3000,5000,7000)) {
    if (Test-Path "docs/benchmark/$target/result.json") { Write-Output "Benchmark $target already captured; skipping."; continue }
    Write-Output "=== Preparing $target invoices ==="
    # Local backup excludes no tables; credentials are passed only through process environment.
    @'
from pathlib import Path
import subprocess,os,re,datetime
root=Path.cwd();cfg={}
for line in (root/'.env').read_text(encoding='utf-8-sig').splitlines():
 if '=' in line and not line.lstrip().startswith('#'):
  k,v=line.split('=',1);cfg[k.strip()]=v.strip().strip('"').strip("'")
password=cfg.get('DB_PASSWORD')
if password is None:password=re.search(r'\$\{DB_PASSWORD:([^}]*)\}',(root/'backend/src/main/resources/application.properties').read_text(encoding='utf-8')).group(1)
folder=root/'.tmp/benchmark-backups';folder.mkdir(parents=True,exist_ok=True)
file=folder/('before-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'.sql')
env=os.environ.copy();env['MYSQL_PWD']=password
r=subprocess.run([r'D:\MySQL\Server84\bin\mysqldump.exe','--host=127.0.0.1','--user='+cfg.get('DB_USERNAME','root'),'--single-transaction','--no-tablespaces','--set-gtid-purged=OFF','--result-file='+str(file),'supermarket_api'],env=env,capture_output=True)
if r.returncode:raise SystemExit('Backup failed: '+r.stderr.decode(errors='replace'))
print('Backup:',file.name,flush=True)
'@ | python -X utf8 -
    Check-Native
    python -X utf8 database/extend-pos-orders.py --target $target --apply
    Check-Native
    $apps = Get-CimInstance Win32_Process | Where-Object { $_.Name -eq 'java.exe' -and $_.CommandLine -match 'SuperMarketAPI-BigData|com.api.supermarket.SupermarketApplication' }
    foreach ($app in $apps) { Stop-Process -Id $app.ProcessId -ErrorAction Stop }
    python -X utf8 database/spread-pos-history.py --target $target --end-date 2026-10-29 --apply
    Check-Native
    $logFolder = Join-Path $env:USERPROFILE 'supermarket-run'
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    Start-Process cmd.exe -ArgumentList '/c mvnw.cmd "-Dmaven.test.skip=true" spring-boot:run' -WorkingDirectory (Join-Path $repo 'backend') -RedirectStandardOutput (Join-Path $logFolder "backend-benchmark-$target-$stamp.out.log") -RedirectStandardError (Join-Path $logFolder "backend-benchmark-$target-$stamp.err.log") -WindowStyle Hidden
    $headers = Wait-Backend
    $period = Get-Content ".tmp/spread-pos-history-$target/completed.json" -Raw | ConvertFrom-Json
    $day=[datetime]::Parse($period.from)
    $end=[datetime]::Parse($period.to)
    while ($day -le $end) {
        $date=$day.ToString('yyyy-MM-dd')
        $null=Invoke-RestMethod "http://127.0.0.1:8080/api/integrations/astra/events/export?date=$date" -Method Post -Headers $headers -TimeoutSec 120
        $day=$day.AddDays(1)
    }
    python -X utf8 database/benchmark-drill.py --orders $target
    Check-Native
    node drill/capture-benchmark.mjs $target
    Check-Native
    Write-Output "=== Captured $target invoices ==="
}
