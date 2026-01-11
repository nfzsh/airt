# AI Roundtable (AIRT) - 项目上下文文档

## 项目概述

**AI Roundtable (AIRT)** 是一个"人在环路（Human-in-the-loop）"的群体智能思考系统。

### 核心价值
通过多智能体协作与对抗，将单点 AI 的"生成能力"转化为系统的"认知能力"，为人类决策者提供去偏见、多视角的深度分析。

### 技术栈
- **后端**: Spring Boot 3.2.0 + Java 17
- **AI 框架**: LangChain4j 0.36.2
- **状态机**: Spring State Machine 3.0.1
- **通信**: WebSocket
- **前端**: 原生 HTML/CSS/JavaScript

---

## 关键设计文档

### 完整需求文档
参见 `docs/PRD.md` - 包含产品定义、角色体系、MCP 集成、应用场景等完整内容。

### 技术架构
参见 `docs/TechSpec.md` - 包含系统架构图、核心模块设计、落地路线图。

---

## 项目结构

```
airt/
├── src/main/java/com/airt/
│   ├── agent/              # Agent 运行时和工厂
│   │   ├── AgentFactory.java      # 创建不同角色的 Agent
│   │   └── AgentRuntime.java      # Agent 执行引擎
│   ├── config/             # 配置类
│   │   ├── WebSocketConfig.java  # WebSocket 配置
│   │   ├── JacksonConfig.java    # JSON 序列化配置
│   │   └── WebConfig.java        # MVC 静态资源配置
│   ├── controller/         # REST API
│   │   ├── RoundtableController.java  # 核心 API
│   │   └── HomeController.java        # 首页路由
│   ├── dto/                # 数据传输对象
│   ├── model/              # 领域模型
│   │   ├── RoleDefinition.java       # 角色定义
│   │   ├── MCPProfile.java           # MCP 能力配置
│   │   ├── PromptTemplate.java       # Prompt 模板
│   │   ├── RoundtableSession.java    # 会话模型
│   │   ├── SharedBlackboard.java     # 公共白板
│   │   └── AgentInstance.java        # Agent 实例
│   ├── mcp/                # MCP 相关
│   │   ├── MCPTool.java             # MCP 工具定义
│   │   ├── MCPService.java          # MCP 服务
│   │   └── MCPToolExecutionResult.java
│   └── service/            # 业务服务
│       ├── OrchestratorService.java  # 核心调度器
│       ├── AgentSelector.java        # Agent 选择策略
│       ├── BlackboardService.java    # 白板管理
│       └── WebSocketService.java     # WebSocket 推送
├── src/main/resources/
│   ├── config/
│   │   ├── roles.yaml              # 角色定义配置
│   │   ├── mcp-profiles.yaml       # MCP 能力配置
│   │   └── prompt-templates.yaml   # Prompt 模板
│   └── application.yml             # 应用配置
├── frontend/               # 前端资源
│   ├── index.html
│   ├── styles.css
│   └── app-new.js
└── docs/                   # 文档
    ├── PRD.md             # 产品需求文档
    └── Claude.md          # 本文件
```

---

## 核心概念

### 1. 角色体系
系统支持可配置的角色，每个角色有：
- **认知风格** (cognitive_style)
- **核心职责** (core_responsibility)
- **允许行为** (allowed_actions)
- **MCP 能力域** (mcp_profile)

常见角色：Moderator、Domain Expert、Devil's Advocate、Product/Biz、Scribe

### 2. 公共白板 (Shared Blackboard)
实现"多轮思考"的关键数据结构：
- `key_insights`: 关键洞察
- `consensus_points`: 共识点
- `conflict_map`: 分歧点
- `pending_questions`: 待解决问题

### 3. MCP (Model Context Protocol) 集成
- 每个角色绑定不同的 MCP 能力域
- Agent 只能调用被授权的 MCP 工具
- Orchestrator 作为权限裁决者

### 4. 上帝模式 (God Mode)
人类可以随时干预讨论：
- 暂停/继续讨论
- 定向提问
- 耳语 (Whisper)
- 强制验证
- 改变焦点

---

## API 端点

### 健康检查
```
GET /airt/api/roundtable/health
```

