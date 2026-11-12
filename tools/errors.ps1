# Error consolidator.
#
# Reads a batch report plus the javac logs it left behind and answers one
# question: which *kinds* of failure are most common, and how many applications
# does each kind affect? That is what makes a fix worth doing globally.
#
# Usage:
#   powershell -File tools\errors.ps1
#   powershell -File tools\errors.ps1 -Batch build\batch -Top 25
#
param(
    [string]$Batch = 'build\batch',
    [int]$Top = 20,
    # Group by normalised message instead of by exact message text.
    [switch]$Exact
)

$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$reportPath = Join-Path $Batch 'report.csv'
if (-not (Test-Path $reportPath)) {
    Write-Host "no report at $reportPath - run tools\batch.ps1 -Compile first"
    exit 1
}

$rows = Import-Csv $reportPath
$failing = $rows | Where-Object { $_.Compiles -eq 'False' }
$genDir = Join-Path $Batch '_gen'

# ---------------------------------------------------------------- collectors

$kinds = @{}          # normalised message -> count
$kindJars = @{}       # normalised message -> set of jar ids
$kindExample = @{}    # normalised message -> example line
$packages = @{}       # missing package -> count
$symbols = @{}        # symbol + location -> count

$totalErrors = 0

function Normalise([string]$text) {
    $t = $text.Trim()
    # Drop the payload of a message so identical causes collapse together.
    $t = $t -replace '\s+', ' '
    $t = $t -replace "'[^']*'", "'…'"
    $t = $t -replace '\b[A-Za-z_][\w\.]*\.java\b', '<file>'
    $t = $t -replace '\b\d+\b', 'N'
    return $t
}

foreach ($r in $failing) {
    $id = if ($r.Id) { $r.Id } else { ($r.Name -replace '\.jar$', '') }
    $logPath = Join-Path $genDir ($id + '.log')
    if (-not (Test-Path $logPath)) { continue }

    $log = Get-Content $logPath -Raw
    if (-not $log) { continue }

    $totalErrors += ([regex]::Matches($log, 'error:')).Count

    foreach ($m in [regex]::Matches($log, 'error: ([^\r\n]+)')) {
        $text = $m.Groups[1].Value.Trim()
        $key = if ($Exact) { $text } else { Normalise $text }
        $kinds[$key] = [int]$kinds[$key] + 1
        if (-not $kindJars.ContainsKey($key)) { $kindJars[$key] = New-Object System.Collections.Generic.HashSet[string] }
        [void]$kindJars[$key].Add($id)
        if (-not $kindExample.ContainsKey($key)) { $kindExample[$key] = $text }
    }

    foreach ($m in [regex]::Matches($log, 'package ([\w\.]+) does not exist')) {
        $p = $m.Groups[1].Value
        $packages[$p] = [int]$packages[$p] + 1
    }

    foreach ($m in [regex]::Matches($log, 'symbol:\s+(?:(\w+)\s+)?([\w\.\$]+)(?:\([^\)]*\))?\s*\r?\n\s*location:\s+([^\r\n]+)')) {
        $sym = "$($m.Groups[1].Value) $($m.Groups[2].Value) @ $($m.Groups[3].Value.Trim())"
        $symbols[$sym] = [int]$symbols[$sym] + 1
    }
}

# ------------------------------------------------------------------- output

Write-Host "=== $($rows.Count) applications, $($failing.Count) fail to compile, $totalErrors javac errors ==="
Write-Host ''

Write-Host "--- error kinds (grouped by cause, top $Top) ---"
Write-Host ("{0,7} {1,7}  {2}" -f 'errors', 'jars', 'message')
$ranked = $kinds.GetEnumerator() | Sort-Object `
    @{ Expression = { $kindJars[$_.Key].Count }; Descending = $true }, `
    @{ Expression = { $_.Value }; Descending = $true } |
    Select-Object -First $Top
foreach ($e in $ranked) {
    $jars = $kindJars[$e.Key].Count
    $ex = $kindExample[$e.Key]
    if ($ex.Length -gt 78) { $ex = $ex.Substring(0, 75) + '...' }
    Write-Host ("{0,7} {1,7}  {2}" -f $e.Value, $jars, $ex)
}

if ($packages.Count -gt 0) {
    Write-Host ''
    Write-Host '--- missing packages ---'
    $packages.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 12 |
        ForEach-Object { Write-Host ("{0,7}  {1}" -f $_.Value, $_.Name) }
}

if ($symbols.Count -gt 0) {
    Write-Host ''
    Write-Host '--- missing symbols ---'
    $symbols.GetEnumerator() | Sort-Object Value -Descending | Select-Object -First 12 |
        ForEach-Object { Write-Host ("{0,7}  {1}" -f $_.Value, $_.Name) }
}

# Machine readable summary, handy for tracking progress between runs.
$summary = Join-Path $Batch 'errors-summary.csv'
$kinds.GetEnumerator() |
    ForEach-Object {
        [pscustomobject]@{
            Errors = $_.Value
            Jars = $kindJars[$_.Key].Count
            Kind = $kindExample[$_.Key]
        }
    } |
    Sort-Object Errors -Descending |
    Export-Csv -Path $summary -NoTypeInformation -Encoding UTF8
Write-Host ''
Write-Host "Summary: $summary"
