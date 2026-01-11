@echo off
echo ======================================
echo   AI Roundtable (AIRT) 启动脚本
echo ======================================
echo.

REM 检查 JAVA_HOME
if "%JAVA_HOME%"=="" (
    echo [错误] 未设置 JAVA_HOME 环境变量
    echo 请安装 Java 17+ 并设置 JAVA_HOME
    pause
    exit /b 1
)

echo [1/2] 编译项目...
call mvn clean compile -DskipTests
if %ERRORLEVEL% neq 0 (
    echo [错误] 编译失败
    pause
    exit /b 1
)

echo.
echo [2/2] 启动应用...
echo 应用将在 http://localhost:8080/airt 启动
echo 按 Ctrl+C 停止应用
echo.

call mvn spring-boot:run

pause
