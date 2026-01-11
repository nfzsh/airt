#!/bin/bash

# AI Roundtable API 测试脚本

set -e

BASE_URL="http://localhost:8080/airt"
SESSION_ID=""

echo "=================================="
echo "AI Roundtable API 测试"
echo "=================================="
echo

# 1. 健康检查
echo "1. 测试健康检查接口..."
response=$(curl -s -w "%{http_code}" -o /dev/null "$BASE_URL/api/roundtable/health")
if [ "$response" = "200" ]; then
    echo "✅ 健康检查通过"
else
    echo "❌ 健康检查失败 (HTTP $response)"
    exit 1
fi
echo

# 2. 创建会话
echo "2. 创建新会话..."
response=$(curl -s -X POST \
    -H "Content-Type: application/json" \
    -d '{
        "topic": "是否应该采用 Rust 重构支付网关",
        "backgroundInfo": "现有系统使用 Java 开发，团队对 Rust 不熟悉",
        "selectedAgentRoles": ["MODERATOR", "DOMAIN_EXPERT", "DEVILS_ADVOCATE", "PRODUCT_BIZ"],
        "autoSelectAgents": false
    }' \
    "$BASE_URL/api/roundtable/sessions")

echo "响应: $response"
SESSION_ID=$(echo "$response" | grep -o '"sessionId":"[^"]*' | cut -d'"' -f4)

if [ -z "$SESSION_ID" ]; then
    echo "❌ 会话创建失败"
    exit 1
else
    echo "✅ 会话创建成功"
    echo "会话ID: $SESSION_ID"
fi
echo

# 3. 获取会话信息
echo "3. 获取会话信息..."
response=$(curl -s -w "%{http_code}" -o /dev/null "$BASE_URL/api/roundtable/sessions/$SESSION_ID")
if [ "$response" = "200" ]; then
    echo "✅ 获取会话信息成功"
else
    echo "❌ 获取会话信息失败 (HTTP $response)"
fi
echo

# 4. 获取会话列表
echo "4. 获取会话列表..."
response=$(curl -s -w "%{http_code}" -o /dev/null "$BASE_URL/api/roundtable/sessions")
if [ "$response" = "200" ]; then
    echo "✅ 获取会话列表成功"
else
    echo "❌ 获取会话列表失败 (HTTP $response)"
fi
echo

# 5. 获取会话中的 Agent
echo "5. 获取会话中的 Agent..."
response=$(curl -s -w "%{http_code}" -o /dev/null "$BASE_URL/api/roundtable/sessions/$SESSION_ID/agents")
if [ "$response" = "200" ]; then
    echo "✅ 获取 Agent 列表成功"
else
    echo "❌ 获取 Agent 列表失败 (HTTP $response)"
fi
echo

# 6. 执行下一轮讨论
echo "6. 执行下一轮讨论..."
response=$(curl -s -w "%{http_code}" -o /dev/null -X POST \
    "$BASE_URL/api/roundtable/sessions/$SESSION_ID/next")
if [ "$response" = "200" ]; then
    echo "✅ 下一轮执行成功"
else
    echo "❌ 下一轮执行失败 (HTTP $response)"
fi
echo

# 7. 模拟人类干预
echo "7. 模拟人类干预..."
response=$(curl -s -w "%{http_code}" -o /dev/null -X POST \
    -H "Content-Type: application/json" \
    -d '{
        "type": "DIRECTED_QUESTION",
        "content": "请详细说明技术方案的风险",
        "targetAgentId": "DOMAIN_EXPERT",
        "autoResume": true
    }' \
    "$BASE_URL/api/roundtable/sessions/$SESSION_ID/intervene")
if [ "$response" = "200" ]; then
    echo "✅ 人类干预成功"
else
    echo "❌ 人类干预失败 (HTTP $response)"
fi
echo

# 8. 生成决策报告
echo "8. 生成决策报告..."
response=$(curl -s -w "%{http_code}" -o /dev/null "$BASE_URL/api/roundtable/sessions/$SESSION_ID/decision")
if [ "$response" = "200" ]; then
    echo "✅ 决策报告生成成功"
else
    echo "❌ 决策报告生成失败 (HTTP $response)"
fi
echo

# 9. 删除会话
echo "9. 删除会话..."
response=$(curl -s -w "%{http_code}" -o /dev/null -X DELETE \
    "$BASE_URL/api/roundtable/sessions/$SESSION_ID")
if [ "$response" = "200" ]; then
    echo "✅ 会话删除成功"
else
    echo "❌ 会话删除失败 (HTTP $response)"
fi
echo

echo "=================================="
echo "API 测试完成"
echo "=================================="
