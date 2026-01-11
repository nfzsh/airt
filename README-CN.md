# AI Roundtable (AIRT) - 智能认知圆桌

## 快速开始

### 1. 安装依赖

- Java 17+
- Maven 3.6+
- Redis 5.0+

### 2. 配置 API Key（可选）

如果不配置，系统将使用模拟响应模式。

```bash
# Windows PowerShell
$env:OPENAI_API_KEY="sk-your-openai-key"
$env:ANTHROPIC_API_KEY="your-anthropic-key"

# Windows CMD
set OPENAI_API_KEY=sk-your-openai-key
set ANTHROPIC_API_KEY=your-anthropic-key

# Linux/macOS
export OPENAI_API_KEY="sk-your-openai-key"
export ANTHROPIC_API_KEY="your-anthropic-key"
```

### 3. 启动 Redis

```bash
# Windows
redis-server

# Linux/macOS
redis-server --daemonize yes
```

### 4. 运行项目

**方式一：使用 Maven**

```bash
mvn spring-boot:run
```

**方式二：打包后运行**

```bash
mvn clean package
java -jar target/airt-1.0.0.jar
```

### 5. 访问前端

打开浏览器访问：
- 前端界面：打开 `frontend/index.html`
- API 地址：`http://localhost:8080/airt/api/roundtable`

## 使用说明

### 创建讨论

1. 输入讨论主题
2. 选择参与角色（支持多选）：
   - 🎯 产品经理
   - ⚙️ 后端工程师
   - 🎨 前端工程师
   - 💼 业务专家
   - 😈 挑战者
   - 🎙️ 主持人
   - 📚 技术专家
   - 📝 记录员
3. 点击"开始讨论"

### 讨论控制

- **推进一轮**：让下一个 AI 发言
- **暂停**：暂停讨论
- **继续**：恢复讨论
- **结束讨论**：生成决策报告

## API 端点

### 获取所有角色
```
GET /airt/api/roundtable/roles
```

### 创建会话
```
POST /airt/api/roundtable/session
{
  "topic": "讨论主题",
  "description": "背景描述",
  "roles": ["product_manager", "backend_engineer"]
}
```

### 启动会话
```
POST /airt/api/roundtable/session/{sessionId}/start
```

### 推进讨论
```
POST /airt/api/roundtable/session/{sessionId}/proceed
```

### 暂停/恢复/结束
```
POST /airt/api/roundtable/session/{sessionId}/pause
POST /airt/api/roundtable/session/{sessionId}/resume
POST /airt/api/roundtable/session/{sessionId}/finish
```

### 获取历史记录
```
GET /airt/api/roundtable/session/{sessionId}/history
```

## 配置文件

### 角色配置
`src/main/resources/config/roles.yaml` - 定义所有可用角色

### MCP 配置
`src/main/resources/config/mcp-profiles.yaml` - 定义各角色的 MCP 能力域

### Prompt 模板
`src/main/resources/config/prompt-templates.yaml` - 定义各角色的系统提示词

## 自定义角色

要添加新角色，编辑 `roles.yaml`：

```yaml
roles:
  - role_id: your_role
    display_name: 你的角色名
    description: 角色描述
    cognitive_style:
      - style1
      - style2
    core_responsibility:
      - 职责1
      - 职责2
    allowed_actions:
      - action1
      - action2
    mcp_profile: your_mcp_profile
    prompt_template: your_template
    recommended_model: gpt-4
    system_role: true
    icon: "🤖"
```

## 技术架构

- **后端框架**: Spring Boot 3.2.0
- **AI 框架**: LangChain4j 0.36.2
- **状态机**: Spring State Machine
- **缓存**: Redis
- **前端**: 原生 HTML/CSS/JavaScript

## 故障排查

### 端口被占用
修改 `src/main/resources/application.yml` 中的 `server.port`

### Redis 连接失败
确保 Redis 正在运行：`redis-cli ping` 应返回 `PONG`

### API 调用失败
检查 API Key 是否正确配置，或使用模拟模式（不配置 API Key）

## 开发路线图

- [ ] 完善 MCP 工具实现
- [ ] WebSocket 实时通信
- [ ] 更多角色模板
- [ ] 决策报告导出
- [ ] 多语言支持
