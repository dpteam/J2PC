@echo off
rem ---------------------------------------------------------------------------
rem J2PC test runner: recompiles the sample JARs, compiles the generated Java
rem and reports the result. Used by CI and by hand.
rem
rem Usage: test.cmd [path-to-j2me-folder]
rem ---------------------------------------------------------------------------
setlocal enabledelayedexpansion

set "ROOT=%~dp0"
cd /d "%ROOT%"

set "DIST=dist"

set "SAMPLES=%~1"
if "%SAMPLES%"=="" set "SAMPLES=%ROOT%samples"

if not exist "%DIST%\j2pc.jar" (
    echo Building first...
    call build.cmd
    if errorlevel 1 exit /b 1
)

set FAILED=0
set PASSED=0

for %%J in ("%SAMPLES%\*.jar") do (
    set "NAME=%%~nJ"
    set "OUT=build\test\%%~nJ"
    echo.
    echo === !NAME! ===
    if exist "!OUT!" rmdir /s /q "!OUT!"
    java -jar "%DIST%\j2pc.jar" -q -o "!OUT!" "%%~fJ"
    if errorlevel 1 (
        echo   [FAIL] recompilation
        set /a FAILED+=1
    ) else (
        if not exist "!OUT!\*.java" (
            echo   [SKIP] no classes
        ) else (
            dir /s /b "!OUT!\*.java" > build\test\gen.txt
            if not exist "build\test\classes\!NAME!" mkdir "build\test\classes\!NAME!"
            javac -encoding UTF-8 -cp "%DIST%\j2pc.jar" -d "build\test\classes\!NAME!" @build\test\gen.txt > build\test\javac.log 2>&1
            if errorlevel 1 (
                echo   [FAIL] generated code does not compile - see build\test\javac.log
                set /a FAILED+=1
            ) else (
                echo   [ OK ] recompiled and compiled
                set /a PASSED+=1
            )
        )
    )
)

echo.
echo Passed: !PASSED!  Failed: !FAILED!
if !FAILED! GTR 0 exit /b 1
exit /b 0
