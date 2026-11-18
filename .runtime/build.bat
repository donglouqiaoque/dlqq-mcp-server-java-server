@echo off
setlocal EnableExtensions

REM ============================================================
REM dlqq-mcp-server - build and copy artifacts to .runtime
REM Script lives in <project root>\script\ , project root = parent dir
REM
REM Usage:
REM   build_and_copy.bat [nopause]
REM
REM   nopause  skip the final pause (used by AI automation)
REM
REM Output:
REM   .runtime\dlqq_mcp_server.jar   (thin jar)
REM   .runtime\libs\*.jar               (runtime dependencies)
REM
REM RULE: this file must stay ASCII-only. Do NOT add CJK characters
REM       to a .bat file (cmd codepage would garble them).
REM ============================================================

set "SCRIPT_DIR=%~dp0"
set "PROJECT_DIR=%SCRIPT_DIR%.."
cd /d "%PROJECT_DIR%"

set "NOPAUSE=0"
:parse
if "%~1"=="" goto parsed
if /i "%~1"=="nopause"  ( set "NOPAUSE=1" & shift & goto parse )
if /i "%~1"=="-nopause" ( set "NOPAUSE=1" & shift & goto parse )
if /i "%~1"=="/nopause" ( set "NOPAUSE=1" & shift & goto parse )
shift & goto parse
:parsed

set "JAR_NAME=dlqq_mcp_server.jar"

echo [INFO] ========================================
echo [INFO] Building Java project...
echo [INFO] ========================================
echo.

call mvn clean package -DskipTests

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven build failed!
    call :do_pause
    exit /b 1
)

echo.
echo [INFO] Maven build success!
echo [INFO] Copying artifacts to .runtime ...

if not exist ".runtime" mkdir ".runtime"
if not exist ".runtime\libs" mkdir ".runtime\libs"

REM Remove stale jars so the target reflects the current dependency set
if exist ".runtime\libs\*.jar" del /q ".runtime\libs\*.jar"

copy /Y "target\%JAR_NAME%" ".runtime\%JAR_NAME%" >nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Failed to copy %JAR_NAME%
    call :do_pause
    exit /b 1
)

if not exist "target\libs" (
    echo [ERROR] target\libs not found. Check the maven-dependency-plugin configuration.
    call :do_pause
    exit /b 1
)

robocopy "target\libs" ".runtime\libs" *.jar /NJH /NJS /NDL /NP /R:1 /W:1 >nul
if %ERRORLEVEL% GEQ 8 (
    echo [ERROR] Failed to copy dependency jars to .runtime\libs
    call :do_pause
    exit /b 1
)

echo.
echo [INFO] ========================================
echo [INFO] Build completed
echo [INFO] ========================================
echo [INFO] JAR : .runtime\%JAR_NAME%
echo [INFO] Libs: .runtime\libs
echo.
call :do_pause

endlocal
exit /b 0

:do_pause
if "%NOPAUSE%"=="0" pause
exit /b 0
