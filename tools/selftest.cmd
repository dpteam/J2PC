@echo off
rem ---------------------------------------------------------------------------
rem Self test for Windows: builds a synthetic MIDlet suite, recompiles it with
rem J2PC and compiles the generated sources. No third-party JARs are involved,
rem so this runs on a clean checkout and is what CI executes.
rem
rem Usage: tools\selftest.cmd
rem ---------------------------------------------------------------------------
setlocal

set "ROOT=%~dp0.."
cd /d "%ROOT%"

set "JAR=dist\j2pc.jar"
set "WORK=build\selftest"

if not exist "%JAR%" (
    echo Building j2pc first...
    call build.cmd
    if errorlevel 1 exit /b 1
)

echo [1/5] Compiling the synthetic suite...
if exist "%WORK%" rmdir /s /q "%WORK%"
mkdir "%WORK%\classes" 2>nul
mkdir "%WORK%\out" 2>nul
mkdir "%WORK%\gen" 2>nul

dir /s /b src\test\*.java > "%WORK%\sources.txt"
javac -encoding UTF-8 -nowarn -cp "%JAR%" -d "%WORK%\classes" @"%WORK%\sources.txt"
if errorlevel 1 goto :fail

echo [2/5] Packaging it as a JAR...
pushd "%WORK%\classes"
jar cf "..\synthetic.jar" .
popd
copy /y src\test\synthetic.jad "%WORK%\synthetic.jad" >nul

echo [3/5] Recompiling...
java -jar "%JAR%" -o "%WORK%\out" "%WORK%\synthetic.jar" "%WORK%\synthetic.jad"
if errorlevel 1 goto :fail

echo [4/5] Compiling the generated sources...
dir /s /b "%WORK%\out\*.java" > "%WORK%\gen.txt"
javac -encoding UTF-8 -nowarn -cp "%JAR%" -d "%WORK%\gen" @"%WORK%\gen.txt"
if errorlevel 1 goto :fail

echo [5/5] Checking the launcher...
if not exist "%WORK%\gen\J2PCLauncher.class" (
    echo FAILED: J2PCLauncher was not generated
    goto :fail
)

echo.
echo Self test passed.
exit /b 0

:fail
echo.
echo SELF TEST FAILED
exit /b 1
