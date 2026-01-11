# AI Roundtable (AIRT) - 智能认知圆桌

一个"人在环路（Human-in-the-loop）"的群体智能思考系统，通过多智能体协作与对抗，将单点 AI 的"生成能力"转化为系统的"认知能力"。

## 🌟 核心特性

- **多智能体协作**: 预置不同认知模型的 Agent（主持人、专家、挑战者、价值官、记录员）
- **公共白板**: 实时维护讨论共识、分歧和待解决问题
- **上帝视角控制台**: 人类可随时干预、定向提问、强制验证
- **结构化产出**: 自动生成包含共识、分歧和事实依据的决策报告
- **事件驱动架构**: 基于状态机的可控、可扩展设计

## 🏗️ 系统架构

```
用户 (Web UI) ←→ 调度器 (Orchestrator) ←→ Agent 运行时
                        ↓
                   状态机 (FSM) ←→ 公共白板 (Redis)
                        ↓
                  元认知监控 (MetaCritic)
```

## 📋 快速开始

### 前置要求

- Java 17+
- Maven 3.6+
- Redis 5.0+
- Node.js 16+ (可选，用于前端开发)

### 1. 克隆项目

```bash
git clone <repository-url>
cd airt
```

### 2. 配置环境

#### 后端配置

编辑 `src/main/resources/application.yml`:

```yaml
# LLM API Configuration
llm:
  openai:
    api-key: ${OPENAI_API_KEY:your-openai-api-key}
  anthropic:
    api-key: ${ANTHROPIC_API_KEY:your-anthropic-api-key}

# Redis Configuration
spring:
  redis:
    host: localhost
    port: 6379
```

#### 环境变量

```bash
export OPENAI_API_KEY="sk-your-openai-key"
export ANTHROPIC_API_KEY="your-anthropic-key"
```

### 3. 启动 Redis

```bash
redis-server
```

### 4. 编译并运行后端

```bash
# 编译项目
mvn clean compile

# 运行应用
mvn spring-boot:run
```

后端服务将在 `http://localhost:8080/airt` 启动。

### 5. 启动前端

```bash
cd frontend

# 使用 Python 启动本地服务器
python -m http.server 3000

# 或使用 Node.js
npx serve .
```

打开浏览器访问 `http://localhost:3000`

## 🎮 使用指南

### 1. 创建讨论

1. 点击"新建讨论"按钮
2. 输入讨论主题和背景信息
3. 选择参与角色（建议全选以获得最佳体验）
4. 点击"开始讨论"

### 2. 观察讨论

- **左侧讨论流**: 实时显示各角色的发言和推理过程
- **右侧公共白板**: 展示关键洞察、共识、分歧和待解决问题

### 3. 上帝模式干预

点击"显示控制台"可使用以下功能：

- **定向提问**: 向特定角色提问
- **耳语**: 给主持人发送私信
- **强制验证**: 要求系统验证某个陈述
- **改变焦点**: 调整讨论方向

### 4. 结束讨论

点击"结束讨论"生成结构化决策报告。

## 📁 项目结构

```
airt/
├── src/main/java/com/airt/
│   ├── model/              # 数据模型
│   ├── service/            # 业务服务
│   ├── controller/         # API 控制器
│   ├── agent/              # Agent 相关
│   ├── orchestrator/       # 调度器和状态机
│   ├── llm/                # LLM 服务
│   ├── monitor/            # 元认知监控
│   └── config/             # 配置类
├── src/main/resources/
│   ├── application.yml     # 应用配置
│   └── logback-spring.xml  # 日志配置
├── frontend/               # 前端界面
│   ├── index.html
│   ├── styles.css
│   └── app.js
├── pom.xml                 # Maven 配置
└── README.md
```

## 🎯 核心模块

### 1. OrchestratorService (调度器服务)

系统的"大脑"，负责：
- 元认知检查（是否陷入死循环、是否偏题）
- 选择下一个发言者
- 执行 Agent 响应
- 更新公共白板
- 推进状态机

### 2. AgentRuntime (Agent 运行时)

执行 Agent 的核心逻辑：
- 构建结构化 Prompt
- 调用 LLM API
- 解析响应
- 质量评估

### 3. SharedBlackboard (公共白板)

实现"多轮思考"的关键：
- 记录关键洞察
- 跟踪共识和分歧
- 维护事实日志
- 管理待解决问题

### 4. RoundtableFSM (状态机)

控制讨论流程：
- INIT → OPENING → DEBATE → SYNTHESIS → FINISHED
- 支持人类干预和事实核查
- 确保流程可控

## 🔧 配置说明

### LLM 模型配置

在 `application.yml` 中配置：

```yaml
llm:
  openai:
    api-key: ${OPENAI_API_KEY}
    model: gpt-4
    timeout: 30s
  anthropic:
    api-key: ${ANTHROPIC_API_KEY}
    model: claude-3-sonnet
    timeout: 30s
```

### Redis 配置

```yaml
spring:
  redis:
    host: localhost
    port: 6379
    database: 0
    timeout: 2000ms
```

### 应用配置

```yaml
airt:
  session-timeout: 60  # 会话超时时间（分钟）
  max-rounds: 50     # 最大讨论轮数
  god-mode:
    enabled: true    # 启用上帝模式
```

## 🚀 开发路线图

### ✅ Phase 1: MVP (已完成)
- [x] 基础调度器
- [x] 2-5 个 Agent 辩论
- [x] 简单的 Web 界面
- [x] 基础状态管理

### ✅ Phase 2: 认知增强 (已完成)
- [x] 公共白板实现
- [x] Scribe Agent 自动化
- [x] 观点提取和结构化
- [x] 冲突识别

### ✅ Phase 3: 事实锚定与干预 (已完成)
- [x] 上帝视角控制台
- [x] 人类干预功能
- [x] MCP 集成框架
- [x] 定向提问和耳语

### 🔄 未来增强

- [ ] MCP 服务集成 (Jira, GitHub, 内部 Wiki)
- [ ] 高级 Prompt 工程
- [ ] 多语言支持
- [ ] 决策报告导出 (PDF, Word)
- [ ] 讨论模板库
- [ ] Agent 个性化配置
- [ ] 实时语音集成

## 🧪 测试

```bash
# 运行单元测试
mvn test

# 运行集成测试
mvn verify
```

## 📊 监控和日志

应用日志位于 `logs/airt.log`，包含：
- 讨论状态变更
- Agent 执行日志
- WebSocket 连接状态
- 错误和异常信息

## 🔒 安全注意事项

1. **API 密钥**: 不要将 API 密钥提交到版本控制
2. **CORS**: 生产环境需配置正确的跨域策略
3. **Redis**: 确保 Redis 实例有访问控制
4. **输入验证**: 所有用户输入都经过验证和清理

## 🤝 贡献指南

1. Fork 项目
2. 创建特性分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 创建 Pull Request

## 📄 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情。

## 🙏 致谢

- Spring Boot 团队提供的优秀框架
- OpenAI 和 Anthropic 的 LLM API
- 所有为开源社区贡献的开发者

---

**AI Roundtable** - 让群体智能服务于人类决策 🪑🤖
