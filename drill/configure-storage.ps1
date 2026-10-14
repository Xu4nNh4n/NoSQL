param([string]$DrillUrl = 'http://localhost:8047')

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$envPath = Join-Path $projectRoot '.env'
$settings = @{}
foreach ($line in Get-Content -LiteralPath $envPath) {
    if ($line -match '^\s*([A-Z_]+)=(.*)$') {
        $settings[$matches[1]] = $matches[2].Trim()
    }
}
foreach ($key in @('MONGODB_URI', 'DB_URL', 'DB_USERNAME', 'DB_PASSWORD')) {
    $override = [Environment]::GetEnvironmentVariable($key)
    if ($override) { $settings[$key] = $override }
    if (-not $settings[$key]) { throw "Thiếu cấu hình $key" }
}

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
    mongo_source = @{
        type = 'mongo'; enabled = $true; connection = $settings['MONGODB_URI']
    }
    analytics = @{
        type = 'file'; enabled = $true; connection = 'file:///'
        workspaces = @{
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

# Drill 1.21's Mongo reader can infer MongoDB ObjectId as VARCHAR and then fail
# while materializing the BSON value. The BSON reader keeps ObjectId compatible
# with Drill's VARBINARY writer and is persisted at system level.
$optionBody = @{ queryType = 'SQL'; query = 'ALTER SYSTEM SET store.mongo.bson.record.reader = true' } |
    ConvertTo-Json -Depth 5
$optionResult = Invoke-RestMethod -Uri "$DrillUrl/query.json" -Method Post `
    -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($optionBody))
if ($optionResult.queryState -ne 'COMPLETED' -or $optionResult.rows[0].ok -ne $true) {
    throw 'Không bật được store.mongo.bson.record.reader trên Drill.'
}
Write-Host 'Đã bật tùy chọn tương thích BSON ObjectId cho MongoDB.'
