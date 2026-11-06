$ErrorActionPreference='Stop'
Add-Type -AssemblyName System.Drawing
$repo=Split-Path -Parent $PSScriptRoot
$folder=Join-Path $repo 'docs/benchmark'
$results=@(1000,3000,5000,7000 | ForEach-Object { Get-Content (Join-Path $folder "$_/result.json") -Raw -Encoding UTF8 | ConvertFrom-Json })
$charts=@(
    @{file='sales-time.png';title='Bán chạy: thời gian truy vấn theo số hóa đơn';field='medianTotalMs';reports=@('SALES');unit='ms';scale=1},
    @{file='cash-time.png';title='Dòng tiền: thời gian truy vấn theo số hóa đơn';field='medianTotalMs';reports=@('CASH_FLOW');unit='ms';scale=1},
    @{file='fragment-memory.png';title='Đỉnh bộ nhớ fragment lớn nhất trong Profile';field='medianMaxFragmentPeakBytes';reports=@('SALES','CASH_FLOW');unit='MiB';scale=1048576}
)
foreach($chart in $charts){
    $bitmap=New-Object System.Drawing.Bitmap 1400,850
    $g=[System.Drawing.Graphics]::FromImage($bitmap)
    $g.SmoothingMode=[System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.TextRenderingHint=[System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit
    $g.Clear([System.Drawing.Color]::White)
    $font=New-Object System.Drawing.Font 'Arial',15
    $titleFont=New-Object System.Drawing.Font 'Arial',23,([System.Drawing.FontStyle]::Bold)
    $small=New-Object System.Drawing.Font 'Arial',12
    $muted=New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml('#64748b'))
    $grid=New-Object System.Drawing.Pen ([System.Drawing.ColorTranslator]::FromHtml('#e2e8f0')),1
    $colors=@('#2563eb','#0891b2')
    $g.DrawString($chart.title,$titleFont,[System.Drawing.Brushes]::Black,40,30)
    $g.DrawString('Các lượt thành công sau warm-up · 30/09–29/10/2026 · xem tỷ lệ đạt trong bảng',$small,$muted,42,85)
    $values=@(foreach($r in $results){foreach($name in $chart.reports){[double]$r.reports.$name.($chart.field)/$chart.scale}})
    $max=($values | Measure-Object -Maximum).Maximum * 1.18
    if($max -le 0){$max=1}
    for($tick=0;$tick -le 4;$tick++){
        $y=690-$tick*125
        $g.DrawLine($grid,140,$y,1330,$y)
        $g.DrawString(('{0:N0} {1}' -f ($max*$tick/4),$chart.unit),$small,$muted,30,($y-8))
    }
    for($i=0;$i -lt $results.Count;$i++){
        $center=285+$i*290
        for($series=0;$series -lt $chart.reports.Count;$series++){
            $name=$chart.reports[$series]
            if($null -eq $results[$i].reports.$name.($chart.field)){
                $g.DrawString('Lỗi truy vấn',$small,[System.Drawing.Brushes]::DarkRed,($center-70),665)
                continue
            }
            $value=[double]$results[$i].reports.$name.($chart.field)/$chart.scale
            $height=500*$value/$max
            $x=$center-55+$series*60
            $brush=New-Object System.Drawing.SolidBrush ([System.Drawing.ColorTranslator]::FromHtml($colors[$series]))
            $g.FillRectangle($brush,[single]$x,[single](690-$height),52,[single]$height)
            $g.DrawString(('{0:N1}' -f $value),$small,[System.Drawing.Brushes]::Black,[single]($x-4),[single](661-$height))
            $brush.Dispose()
        }
        $g.DrawString(('{0:N0} hóa đơn' -f $results[$i].orders),$font,[System.Drawing.Brushes]::Black,($center-75),715)
        if($chart.reports.Count -eq 1){
            $passed=$results[$i].reports.($chart.reports[0]).successfulMeasuredRuns
            $g.DrawString(("$passed/3 lượt đạt"),$small,$muted,($center-60),750)
        }
    }
    $legend=if($chart.reports.Count -gt 1){'Xanh dương: bán chạy · Xanh lam: dòng tiền. Đây không phải tổng RAM của Drill/container.'}else{'Thời gian trong Drill Profile; không gồm thời gian dựng giao diện. Trục bắt đầu từ 0.'}
    $g.DrawString($legend,$small,$muted,42,785)
    $bitmap.Save((Join-Path $folder $chart.file),[System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose();$bitmap.Dispose();$font.Dispose();$titleFont.Dispose();$small.Dispose();$muted.Dispose();$grid.Dispose()
}
Write-Output 'Rendered three comparison charts from actual benchmark results.'
