# AIRT 项目当前状态

## ✅ 已完成

### 核心代码 (100%)
- [x] 24 个 Java 类已创建
- [x] pom.xml 依赖配置完成
- [x] 配置文件 (roles.yaml, mcp-profiles.yaml, prompt-templates.yaml)
- [x] 前端界面 (HTML/CSS/JS)

### 功能实现
- [x] 可配置的角色体系 (8 个预置角色)
- [x] MCP 能力域绑定
- [x] Agent 工厂和运行时
- [x] 调度器 (Orchestrator)
- [x] REST API (8 个端点)
- [x] WebSocket 配置
- [x] 公共白板服务
- [x] 前端界面

### 代码质量
- [x] 清理未使用的导入
- [x] 修复 langchain4j API 调用
- [x] 添加 Jackson 配置

## 📋 需要用户操作

### 1. 安装依赖
- Java 17+
- Maven 3.6+
- Redis 5.0+

### 2. 配置 API Key (可选)
```bash
export OPENAI_API_KEY="sk-your-key"
```

### 3. 编译运行
```bash
# 方式一: 使用 Maven
mvn spring-boot:run

# 方式二: 使用 IDE
# 在 IntelliJ IDEA 中运行 AirtApplication.java

# 方式三: 使用启动脚本 (Windows)
start.bat
```

### 4. 访问前端
直接在浏览器中打开 `frontend/index.html`

## 📁 文件结构

```
airt/
├── src/main/java/com/airt/
│   ├── AirtApplication.java           # 主应用
│   ├── agent/
│   │   ├── AgentFactory.java          # Agent 工厂
│   │   └── AgentRuntime.java          # Agent 运行时
│   ├── config/
│   │   ├── ConfigurationLoader.java   # 配置加载
│   │   ├── JacksonConfig.java         # JSON 配置
│   │   └── WebSocketConfig.java       # WebSocket 配置
│   ├── controller/
│   │   └── RoundtableController.java  # REST API
│   ├── dto/
│   │   └── AgentResponse.java         # 响应 DTO
│   ├── mcp/
│   │   ├── MCPService.java            # MCP 服务
│   │   ├── MCPTool.java               # 工具定义
│   │   └── MCPToolExecutionResult.java
│   ├── model/
│   │   ├── AgentInstance.java
│   │   ├── MCPProfile.java
│   │   ├── PromptTemplate.java
│   │   ├── RoleDefinition.java
│   │   ├── RoundtableSession.java
│   │   ├── SessionContext.java
│   │   └── SharedBlackboard.java
│   └── service/
│       ├── AgentSelector.java         # 发言者选择
│       ├── BlackboardService.java     # 白板服务
│       ├── OrchestratorService.java   # 调度器
│       └── WebSocketService.java      # WebSocket 服务
├── src/main/resources/
│   ├── application.yml                # 应用配置
│   ├── config/
│   │   ├── roles.yaml                 # 角色定义
│   │   ├── mcp-profiles.yaml          # MCP 配置
│   │   └── prompt-templates.yaml      # Prompt 模板
│   └── logback-spring.xml
├── frontend/
│   ├── index.html                     # 前端页面
│   ├── app-new.js                     # 应用脚本
│   └── styles.css                     # 样式
├── pom.xml                            # Maven 配置
├── start.bat                          # 启动脚本
├── test-api-new.bat                   # API 测试
├── README-CN.md                       # 中文说明
└── TROUBLESHOOTING.md                 # 故障排查
```

## 🎯 核心特性

1. **动态角色配置** - 在开始讨论时自由选择角色组合
2. **MCP 能力域绑定** - 每个角色只能调用授权的 MCP 工具
3. **配置驱动** - 新增角色只需修改 YAML
4. **多模型支持** - 基于 langchain4j，支持 GPT-4/Claude 等
5. **REST API** - 完整的后端接口
6. **前端界面** - 现代化的 Web UI

## 🔧 快速命令

```bash
# 检查环境
java -version
mvn -version
redis-cli ping

# 编译
mvn clean compile

# 运行
mvn spring-boot:run

# 测试 API
curl http://localhost:8080/airt/api/roundtable/health
curl http://localhost:8080/airt/api/roundtable/roles
```

## 📝 TODO (可选增强)

- [ ] WebSocket 实时通信实现
- [ ] 真实 MCP 工具集成 (Jira, GitHub)
- [ ] 决策报告导出 (PDF/Word)
- [ ] 多语言支持
- [ ] 更多角色模板

## 🚀 系统已就绪

所有代码已完成，只需：
1. 安装 Java + Maven + Redis
2. 运行 `mvn spring-boot:run`
3. 打开 `frontend/index.html`

即可使用！
