param(
    [ValidateSet('OPERATIONS', 'SIMULATED')][string]$Dataset = 'SIMULATED',
    [string]$DrillUrl = '',
    [switch]$Monthly
)

$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$settings = @{}
foreach ($line in Get-Content -LiteralPath (Join-Path $root '.env')) {
    if ($line -match '^\s*([A-Z_]+)=(.*)$') { $settings[$matches[1]] = $matches[2].Trim() }
}
if (-not $DrillUrl) { $DrillUrl = if ($settings['DRILL_URL']) { $settings['DRILL_URL'] } else { 'http://localhost:8048' } }
$datasetId = if ($settings['SIMULATION_DATASET_ID']) { $settings['SIMULATION_DATASET_ID'] } else { 'sim-421014-100000-2026-10-14' }
if ($datasetId -notmatch '^[a-zA-Z0-9_-]+$') { throw 'SIMULATION_DATASET_ID không hợp lệ' }
if ($Dataset -eq 'SIMULATED') {
    $source = Join-Path $root "analytics/simulation/$datasetId/cassandra"
    $curated = Join-Path $root "analytics/simulation/$datasetId/curated"
    $workspace = 'analytics.simulation_curated'
    $sourceReference = 'analytics.simulation.`' + $datasetId + '/cassandra/events-*.json`'
} else {
    $source = Join-Path $root 'analytics/cassandra'
    $curated = Join-Path $root 'analytics/curated'
    $workspace = 'analytics.curated'
    $sourceReference = 'analytics.cassandra.`events-*.json`'
}
$files = @(Get-ChildItem -LiteralPath $source -Filter 'events-*.json' | Where-Object Name -Match '^events-\d{4}-\d{2}-\d{2}\.json$' | Sort-Object Name)
if ($files.Count -eq 0) { throw 'Chưa có bản xuất Cassandra; chạy bước bootstrap/export trước' }
$results = @()
foreach ($file in $files) {
    $date = $file.BaseName.Substring(7)
    $raw = & (Join-Path $PSScriptRoot 'curate-events-parquet.ps1') -Date $date -Dataset $Dataset -DatasetId $datasetId -DrillUrl $DrillUrl | Out-String
    $results += ($raw | ConvertFrom-Json)
    if ($results.Count % 20 -eq 0) { Write-Host "Parquet $Dataset : $($results.Count)/$($files.Count) ngày" }
}

$monthlyResult = $null
if ($Monthly) {
    $hashes = @{}
    foreach ($file in $files) { $hashes[$file.Name] = (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant() }
    $table = 'events_monthly_' + [guid]::NewGuid().ToString('N')
    $target = $workspace + '.`' + $table + '`'
    $sql = @'
CREATE TABLE TARGET PARTITION BY (event_month) AS
SELECT CAST(event_date AS DATE) AS event_date,
       CAST(REPLACE(REPLACE(REPLACE(event_time, 'T', ' '), 'Z', ''), '+00:00', '') AS TIMESTAMP) AS event_time,
       CAST(event_id AS VARCHAR) AS event_id, CAST(event_type AS VARCHAR) AS event_type,
       CAST(order_id AS BIGINT) AS order_id, CAST(product_sku AS VARCHAR) AS product_sku,
       CAST(quantity AS INT) AS quantity, CAST(amount AS DECIMAL(18,2)) AS amount,
       CAST(payment_method AS VARCHAR) AS payment_method, CAST(status AS VARCHAR) AS status,
       CAST(`metadata` AS VARCHAR) AS `metadata`, SUBSTR(CAST(event_date AS VARCHAR), 1, 7) AS event_month
FROM (
    SELECT *, ROW_NUMBER() OVER (PARTITION BY event_id ORDER BY event_time DESC) AS event_rank
    FROM SOURCE
) raw
WHERE event_rank = 1 AND event_id IS NOT NULL AND event_type IS NOT NULL
  AND event_id <> '11111111-1111-1111-1111-111111111111'
'@
    $sql = $sql.Replace('TARGET', $target).Replace('SOURCE', $sourceReference)
    $body = @{ queryType='SQL'; query=$sql; autoLimit=0 } | ConvertTo-Json
    $answer = Invoke-RestMethod "$DrillUrl/query.json" -Method Post -ContentType 'application/json; charset=utf-8' -Body ([Text.Encoding]::UTF8.GetBytes($body))
    if ($answer.queryState -ne 'COMPLETED' -or $answer.errorMessage) { throw "Monthly Parquet chưa hoàn thành; queryId=$($answer.queryId)" }
    foreach ($file in $files) {
        if ((Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -ne $hashes[$file.Name]) { throw 'JSON thay đổi trong lúc CTAS; không công bố manifest tháng' }
    }
    $parquets = @(Get-ChildItem -LiteralPath (Join-Path $curated $table) -Recurse -Filter '*.parquet')
    if ($parquets.Count -eq 0) { throw 'CTAS chưa tạo được Parquet trên volume' }
    $monthlyResult = @{ table=$table; dataset=$Dataset; format='PARQUET'; compression='SNAPPY'; partitionBy='event_month';
        source=$sourceReference; sourceFiles=$hashes; builtAt=[datetime]::UtcNow.ToString('o'); queryId=$answer.queryId;
        parquetFiles=$parquets.Count; parquetBytes=($parquets | Measure-Object Length -Sum).Sum; execution=$answer.rows }
    $manifestPath = Join-Path $curated 'events_monthly.ready.json'
    $temporary = Join-Path $curated "$table.manifest.tmp"
    try {
        [IO.File]::WriteAllText($temporary, ($monthlyResult | ConvertTo-Json -Depth 8), [Text.UTF8Encoding]::new($false))
        Move-Item -LiteralPath $temporary -Destination $manifestPath -Force
    } finally { if (Test-Path -LiteralPath $temporary) { Remove-Item -LiteralPath $temporary } }
}
[pscustomobject]@{ Dataset=$Dataset; Days=$results.Count; Monthly=$monthlyResult; GeneratedAt=[datetime]::UtcNow.ToString('o') } | ConvertTo-Json -Depth 8
