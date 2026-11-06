param([string]$DrillUrl = 'http://localhost:8047')

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$envPath = Join-Path $projectRoot '.env'
$settings = @{}
foreach ($line in $(if (Test-Path -LiteralPath $envPath) { Get-Content -LiteralPath $envPath })) {
    if ($line -match '^\s*([A-Z_]+)=(.*)$') {
        $settings[$matches[1]] = $matches[2].Trim()
    }
}
foreach ($key in @('DB_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
    $override = [Environment]::GetEnvironmentVariable($key)
    if ($null -ne $override) { $settings[$key] = $override }
    if (-not $settings.ContainsKey($key) -or ($key -ne 'DB_PASSWORD' -and -not $settings[$key])) { throw "Thiếu cấu hình $key" }
}

if (-not $PSBoundParameters.ContainsKey('DrillUrl')) {
    $DrillUrl = if ($env:DRILL_URL) { $env:DRILL_URL } elseif ($settings['DRILL_URL']) { $settings['DRILL_URL'] } else { 'http://localhost:8048' }
}
$datasetId = if ($settings['SIMULATION_DATASET_ID']) { $settings['SIMULATION_DATASET_ID'] } else { 'sim-421014-100000-2026-10-14' }
if ($datasetId -notmatch '^[a-zA-Z0-9_-]+$') { throw 'SIMULATION_DATASET_ID không hợp lệ' }
foreach ($relative in @('analytics/cassandra', 'analytics/curated', "analytics/simulation/$datasetId/curated")) {
    $path = Join-Path $projectRoot $relative
    if (-not (Test-Path -LiteralPath $path -PathType Container)) { New-Item -ItemType Directory -Path $path -Force | Out-Null }
}
# These addresses are resolved inside the Drill Docker network.
$cassandraHost = if ($env:DRILL_CASSANDRA_HOST) { $env:DRILL_CASSANDRA_HOST } elseif ($settings['DRILL_CASSANDRA_HOST']) { $settings['DRILL_CASSANDRA_HOST'] } else { 'cassandra' }
$cassandraPort = if ($env:DRILL_CASSANDRA_PORT) { $env:DRILL_CASSANDRA_PORT } elseif ($settings['DRILL_CASSANDRA_PORT']) { $settings['DRILL_CASSANDRA_PORT'] } else { '9042' }
if ($cassandraPort -notmatch '^\d+$' -or [int]$cassandraPort -lt 1 -or [int]$cassandraPort -gt 65535) { throw 'Invalid DRILL_CASSANDRA_PORT' }
$mysqlUrl = $settings['DB_URL'] -replace '//(localhost|127\.0\.0\.1):', '//host.docker.internal:'
if (-not $mysqlUrl.Contains('?')) {
    $mysqlUrl += '?useSSL=false&allowPublicKeyRetrieval=true'
}
# MySQL TIMESTAMP must be read and filtered in UTC, just like Astra event_date.
# Replace conflicting timezone parameters instead of adding duplicate URL options.
$urlParts = $mysqlUrl -split '\?', 2
$queryOptions = @($urlParts[1] -split '&' | Where-Object {
    $_ -and $_ -notmatch '^(connectionTimeZone|serverTimezone|forceConnectionTimeZoneToSession|preserveInstants)='
})
$queryOptions += @('connectionTimeZone=UTC', 'serverTimezone=UTC', 'forceConnectionTimeZoneToSession=true', 'preserveInstants=true')
$mysqlUrl = $urlParts[0] + '?' + ($queryOptions -join '&')
$plugins = @{
    mysql_source = @{
        type = 'jdbc'; enabled = $true; driver = 'com.mysql.cj.jdbc.Driver'
        url = $mysqlUrl; username = $settings['DB_USERNAME']; password = $settings['DB_PASSWORD']
    }
    cassandra_source = @{
        type = 'cassandra'; enabled = $true; host = $cassandraHost; port = [int]$cassandraPort
        username = $null; password = $null
    }
    analytics = @{
        type = 'file'; enabled = $true; connection = 'file:///'
        workspaces = @{
            pos_payments = @{
                location = '/analytics/pos-payments'; writable = $false
                defaultInputFormat = 'json'; allowAccessOutsideWorkspace = $false
            }
            cassandra = @{
                location = '/analytics/cassandra'; writable = $false
                defaultInputFormat = 'json'; allowAccessOutsideWorkspace = $false
            }
            curated = @{
                location = '/analytics/curated'; writable = $true
                defaultInputFormat = 'parquet'; allowAccessOutsideWorkspace = $false
            }
            simulation = @{
                location = '/analytics/simulation'; writable = $false
                defaultInputFormat = 'json'; allowAccessOutsideWorkspace = $false
            }
            simulation_curated = @{
                location = "/analytics/simulation/$datasetId/curated"; writable = $true
                defaultInputFormat = 'parquet'; allowAccessOutsideWorkspace = $false
            }
        }
        formats = @{
            json = @{ type = 'json'; extensions = @('json') }
            parquet = @{ type = 'parquet' }
        }
    }
}
foreach ($name in $plugins.Keys) {
    $body = @{ name = $name; config = $plugins[$name] } | ConvertTo-Json -Depth 10
    try {
        $result = Invoke-RestMethod -Uri "$DrillUrl/storage/$name.json" -Method Post `
            -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
        if ($result.result -ne 'success') { throw 'Storage update failed' }
        Write-Host "Đã cấu hình storage: $name"
    } catch {
        throw "Không cấu hình được $name. Kiểm tra Drill đang chạy ở $DrillUrl."
    }
}

# Keep hash-join buffer allocation within the local container's 1 GiB direct-memory budget.
foreach ($localOption in @(
    'ALTER SYSTEM SET `planner.width.max_per_node` = 1',
    'ALTER SYSTEM SET `exec.hashjoin.num_partitions` = 4'
)) {
    $localBody = @{ queryType = 'SQL'; query = $localOption } | ConvertTo-Json
    $localResult = Invoke-RestMethod -Uri "$DrillUrl/query.json" -Method Post `
        -ContentType 'application/json' -Body $localBody
    if ($localResult.queryState -ne 'COMPLETED' -or $localResult.rows[0].ok -ne $true) {
        throw "Không thiết lập được giới hạn bộ nhớ JOIN: $localOption"
    }
}
Write-Host 'Đã giới hạn song song và hash partition cho Drill local.'
