# JSON 命名策略修复说明

## 问题描述

前端无法正确显示角色和内容，根本原因是后端和前端的 JSON 字段命名风格不一致：

- **后端**: 使用 `snake_case`（如 `role_display_name`, `thinking_process`）
- **前端**: 使用 `camelCase`（如 `roleDisplayName`, `thinkingProcess`）

## 根本原因

`JacksonConfig.java` 配置了 `PropertyNamingStrategies.SNAKE_CASE`，导致所有 API 返回的 JSON 都是 snake_case 格式。

## 修复方案

### 修改 JacksonConfig.java

**修改前**:
```java
// 使用 Snake Case 命名策略
mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
```

**修改后**:
```java
// 使用 camelCase 命名策略（Jackson 默认）
// 移除 SNAKE_CASE 以便与前端 JavaScript 对象命名保持一致
```

## 修改前后对比

### History API 返回格式

**修改前** (snake_case):
```json
[
    {
        "agent_instance_id": "...",
        "role_name": "moderator",
        "role_display_name": "主持人",
        "thinking_process": "...",
        "public_response": "...",
        "key_insights": [],
        "tool_calls": []
    }
]
```

**修改后** (camelCase):
```json
[
    {
        "agentInstanceId": "...",
        "roleName": "moderator",
        "roleDisplayName": "主持人",
        "thinkingProcess": "...",
        "publicResponse": "...",
        "keyInsights": [],
        "toolCalls": []
    }
]
```

### Session API 返回格式

**修改前** (snake_case):
```json
{
    "session_id": "...",
    "selected_roles": ["moderator"],
    "current_round": 1,
    "max_rounds": 50
}
```

**修改后** (camelCase):
```json
{
    "sessionId": "...",
    "selectedRoles": ["moderator"],
    "currentRound": 1,
    "maxRounds": 50
}
```

## 受影响的 API

所有 REST API 的返回格式都会从 snake_case 改为 camelCase：

1. `/api/roundtable/session/{id}` - RoundtableSession
2. `/api/roundtable/session/{id}/history` - List<AgentResponse>
3. `/api/config/roles` - List<RoleConfigDTO>
4. `/api/config/mcp-profiles` - List<MCPProfileConfigDTO>
5. `/api/config/prompt-templates` - List<PromptTemplateConfigDTO>

## 前端字段映射

前端 JavaScript 代码中使用的字段名现在与后端返回的 JSON 完全匹配：

| 前端字段 | 后端字段 (修改后) | 说明 |
|---------|-----------------|------|
| `msg.roleDisplayName` | `roleDisplayName` | 角色显示名称 |
| `msg.thinkingProcess` | `thinkingProcess` | 思考过程 |
| `msg.publicResponse` | `publicResponse` | 公开响应 |
| `msg.keyInsights` | `keyInsights` | 关键洞察 |
| `msg.toolCalls` | `toolCalls` | 工具调用 |
| `msg.timestamp` | `timestamp` | 时间戳 |
| `session.sessionId` | `sessionId` | 会话 ID |
| `session.selectedRoles` | `selectedRoles` | 选择的角色列表 |
| `session.currentRound` | `currentRound` | 当前轮数 |

## 测试步骤

1. **重启应用**：需要重启 Spring Boot 应用以使 Jackson 配置生效

2. **测试角色加载**：
   ```javascript
   // 控制台应该显示正确格式的角色数据
   fetch('/airt/api/config/roles')
       .then(r => r.json())
       .then(roles => console.log(roles));
   ```

3. **测试创建会话**：
   - 选择角色
   - 输入主题
   - 点击"开始讨论"

4. **测试历史显示**：
   - 点击"下一轮"推进讨论
   - 检查讨论历史是否正确显示角色名称和内容

## 注意事项

1. **必须重启应用**：修改 Jackson 配置后需要重启 Spring Boot 应用
2. **前端无需修改**：前端代码已经使用 camelCase，无需任何修改
3. **其他客户端**：如果有其他客户端（如移动应用、第三方集成）依赖 snake_case 格式，需要同步更新

## 相关文件

- `src/main/java/com/airt/config/JacksonConfig.java` - Jackson 配置
- `src/main/java/com/airt/dto/AgentResponse.java` - Agent 响应 DTO
- `src/main/java/com/airt/model/RoundtableSession.java` - 会话模型
- `frontend/app-new.js` - 前端应用逻辑
