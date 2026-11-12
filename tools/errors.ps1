# Aggregates javac errors from the batch run into a taxonomy.
# Usage: powershell -File tools\errors.ps1
param(
    [string]$Batch = 'build\batch'
)

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$report = Join-Path $Batch 'report.csv'
if (-not (Test-Path $report)) {
    Write-Host "no report at $report"
    exit 1
}

$rows = Import-Csv $report | Where-Object { $_.Compiles -eq 'False' }
$kinds = @{}
$symbols = @{}
$packages = @{}
$total = 0

foreach ($r in $rows) {
    $name = $r.Name -replace '\.jar$', ''
    $list = Join-Path $Batch "_gen\$name.txt"
    if (-not (Test-Path $list)) { continue }

    $out = & javac -encoding UTF-8 -nowarn -cp dist\j2pc.jar -d build\_errtmp "@$list" 2>&1 | Out-String

    # error: <text>
    foreach ($m in [regex]::Matches($out, 'error: ([^\r\n]+)')) {
        $text = $m.Groups[1].Value.Trim()
        $kind = $text
        if ($kind -match '^cannot find symbol') { $kind = 'cannot find symbol' }
        if ($kind -match 'incompatible types') { $kind = 'incompatible types' }
        if ($kind -match '^method .* cannot be applied') { $kind = 'method not applicable' }
        if ($kind -match '^no suitable method') { $kind = 'method not applicable' }
        $kinds[$kind] = [int]$kinds[$kind] + 1
        $total++
    }

    # symbol:  <kind> <name>  /  location: class X
    foreach ($m in [regex]::Matches($out, 'symbol:\s+(?:(\w+)\s+)?([\w\.\$]+)(?:\([^\)]*\))?\s*\r?\n\s*location:\s+([^\r\n]+)')) {
        $sym = "$($m.Groups[1].Value) $($m.Groups[2].Value) @ $($m.Groups[3].Value.Trim())"
        $symbols[$sym] = [int]$symbols[$sym] + 1
    }

    foreach ($m in [regex]::Matches($out, 'package ([\w\.]+) does not exist')) {
        $packages[$m.Groups[1].Value] = [int]$packages[$m.Groups[1].Value] + 1
    }
}

Write-Host "=== total errors: $total across $($rows.Count) jars ==="
Write-Host ''
Write-Host '--- error kinds ---'
$kinds.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 15 |
    ForEach-Object { Write-Host ("{0,6}  {1}" -f $_.Value, $_.Name) }

Write-Host ''
Write-Host '--- missing packages ---'
$packages.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 15 |
    ForEach-Object { Write-Host ("{0,6}  {1}" -f $_.Value, $_.Name) }

Write-Host ''
Write-Host '--- missing symbols ---'
$symbols.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 40 |
    ForEach-Object { Write-Host ("{0,6}  {1}" -f $_.Value, $_.Name) }
