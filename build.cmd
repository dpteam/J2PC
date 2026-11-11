@echo off
rem ---------------------------------------------------------------------------
rem J2PC build script for Windows (cmd.exe).
rem Requires a JDK 17+ on the PATH; no Maven or Gradle needed.
rem ---------------------------------------------------------------------------
setlocal

set "ROOT=%~dp0"
cd /d "%ROOT%"

set "CLASSES=build\classes"
set "DIST=dist"

if not exist "%CLASSES%" mkdir "%CLASSES%"
if not exist "%DIST%" mkdir "%DIST%"

echo [1/3] Compiling sources...
dir /s /b src\*.java > build\sources.txt
javac -encoding UTF-8 -d "%CLASSES%" @build\sources.txt
if errorlevel 1 goto :fail

echo [2/3] Creating manifest...
> build\manifest.txt echo Main-Class: j2pc.Main

echo [3/3] Packaging dist\j2pc.jar...
jar cfm "%DIST%\j2pc.jar" build\manifest.txt -C "%CLASSES%" .
if errorlevel 1 goto :fail

echo.
echo Build finished: %DIST%\j2pc.jar
echo Try: java -jar %DIST%\j2pc.jar --help
exit /b 0

:fail
echo.
echo BUILD FAILED
exit /b 1
