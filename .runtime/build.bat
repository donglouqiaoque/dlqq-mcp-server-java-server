@echo off
setlocal EnableExtensions

REM ============================================================
REM dlqq-mcp-server - build and copy artifacts to .runtime
REM
REM Fixed location: <project root>\.runtime\build.bat
REM Project root is the parent directory of this script folder.
REM
REM Usage:
REM   build.bat [nopause]
REM
REM   nopause  skip the final pause (used by AI automation)
REM
REM Output:
REM   .runtime\dlqq_mcp_server.jar   (thin jar)
REM   .runtime\libs\*.jar            (runtime dependencies)
REM
REM Toolchain:
REM   Java  : .runtime\java          (bundled JDK)
REM   Maven : .runtime\maven         (bundled Maven)
REM
REM RULE: this file must stay ASCII-only. Do NOT add CJK characters
REM       to a .bat file (cmd codepage would garble them).
REM ============================================================

REM This script must live in <project root>\.runtime\
set "RUNTIME_DIR=%~dp0"
if "%RUNTIME_DIR:~-1%"=="\" set "RUNTIME_DIR=%RUNTIME_DIR:~0,-1%"
for %%I in ("%RUNTIME_DIR%\..") do set "PROJECT_DIR=%%~fI"
set "TARGET_DIR=%PROJECT_DIR%\target"

echo [INFO] Runtime dir: "%RUNTIME_DIR%"
echo [INFO] Project dir: "%PROJECT_DIR%"
echo.

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

REM Use the bundled JDK and Maven from this runtime folder
set "JAVA_HOME=%RUNTIME_DIR%\java"
set "MVN=%RUNTIME_DIR%\maven\bin\mvn.cmd"

if not exist "%JAVA_HOME%\bin\java.exe" (
    echo [ERROR] Java not found: "%JAVA_HOME%\bin\java.exe"
    call :do_pause
    exit /b 1
)
if not exist "%MVN%" (
    echo [ERROR] Maven not found: "%MVN%"
    call :do_pause
    exit /b 1
)

echo [INFO] JAVA_HOME: "%JAVA_HOME%"
echo [INFO] MVN      : "%MVN%"
echo.

echo [INFO] Maven version:
call "%MVN%" -v
echo.

echo [INFO] Java version:
"%JAVA_HOME%\bin\java.exe" -version 2>&1
echo.

echo [INFO] ========================================
echo [INFO] Building Java project...
echo [INFO] ========================================
echo.

call "%MVN%" clean package -DskipTests

if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven build failed!
    call :do_pause
    exit /b 1
)

echo.
echo [INFO] Maven build success!
echo [INFO] Copying artifacts to "%RUNTIME_DIR%" ...

if not exist "%RUNTIME_DIR%\libs" mkdir "%RUNTIME_DIR%\libs"

REM Remove stale jars so the target reflects the current dependency set
if exist "%RUNTIME_DIR%\libs\*.jar" del /q "%RUNTIME_DIR%\libs\*.jar"

copy /Y "%TARGET_DIR%\%JAR_NAME%" "%RUNTIME_DIR%\%JAR_NAME%" >nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Failed to copy %JAR_NAME%
    echo [ERROR] Source: "%TARGET_DIR%\%JAR_NAME%"
    echo [ERROR] Target: "%RUNTIME_DIR%\%JAR_NAME%"
    echo [ERROR] Hint: stop the running server if the jar is locked, then retry.
    call :do_pause
    exit /b 1
)

if not exist "%TARGET_DIR%\libs" (
    echo [ERROR] "%TARGET_DIR%\libs" not found.
    echo [ERROR] Check the maven-dependency-plugin configuration.
    call :do_pause
    exit /b 1
)

robocopy "%TARGET_DIR%\libs" "%RUNTIME_DIR%\libs" *.jar /NJH /NJS /NDL /NP /R:1 /W:1 >nul
if %ERRORLEVEL% GEQ 8 (
    echo [ERROR] Failed to copy dependency jars to "%RUNTIME_DIR%\libs"
    call :do_pause
    exit /b 1
)

echo.
echo [INFO] ========================================
echo [INFO] Build completed
echo [INFO] ========================================
echo [INFO] JAR : "%RUNTIME_DIR%\%JAR_NAME%"
echo [INFO] Libs: "%RUNTIME_DIR%\libs"
echo.
call :do_pause

endlocal
exit /b 0

:do_pause
if "%NOPAUSE%"=="0" pause
exit /b 0
