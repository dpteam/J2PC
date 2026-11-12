@echo off
rem ---------------------------------------------------------------------------
rem Self test for Windows: builds a synthetic MIDlet suite, recompiles it with
rem J2PC, compiles the generated sources and packages them into a runnable JAR.
rem No third-party JARs are involved, so this runs on a clean checkout and is
rem what CI executes.
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

echo [1/6] Compiling the synthetic suite...
if exist "%WORK%" rmdir /s /q "%WORK%"
mkdir "%WORK%\classes" 2>nul
mkdir "%WORK%\out" 2>nul
mkdir "%WORK%\gen" 2>nul

dir /s /b src\test\*.java > "%WORK%\sources.txt"
javac -encoding UTF-8 -nowarn -cp "%JAR%" -d "%WORK%\classes" @"%WORK%\sources.txt"
if errorlevel 1 goto :fail

echo [2/6] Packaging it as a JAR...
pushd "%WORK%\classes"
jar cf "..\synthetic.jar" .
popd
copy /y src\test\synthetic.jad "%WORK%\synthetic.jad" >nul

echo [3/6] Recompiling...
java -jar "%JAR%" -o "%WORK%\out" "%WORK%\synthetic.jar" "%WORK%\synthetic.jad"
if errorlevel 1 goto :fail

echo [4/6] Compiling the generated sources...
dir /s /b "%WORK%\out\*.java" > "%WORK%\gen.txt"
javac -encoding UTF-8 -nowarn -cp "%JAR%" -d "%WORK%\gen" @"%WORK%\gen.txt"
if errorlevel 1 goto :fail

echo [5/6] Checking the launcher...
if not exist "%WORK%\gen\J2PCLauncher.class" (
    echo FAILED: J2PCLauncher was not generated
    goto :fail
)

echo [6/6] Packaging a runnable JAR...
java -jar "%JAR%" --build -o "%WORK%\pkg" "%WORK%\synthetic.jar" "%WORK%\synthetic.jad"
if errorlevel 1 goto :fail

set "APPJAR="
for %%f in ("%WORK%\pkg\*.jar") do set "APPJAR=%%~ff"
if not defined APPJAR (
    echo FAILED: no JAR was packaged
    goto :fail
)

rem A self-contained archive needs the entry point and the emulation runtime.
jar tf "%APPJAR%" | findstr /c:"J2PCLauncher.class" >nul
if errorlevel 1 (
    echo FAILED: the packaged JAR has no J2PCLauncher
    goto :fail
)
jar tf "%APPJAR%" | findstr /c:"javax/microedition/midlet/MIDlet.class" >nul
if errorlevel 1 (
    echo FAILED: the packaged JAR has no MIDP runtime
    goto :fail
)
jar tf "%APPJAR%" | findstr /c:"j2pc/rt/" >nul
if errorlevel 1 (
    echo FAILED: the packaged JAR has no runtime support
    goto :fail
)

echo.
echo Self test passed.
exit /b 0

:fail
echo.
echo SELF TEST FAILED
exit /b 1
