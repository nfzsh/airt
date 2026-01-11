# LLM 配置指南

## 配置文件位置

`src/main/resources/application.yml`

## 支持的提供商

### 1. OpenAI（包括兼容 API）

支持的模型：
- ✅ OpenAI: `gpt-4`, `gpt-3.5-turbo`
- ✅ DeepSeek: `deepseek-chat`, `deepseek-coder`
- ✅ 通义千问: `qwen-turbo`, `qwen-plus`, `qwen-max`
- ✅ 其他 OpenAI 兼容 API

### 2. Anthropic

支持的模型：
- ✅ `claude-3-opus-20240229`
- ✅ `claude-3-sonnet-20240229`
- ✅ `claude-3-haiku-20240307`
- ✅ `claude-3.5-sonnet-20241022`

---

## 配置示例

### 使用 DeepSeek（当前配置）

```yaml
llm:
  openai:
    api-key: ${OPENAI_API_KEY:your-deepseek-api-key}
    base-url: https://api.deepseek.com
    model: deepseek-chat
    timeout: 30
    temperature: 0.7
    max-tokens: 2000
```

### 使用 OpenAI

```yaml
llm:
  openai:
    api-key: ${OPENAI_API_KEY:your-openai-api-key}
    base-url: https://api.openai.com
    model: gpt-4
    timeout: 30
    temperature: 0.7
    max-tokens: 2000
```

### 使用通义千问

```yaml
llm:
  openai:
    api-key: ${DASHSCOPE_API_KEY:your-dashscope-api-key}
    base-url: https://dashscope.aliyuncs.com/compatible-mode/v1
    model: qwen-plus
    timeout: 30
    temperature: 0.7
    max-tokens: 2000
```

### 使用 Anthropic Claude

```yaml
llm:
  anthropic:
    api-key: ${ANTHROPIC_API_KEY:your-anthropic-api-key}
    base-url: https://api.anthropic.com
    model: claude-3.5-sonnet-20241022
    timeout: 30
    temperature: 0.7
    max-tokens: 2000
```

### 同时配置多个提供商

```yaml
llm:
  # OpenAI 及兼容 API（默认）
  openai:
    api-key: ${OPENAI_API_KEY:your-api-key}
    base-url: https://api.openai.com
    model: gpt-4
    timeout: 30
    temperature: 0.7
    max-tokens: 2000

  # Anthropic Claude
  anthropic:
    api-key: ${ANTHROPIC_API_KEY:your-api-key}
    base-url: https://api.anthropic.com
    model: claude-3.5-sonnet-20241022
    timeout: 30
    temperature: 0.7
    max-tokens: 2000
```

---

## 在角色配置中使用不同的模型

在 `application.yml` 的角色配置中指定模型：

```yaml
airt:
  roles:
    - role-id: product_manager
      display-name: 产品经理
      recommended-model: gpt-4  # 使用 OpenAI

    - role-id: devil_advocate
      display-name: 挑战者
      recommended-model: claude-3.5-sonnet-20241022  # 使用 Anthropic

    - role-id: backend_engineer
      display-name: 后端工程师
      recommended-model: deepseek-chat  # 使用 DeepSeek
```

系统会自动根据模型名称选择正确的提供商：
- `gpt-*` → OpenAI
- `claude-*` → Anthropic
- `deepseek-*` → OpenAI（DeepSeek API）
- `qwen-*` 或 `tongyi-*` → OpenAI（通义千问 API）

---

## 环境变量配置

推荐使用环境变量配置 API Key：

```yaml
llm:
  openai:
    api-key: ${OPENAI_API_KEY}  # 从环境变量读取
  anthropic:
    api-key: ${ANTHROPIC_API_KEY}  # 从环境变量读取
```

### Windows
```batch
set OPENAI_API_KEY=sk-your-actual-key
set ANTHROPIC_API_KEY=sk-ant-your-actual-key
```

### Linux/Mac
```bash
export OPENAI_API_KEY=sk-your-actual-key
export ANTHROPIC_API_KEY=sk-ant-your-actual-key
```

### IDEA 运行配置
在 Run Configuration 中添加环境变量：
```
OPENAI_API_KEY=sk-your-actual-key
ANTHROPIC_API_KEY=sk-ant-your-actual-key
```

---

## 参数说明

### OpenAI 配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `api-key` | String | 必填 | API 密钥 |
| `base-url` | String | `https://api.openai.com` | API 基础 URL |
| `model` | String | `gpt-4` | 默认模型名称 |
| `timeout` | Integer | `30` | 超时时间（秒） |
| `temperature` | Double | `0.7` | 温度参数（0-2） |
| `max-tokens` | Integer | `2000` | 最大生成 token 数 |

### Anthropic 配置参数

| 参数 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `api-key` | String | 必填 | API 密钥 |
| `base-url` | String | `https://api.anthropic.com` | API 基础 URL |
| `model` | String | `claude-3-sonnet-20240229` | 默认模型名称 |
| `timeout` | Integer | `30` | 超时时间（秒） |
| `temperature` | Double | `0.7` | 温度参数（0-1） |
| `max-tokens` | Integer | `2000` | 最大生成 token 数 |

---

## 常用 API 端点

### OpenAI
```
https://api.openai.com
https://api.openai.com/v1
```

### DeepSeek
```
https://api.deepseek.com
```

### 通义千问
```
https://dashscope.aliyuncs.com/compatible-mode/v1
```

### Anthropic
```
https://api.anthropic.com
```

---

## 故障排查

### 1. API Key 错误

```
IllegalStateException: OpenAI API key is not configured in application.yml
```

**解决方案**：
- 检查 `application.yml` 中的 `api-key` 配置
- 确保没有使用默认的占位符值（如 `your-api-key-here`）
- 如果使用环境变量，确保环境变量已正确设置

### 2. API 调用超时

```
TimeoutException: Request timed out after 30 seconds
```

**解决方案**：
- 增加 `timeout` 配置值
- 检查网络连接
- 验证 API 端点是否可访问

### 3. 模型不存在

```
InvalidModelException: Model 'xxx' does not exist
```

**解决方案**：
- 确认模型名称正确
- 检查该模型是否在当前提供商的可用列表中
- 确保 API Key 有权访问该模型

### 4. Base URL 错误

```
ConnectException: Failed to connect to xxx
```

**解决方案**：
- 验证 `base-url` 配置正确
- 确保网络可以访问该端点
- 检查是否需要代理配置

---

## 调试日志

启用 DEBUG 日志查看模型创建过程：

```yaml
logging:
  level:
    com.airt.agent.AgentRuntime: DEBUG
```

日志输出示例：
```
DEBUG AgentRuntime - Creating model: deepseek-chat, provider: OPENAI
DEBUG AgentRuntime - OpenAI Model - baseUrl: https://api.deepseek.com, model: deepseek-chat, temperature: 0.7, maxTokens: 2000
```

---

## 最佳实践

1. **使用环境变量**：不要在代码或配置文件中硬编码 API Key
2. **不同环境使用不同的 Key**：开发、测试、生产环境使用不同的 API Key
3. **设置合理的超时时间**：根据模型响应速度调整
4. **监控 API 使用量**：避免超出配额
5. **使用合适的模型**：不是所有任务都需要最昂贵的模型
6. **缓存模型实例**：避免重复创建模型（系统已实现）

---

## 相关文件

- 配置类：`src/main/java/com/airt/config/LlmProperties.java`
- 运行时：`src/main/java/com/airt/agent/AgentRuntime.java`
- 配置文件：`src/main/resources/application.yml`
