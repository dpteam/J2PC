@echo off
rem ---------------------------------------------------------------------------
rem Behavioural equivalence check for Windows.
rem
rem Builds the synthetic suite, runs the Verifier against the ORIGINAL classes,
rem recompiles the suite with J2PC, runs the same Verifier against the
rem RECOMPILED classes, and diffs the two outputs. A difference means the
rem translation changed behaviour.
rem
rem Usage: tools\verify.cmd
rem ---------------------------------------------------------------------------
setlocal

set "ROOT=%~dp0.."
cd /d "%ROOT%"

set "JAR=dist\j2pc.jar"
set "WORK=build\verify"
set "CLASSES=synthetic.Arithmetic,synthetic.ControlFlow,synthetic.Exceptions,synthetic.Objects,synthetic.Modern,synthetic.Hierarchy"

if not exist "%JAR%" (
    call build.cmd
    if errorlevel 1 exit /b 1
)

echo [1/5] Compiling the synthetic suite...
if exist "%WORK%" rmdir /s /q "%WORK%"
mkdir "%WORK%\original" 2>nul
mkdir "%WORK%\out" 2>nul
mkdir "%WORK%\gen" 2>nul

dir /s /b src\test\*.java > "%WORK%\sources.txt"
javac -encoding UTF-8 -nowarn -cp "%JAR%" -d "%WORK%\original" @"%WORK%\sources.txt"
if errorlevel 1 goto :fail

echo [2/5] Packaging and recompiling...
pushd "%WORK%\original"
jar cf "..\synthetic.jar" .
popd
java -jar "%JAR%" -q -o "%WORK%\out" "%WORK%\synthetic.jar"
if errorlevel 1 goto :fail

echo [3/5] Compiling the generated sources...
dir /s /b "%WORK%\out\*.java" > "%WORK%\gen.txt"
javac -encoding UTF-8 -nowarn -cp "%JAR%" -d "%WORK%\gen" @"%WORK%\gen.txt"
if errorlevel 1 goto :fail

echo [4/5] Running the verifier against both versions...
rem The test classes must come FIRST on the class path: if the runtime jar ever
rem contains a stale copy of them, it would silently shadow both versions and the
rem comparison would be meaningless.
java -cp "%WORK%\original;%JAR%" j2pc.tools.Verifier --classes=%CLASSES% > "%WORK%\original.txt" 2>&1
java -cp "%WORK%\gen;%JAR%" j2pc.tools.Verifier --classes=%CLASSES% > "%WORK%\recompiled.txt" 2>&1

echo [5/5] Comparing...
findstr /c:"Could not find or load main class" "%WORK%\original.txt" >nul 2>&1
if not errorlevel 1 (
    echo VERIFIER DID NOT RUN - see "%WORK%\original.txt"
    exit /b 1
)
rem A comparison of two empty outputs would trivially "pass", so require a
rem meaningful number of results before trusting the diff.
for /f %%C in ('findstr /r /c:"  .*= " "%WORK%\original.txt" ^| find /c /v ""') do set RESULTS=%%C
if not defined RESULTS set RESULTS=0
if %RESULTS% LSS 60 (
    echo TOO FEW RESULTS ^(%RESULTS%^) - the verifier did not exercise the suite
    exit /b 1
)
findstr /c:"<not loadable" /c:"<cannot instantiate" "%WORK%\original.txt" >nul 2>&1
if not errorlevel 1 (
    echo SOME CLASSES WERE NOT LOADABLE - see "%WORK%\original.txt"
    exit /b 1
)
fc /n "%WORK%\original.txt" "%WORK%\recompiled.txt" > "%WORK%\diff.txt" 2>&1
if errorlevel 1 (
    echo.
    echo DIFFERENCES FOUND:
    type "%WORK%\diff.txt"
    exit /b 1
)

echo.
echo Identical behaviour.
exit /b 0

:fail
echo.
echo VERIFY FAILED
exit /b 1
