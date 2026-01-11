# AIRT 项目结构检查

## 已创建的文件

### Java 源文件
- AirtApplication.java - 主应用类
- config/ConfigurationLoader.java - 配置加载器
- config/JacksonConfig.java - Jackson 配置
- config/WebSocketConfig.java - WebSocket 配置
- controller/RoundtableController.java - REST API 控制器
- dto/AgentResponse.java - Agent 响应 DTO
- mcp/MCPService.java - MCP 服务
- mcp/MCPTool.java - MCP 工具定义
- mcp/MCPToolExecutionResult.java - MCP 执行结果
- model/AgentInstance.java - Agent 实例模型
- model/MCPProfile.java - MCP 配置模型
- model/PromptTemplate.java - Prompt 模板模型
- model/RoleDefinition.java - 角色定义模型
- model/RoundtableSession.java - 会话模型
- model/SessionContext.java - 会话上下文模型
- model/SharedBlackboard.java - 公共白板模型
- agent/AgentFactory.java - Agent 工厂
- agent/AgentRuntime.java - Agent 运行时
- service/AgentSelector.java - Agent 选择器
- service/BlackboardService.java - 白板服务
- service/OrchestratorService.java - 调度器服务
- service/WebSocketService.java - WebSocket 服务

### 配置文件
- pom.xml - Maven 配置
- src/main/resources/application.yml - 应用配置
- src/main/resources/config/roles.yaml - 角色定义
- src/main/resources/config/mcp-profiles.yaml - MCP 配置
- src/main/resources/config/prompt-templates.yaml - Prompt 模板

### 前端文件
- frontend/index.html - 主页面
- frontend/app.js - 原始应用脚本
- frontend/app-new.js - 新的应用脚本
- frontend/styles.css - 样式文件

### 脚本文件
- start.bat - Windows 启动脚本
- test-api-new.bat - API 测试脚本
- README-CN.md - 中文说明

## 检查清单

- [x] 所有 Java 文件已创建
- [x] 依赖已添加到 pom.xml
- [x] 配置文件已创建
- [x] 前端文件已更新
- [x] 未使用的导入已清理
- [ ] Maven 编译测试 (需要 Maven)
- [ ] 应用启动测试 (需要 Java + Redis)
- [ ] API 功能测试 (需要运行中的应用)

## 下一步

如果无法使用 Maven，可以尝试：

1. 使用 IDE (IntelliJ IDEA / Eclipse) 打开项目
2. IDE 会自动下载依赖并编译
3. 在 IDE 中运行 AirtApplication.java

或者：

1. 安装 Maven: https://maven.apache.org/download.cgi
2. 配置环境变量
3. 运行 `mvn spring-boot:run`
