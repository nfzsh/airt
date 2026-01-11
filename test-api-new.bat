@echo off
chcp 65001 >nul
setlocal enabledelayedexpansion

set API_BASE=http://localhost:8080/airt/api/roundtable

echo ====================================
echo AIRT API 测试脚本
echo ====================================
echo.

echo [测试 1] 健康检查...
curl -s %API_BASE%/health
echo.
echo.

echo [测试 2] 获取所有角色...
curl -s %API_BASE%/roles
echo.
echo.

echo [测试 3] 创建会话...
curl -s -X POST %API_BASE%/session ^
  -H "Content-Type: application/json" ^
  -d "{\"topic\":\"是否应该采用 Rust 重构支付网关\",\"description\":\"评估技术可行性和投入产出\",\"roles\":[\"product_manager\",\"backend_engineer\",\"devil_advocate\"]}"
echo.
echo.

echo 注意: 请记下返回的 sessionId
set /p SESSION_ID="请输入 sessionId: "

echo.
echo [测试 4] 启动会话...
curl -s -X POST %API_BASE%/session/%SESSION_ID%/start
echo.
echo.

echo [测试 5] 推进讨论...
curl -s -X POST %API_BASE%/session/%SESSION_ID%/proceed
echo.
echo.

echo [测试 6] 获取讨论历史...
curl -s %API_BASE%/session/%SESSION_ID%/history
echo.
echo.

echo [测试 7] 获取会话信息...
curl -s %API_BASE%/session/%SESSION_ID%
echo.
echo.

echo 测试完成！
pause
