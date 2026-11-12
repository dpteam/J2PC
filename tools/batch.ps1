# Batch recompiler.
#
# Runs j2pc over every JAR in one or more folders, optionally compiles the
# generated Java, and writes one merged report.
#
# Usage:
#   powershell -File tools\batch.ps1 -Compile
#   powershell -File tools\batch.ps1 -Compile -Source 'D:\j2me\DPT','D:\j2me\GoodJ2ME'
#
param(
    [string[]]$Source = @('E:\pub_e\Mobile\J2ME\DPT'),
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

# Collect the work list first, so the total is known up front.
$work = @()
foreach ($folder in $Source) {
    if (-not (Test-Path $folder)) {
        Write-Host "skipping missing folder: $folder"
        continue
    }
    foreach ($f in (Get-ChildItem -Path $folder -Recurse -File -Filter *.jar)) {
        $work += [pscustomobject]@{ Folder = $folder; File = $f }
    }
}
$work = $work | Sort-Object { $_.File.Name }

$report = @()
$i = 0
$usedNames = @{}

foreach ($item in $work) {
    $i++
    $f = $item.File
    # Names must stay unique across folders, so collisions get a counter.
    $safe = ($f.BaseName -replace '[^\w\.\-]', '_')
    if ($usedNames.ContainsKey($safe)) {
        $usedNames[$safe] = $usedNames[$safe] + 1
        $safe = $safe + '_' + $usedNames[$safe]
    } else {
        $usedNames[$safe] = 1
    }

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
    $errorCount = 0
    if ($Compile -and $genFiles.Count -gt 0) {
        $genDir = Join-Path $outRoot ("_gen\" + $safe)
        if (Test-Path $genDir) { Remove-Item -Recurse -Force $genDir }
        New-Item -ItemType Directory -Path $genDir -Force | Out-Null
        $list = Join-Path $outRoot ("_gen\" + $safe + '.txt')
        # javac rejects an argument file that starts with a BOM.
        $lines = $genFiles | ForEach-Object { $_.FullName }
        [System.IO.File]::WriteAllLines($list, $lines, (New-Object System.Text.UTF8Encoding($false)))
        # -Width keeps javac's long messages on one line, so the consolidator can
        # group them; the default wraps at the console width and shreds them.
        $jlog = & javac -encoding UTF-8 -nowarn -cp $jar -d $genDir "@$list" 2>&1 | Out-String -Width 4096
        $compileOk = ($LASTEXITCODE -eq 0)
        $errorCount = ([regex]::Matches($jlog, 'error:')).Count
        if (-not $compileOk) {
            $first = ($jlog -split "`n" | Select-String 'error:' | Select-Object -First 1)
            if ($first) { $compileErr = $first.ToString().Trim() }
            # Keep the full log so the consolidator can classify it later.
            $jlog | Set-Content -Path (Join-Path $outRoot ("_gen\" + $safe + '.log')) -Encoding UTF8
        }
    }

    $report += [pscustomobject]@{
        Folder = $item.Folder
        Name = $f.Name
        Id = $safe
        Classes = $classes
        Methods = $methods
        FailedMethods = $failedMethods
        Seconds = [math]::Round($sw.Elapsed.TotalSeconds, 2)
        Exit = $code
        Compiles = $compileOk
        Errors = $errorCount
        Error = $compileErr
    }

    $status = if ($code -ne 0) { 'FAIL' } elseif ($Compile -and $compileOk -eq $false) { 'NOCOMPILE' } else { 'ok' }
    Write-Host ("[{0,3}/{1}] {2,-10} {3,-46} c={4,-4} m={5,-5} e={6,-4} {7}s" -f `
        $i, $work.Count, $status, $f.Name.Substring(0, [Math]::Min(46, $f.Name.Length)), `
        $classes, $methods, $errorCount, [math]::Round($sw.Elapsed.TotalSeconds, 1))
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
Write-Host "Next: powershell -File tools\errors.ps1 -Batch $Out"
