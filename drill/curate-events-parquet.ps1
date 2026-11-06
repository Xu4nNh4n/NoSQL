param(
    [string]$Date = ([datetime]::UtcNow.ToString('yyyy-MM-dd')),
    [string]$DrillUrl = '',
    [ValidateSet('OPERATIONS', 'SIMULATED')][string]$Dataset = 'OPERATIONS',
    [string]$DatasetId = '',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$parsedDate = [datetime]::ParseExact($Date, 'yyyy-MM-dd', $null)
$tableDate = $parsedDate.ToString('yyyy_MM_dd')
$sourceFile = ('analytics.cassandra.`events-DATE.json`' -replace 'DATE', $Date)
$projectRoot = Split-Path $PSScriptRoot -Parent
$settings = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $projectRoot '.env')) {
    if ($line -match '^\s*([A-Z_]+)=(.*)$') { $settings[$matches[1]] = $matches[2].Trim() }
}
if (-not $DrillUrl) { $DrillUrl = if ($env:DRILL_URL) { $env:DRILL_URL } elseif ($settings['DRILL_URL']) { $settings['DRILL_URL'] } else { 'http://localhost:8048' } }
if (-not $DatasetId) { $DatasetId = if ($settings['SIMULATION_DATASET_ID']) { $settings['SIMULATION_DATASET_ID'] } else { 'sim-421014-100000-2026-10-14' } }
if ($DatasetId -notmatch '^[a-zA-Z0-9_-]+$') { throw 'SIMULATION_DATASET_ID không hợp lệ' }
$workspace = 'analytics.curated'
if ($Dataset -eq 'SIMULATED') {
    $sourceFile = 'analytics.simulation.`' + $DatasetId + '/cassandra/events-' + $Date + '.json`'
    $sourcePath = Join-Path $projectRoot "analytics/simulation/$DatasetId/cassandra/events-$Date.json"
    $curatedPath = Join-Path $projectRoot "analytics/simulation/$DatasetId/curated"
    $workspace = 'analytics.simulation_curated'
} else {
    $sourcePath = Join-Path $projectRoot "analytics/cassandra/events-$Date.json"
    $curatedPath = Join-Path $projectRoot 'analytics/curated'
}
if (-not (Test-Path -LiteralPath $sourcePath -PathType Leaf)) { throw "Missing JSON export for date $Date" }
if (-not (Test-Path -LiteralPath $curatedPath -PathType Container)) {
    New-Item -ItemType Directory -Path $curatedPath -Force | Out-Null
}
$sourceHash = (Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant()
# Build a new version; publish the manifest only after CTAS completes. Existing readers keep their table.
$tableName = "events_${tableDate}_$([guid]::NewGuid().ToString('N'))"
$targetTable = "$workspace.$tableName"
$manifestPath = Join-Path $curatedPath "events_$tableDate.ready.json"
if (-not $Force -and (Test-Path -LiteralPath $manifestPath)) {
    $existing = Get-Content -Raw -LiteralPath $manifestPath | ConvertFrom-Json
    if ($existing.sourceSha256 -eq $sourceHash -and $existing.table -match "^events_${tableDate}_[a-f0-9]{32}$") {
        $existingPath = Join-Path $curatedPath $existing.table
        if (Get-ChildItem -LiteralPath $existingPath -Filter '*.parquet' -ErrorAction SilentlyContinue) {
            [pscustomobject]@{ Date=$Date; Dataset=$Dataset; Target="$workspace.$($existing.table)"; Skipped=$true } | ConvertTo-Json
            return
        }
    }
}

function Invoke-DrillSql([string]$Sql, [int]$AutoLimit = 100) {
    $body = @{ queryType = 'SQL'; query = $Sql; autoLimit = $AutoLimit } |
        ConvertTo-Json -Depth 5
    $result = Invoke-RestMethod -Uri "$DrillUrl/query.json" -Method Post `
        -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
    if ($result.queryState -ne 'COMPLETED' -or $result.errorMessage) {
        $errorText = if ($result.errorMessage) { $result.errorMessage } else { $result | ConvertTo-Json -Depth 5 }
        throw "Drill query failed: $errorText"
    }
    return $result
}

# CTAS needs a writable workspace and deterministic compression.
Invoke-DrillSql "ALTER SYSTEM SET store.parquet.compression = 'snappy'" 0 | Out-Null

$createSql = @'
CREATE TABLE TARGET_TABLE AS
SELECT CAST(event_date AS DATE) AS event_date,
       CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time,
       CAST(event_id AS VARCHAR) AS event_id,
       CAST(event_type AS VARCHAR) AS event_type,
       CAST(order_id AS BIGINT) AS order_id,
       CAST(product_sku AS VARCHAR) AS product_sku,
       CAST(quantity AS INT) AS quantity,
       CAST(amount AS DECIMAL(18, 2)) AS amount,
       CAST(payment_method AS VARCHAR) AS payment_method,
       CAST(status AS VARCHAR) AS status,
       CAST(`metadata` AS VARCHAR) AS `metadata`
FROM (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM SOURCE_FILE
) raw
WHERE event_rank = 1 AND event_id IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
  AND event_type IS NOT NULL
'@
$createSql = $createSql.Replace('TARGET_TABLE', $targetTable).Replace('SOURCE_FILE', $sourceFile)
$created = Invoke-DrillSql $createSql 0

if ((Get-FileHash -LiteralPath $sourcePath -Algorithm SHA256).Hash.ToLowerInvariant() -ne $sourceHash) {
    throw 'JSON changed during conversion; Parquet was not published. Run this script again for the latest export.'
}
$tablePath = Join-Path $curatedPath $tableName
if (-not (Test-Path -LiteralPath $tablePath -PathType Container)) {
    throw 'Parquet table not found on host; check mount ./analytics:/analytics and analytics.curated workspace.'
}
$manifest = @{
    table = $tableName
    sourceSha256 = $sourceHash
    date = $Date
    builtAt = [datetime]::UtcNow.ToString('o')
    format = 'PARQUET'
    compression = 'SNAPPY'
    dataset = $Dataset
    queryId = $created.queryId
    source = $sourceFile
}
$temporaryManifest = Join-Path $curatedPath "$tableName.manifest.tmp"
try {
    [IO.File]::WriteAllText($temporaryManifest, ($manifest | ConvertTo-Json), [Text.UTF8Encoding]::new($false))
    Move-Item -LiteralPath $temporaryManifest -Destination $manifestPath -Force
} finally {
    if (Test-Path -LiteralPath $temporaryManifest) { Remove-Item -LiteralPath $temporaryManifest }
}

[pscustomobject]@{
    Source = $sourceFile
    Target = $targetTable
    Format = 'Parquet/Snappy'
    Manifest = $manifestPath
    SourceSha256 = $sourceHash
} | ConvertTo-Json -Depth 8