### 角色管理
```
GET /airt/api/roundtable/roles
```

### 会话管理
```
POST   /airt/api/roundtable/session
GET    /airt/api/roundtable/session/{sessionId}
POST   /airt/api/roundtable/session/{sessionId}/start
POST   /airt/api/roundtable/session/{sessionId}/proceed
POST   /airt/api/roundtable/session/{sessionId}/pause
POST   /airt/api/roundtable/session/{sessionId}/resume
POST   /airt/api/roundtable/session/{sessionId}/finish
GET    /airt/api/roundtable/session/{sessionId}/history
POST   /airt/api/roundtable/session/{sessionId}/question
```

### WebSocket
```
ws://localhost:8080/airt/ws/roundtable/{sessionId}
```

---

## 配置说明

### LLM API 配置
在 `application.yml` 中配置：

```yaml
llm:
  openai:
    api-key: your-openai-api-key
    base-url: https://api.openai.com
    model: gpt-4
  anthropic:
    api-key: your-anthropic-api-key
    base-url: https://api.anthropic.com
    model: claude-3-sonnet
```

### 角色配置示例
在 `config/roles.yaml` 中定义角色：

```yaml
role_id: product_manager
display_name: 产品经理
icon: "👔"
cognitive_style:
  - user_value_driven
  - tradeoff_sensitive
core_responsibility:
  - 澄清需求目标
  - 评估需求优先级
allowed_actions:
  - propose
  - challenge
mcp_profile: pm_default
prompt_template: pm_v1
```

### MCP 配置示例
在 `config/mcp-profiles.yaml` 中定义能力域：

```yaml
profile_id: pm_default
description: 产品经理常用信息源
capabilities:
  - name: search_internal_knowledge
    scope: [product_docs, roadmap]
  - name: query_metrics
    scope: [DAU, conversion_rate]
rate_limit:
  per_minute: 5
```

---

## 开发指南

### 添加新角色

1. 在 `config/roles.yaml` 中定义角色
2. 在 `config/prompt-templates.yaml` 中定义 Prompt
3. 在 `config/mcp-profiles.yaml` 中配置 MCP 能力
4. 无需修改 Java 代码

### 添加新 MCP 工具

1. 实现 `MCPTool` 接口
2. 在 MCP 配置文件中注册工具
3. 为角色分配权限

### 前端开发

前端使用原生 JavaScript，主要文件：
- `index.html`: 页面结构
- `styles.css`: 样式
- `app-new.js`: 应用逻辑

---

## 开发路线图

### ✅ Phase 1: MVP (已完成)
- 基础架构
- 角色体系
- WebSocket 通信
- 简单 Web 界面

### ⏳ Phase 2: 认知增强
- 公共白板完善
- Scribe Agent 自动化
- 智能调度策略

### 📋 Phase 3: 事实锚定与干预
- MCP 深度集成
- 人类干预机制
- 强制验证功能

---

## 重要提醒

### 对 AI 助手

1. **始终参考** `docs/PRD.md` 了解完整需求
2. **遵循** 项目结构约定
3. **优先考虑** 可配置性，新功能应尽量通过配置实现
4. **保持** 简单，避免过度工程化
5. **确保** 安全性，特别是 MCP 权限控制

### 对开发者

1. 所有 Prompt 应外部化到配置文件
2. 新角色只需配置，无需改代码
3. MCP 调用必须经过权限检查
4. 前端使用 SSE 实现流式输出
5. 保持 API 的 RESTful 风格

---

## 常见问题

### Q: 如何添加新的讨论场景？
A: 在 `config/roles.yaml` 中定义新角色，或在 `config/prompt-templates.yaml` 中定义新 Prompt。

### Q: 如何限制 Agent 的能力？
A: 通过 MCP Profile 配置能力域和权限。

### Q: 如何实现自定义调度策略？
A: 修改 `AgentSelector.java` 中的选择逻辑。

### Q: 前端如何实现实时更新？
A: 使用 WebSocket 连接 `/ws/roundtable/{sessionId}`。

---

## 联系方式

如有问题，请查看 `docs/PRD.md` 或提交 Issue。
