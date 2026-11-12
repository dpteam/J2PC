# Batch recompiler: runs j2pc over every JAR in a folder and records results.
# Usage: powershell -File tools\batch.ps1 [-Source <folder>] [-Out <folder>] [-Compile]
param(
    [string]$Source = 'E:\pub_e\Mobile\J2ME\DPT',
    [string]$Out = 'build\batch',
    [switch]$Compile
)

$ErrorActionPreference = 'Continue'
$root = Split-Path -Parent $PSScriptRoot
$jar = Join-Path $root 'dist\j2pc.jar'

if (-not (Test-Path $jar)) {
    Write-Host 'dist\j2pc.jar not found, run build.cmd first'
    exit 1
}

$outRoot = Join-Path $root $Out
if (-not (Test-Path $outRoot)) { New-Item -ItemType Directory -Path $outRoot -Force | Out-Null }

$report = @()
$jars = Get-ChildItem -Path $Source -Recurse -File -Filter *.jar | Sort-Object Name
$i = 0

foreach ($f in $jars) {
    $i++
    $safe = ($f.BaseName -replace '[^\w\.\-]', '_')
    $target = Join-Path $outRoot $safe
    if (Test-Path $target) { Remove-Item -Recurse -Force $target }

    $sw = [System.Diagnostics.Stopwatch]::StartNew()
    $log = & java -jar $jar -q -o $target $f.FullName 2>&1 | Out-String
    $code = $LASTEXITCODE
    $sw.Stop()

    $classes = 0
    $methods = 0
    $failedMethods = 0
    $genFiles = @()
    if (Test-Path $target) {
        $genFiles = Get-ChildItem -Path $target -Recurse -File -Filter *.java
        $classes = $genFiles.Count
        $rep = Join-Path $target 'j2pc-report.txt'
        if (Test-Path $rep) {
            $text = Get-Content $rep -Raw
            if ($text -match 'Methods\s+:\s+(\d+)') { $methods = [int]$Matches[1] }
            if ($text -match 'Unrecompiled:\s+(\d+)') { $failedMethods = [int]$Matches[1] }
        }
    }

    $compileOk = $null
    $compileErr = ''
    if ($Compile -and $genFiles.Count -gt 0) {
        $genDir = Join-Path $outRoot ("_gen\" + $safe)
        if (Test-Path $genDir) { Remove-Item -Recurse -Force $genDir }
        New-Item -ItemType Directory -Path $genDir -Force | Out-Null
        $list = Join-Path $outRoot ("_gen\" + $safe + '.txt')
        # javac rejects an argument file that starts with a BOM, so write it without one.
        $lines = $genFiles | ForEach-Object { $_.FullName }
        [System.IO.File]::WriteAllLines($list, $lines, (New-Object System.Text.UTF8Encoding($false)))
        $jlog = & javac -encoding UTF-8 -nowarn -cp $jar -d $genDir "@$list" 2>&1 | Out-String
        $compileOk = ($LASTEXITCODE -eq 0)
        if (-not $compileOk) {
            $compileErr = ($jlog -split "`n" | Select-String 'error:' | Select-Object -First 1)
            if ($compileErr) { $compileErr = $compileErr.ToString().Trim() }
        }
    }

    $report += [pscustomobject]@{
        Name = $f.Name
        Classes = $classes
        Methods = $methods
        FailedMethods = $failedMethods
        Seconds = [math]::Round($sw.Elapsed.TotalSeconds, 2)
        Exit = $code
        Compiles = $compileOk
        Error = $compileErr
    }

    $status = if ($code -ne 0) { 'FAIL' } elseif ($Compile -and $compileOk -eq $false) { 'NOCOMPILE' } else { 'ok' }
    Write-Host ("[{0,3}/{1}] {2,-12} {3,-52} c={4,-4} m={5,-5} f={6,-3} {7}s" -f `
        $i, $jars.Count, $status, $f.Name.Substring(0, [Math]::Min(52, $f.Name.Length)), `
        $classes, $methods, $failedMethods, [math]::Round($sw.Elapsed.TotalSeconds, 1))
}

$csv = Join-Path $outRoot 'report.csv'
$report | Export-Csv -Path $csv -NoTypeInformation -Encoding UTF8

Write-Host ''
Write-Host ("Total: {0}  ok: {1}  recompile-fail: {2}  compile-fail: {3}" -f `
    $report.Count,
    ($report | Where-Object { $_.Exit -eq 0 -and $_.Compiles -ne $false }).Count,
    ($report | Where-Object { $_.Exit -ne 0 }).Count,
    ($report | Where-Object { $_.Compiles -eq $false }).Count)
Write-Host "Report: $csv"
