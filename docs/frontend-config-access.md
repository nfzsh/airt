# 前端访问配置说明

## 配置 API 端点

后端已经创建了配置查询 API，前端可以通过以下端点获取配置：

### 1. 获取所有角色配置
```javascript
// API 端点
GET /airt/api/config/roles

// 前端调用示例
async function loadRoles() {
    const response = await fetch('/airt/api/config/roles');
    const roles = await response.json();
    console.log('所有角色:', roles);

    // 返回数据结构:
    // [
    //   {
    //     roleId: "product_manager",
    //     displayName: "产品经理",
    //     description: "关注用户价值、需求优先级、防止范围膨胀",
    //     icon: "👔",
    //     cognitiveStyle: ["user-value-driven", "tradeoff-sensitive"],
    //     coreResponsibility: ["澄清需求目标", "评估需求优先级"],
    //     allowedActions: ["propose", "challenge"],
    //     mcpProfile: "pm_default",
    //     promptTemplate: "pm_v1",
    //     recommendedModel: "gpt-4",
    //     systemRole: false
    //   },
    //   ...
    // ]
    return roles;
}
```

### 2. 获取单个角色配置
```javascript
// API 端点
GET /airt/api/config/roles/{roleId}

// 前端调用示例
async function getRoleConfig(roleId) {
    const response = await fetch(`/airt/api/config/roles/${roleId}`);
    const role = await response.json();
    return role;
}
```

### 3. 获取 MCP Profile 配置
```javascript
// API 端点
GET /airt/api/config/mcp-profiles

// 前端调用示例
async function loadMCPProfiles() {
    const response = await fetch('/airt/api/config/mcp-profiles');
    const profiles = await response.json();
    console.log('MCP 配置:', profiles);

    // 返回数据结构:
    // [
    //   {
    //     profileId: "pm_default",
    //     description: "产品经理常用信息源",
    //     capabilities: [
    //       {
    //         name: "search_internal_knowledge",
    //         description: "搜索内部知识库",
    //         scope: ["product_docs", "roadmap"],
    //         required: false
    //       }
    //     ],
    //     rateLimit: {
    //       perMinute: 5,
    //       perHour: 100
    //     }
    //   }
    // ]
    return profiles;
}
```

### 4. 获取 Prompt 模板配置
```javascript
// API 端点
GET /airt/api/config/prompt-templates

// 前端调用示例
async function loadPromptTemplates() {
    const response = await fetch('/airt/api/config/prompt-templates');
    const templates = await response.json();
    return templates;
}
```

### 5. 获取系统配置
```javascript
// API 端点
GET /airt/api/config/system

// 前端调用示例
async function loadSystemConfig() {
    const response = await fetch('/airt/api/config/system');
    const config = await response.json();

    // 返回数据结构:
    // {
    //   sessionTimeout: 60,
    //   maxRounds: 50,
    //   godModeEnabled: true,
    //   whisperEnabled: true,
    //   forceVerification: true,
    //   llmRetryMaxAttempts: 3
    // }

    return config;
}
```

## 前端集成示例

### 方式一：在现有 app-new.js 中集成

修改 `frontend/app-new.js` 的 `loadRoles()` 方法：

```javascript
class AIRoundtableApp {
    constructor() {
        this.currentSessionId = null;
        // 更新 API 基础路径
        this.apiBase = '/airt/api';
        this.configBase = '/airt/api/config';
        this.godModeVisible = false;

        this.init();
    }

    async init() {
        this.bindEvents();
        await this.loadRoles();  // 使用新的配置 API
        this.showToast('AI Roundtable 已启动', 'success');
    }

    async loadRoles() {
        try {
            // 使用新的配置 API
            const response = await fetch(`${this.configBase}/roles`);
            if (response.ok) {
                const roles = await response.json();
                this.renderRoleCheckboxes(roles);
            } else {
                console.error('Failed to load roles:', response.statusText);
            }
        } catch (error) {
            console.error('Failed to load roles:', error);
            this.showToast('加载角色配置失败', 'error');
        }
    }

    renderRoleCheckboxes(roles) {
        const container = document.querySelector('.role-selector');
        if (!container) return;

        container.innerHTML = roles.map(role => `
            <label class="role-checkbox">
                <input type="checkbox" name="roles" value="${role.roleId}" ${role.systemRole ? 'checked' : ''}>
                <span class="role-icon">${role.icon || '🤖'}</span>
                <span class="role-name">${role.displayName}</span>
                <small>${role.description || ''}</small>
            </label>
        `).join('');

        // Re-bind checkbox events
        document.querySelectorAll('.role-checkbox input').forEach(checkbox => {
            checkbox.addEventListener('change', (e) => this.updateRoleSelection(e));
        });
    }

    // 其他方法保持不变...
}
```

### 方式二：创建独立的配置管理器

创建 `frontend/config-manager.js`：

```javascript
/**
 * 配置管理器
 * 负责从后端加载和管理所有配置
 */
class ConfigManager {
    constructor() {
        this.configBase = '/airt/api/config';
        this.cache = {
            roles: null,
            mcpProfiles: null,
            promptTemplates: null,
            systemConfig: null
        };
    }

    /**
     * 获取所有角色配置
     */
    async getRoles(forceRefresh = false) {
        if (this.cache.roles && !forceRefresh) {
            return this.cache.roles;
        }

        const response = await fetch(`${this.configBase}/roles`);
        if (!response.ok) {
            throw new Error(`Failed to load roles: ${response.statusText}`);
        }

        this.cache.roles = await response.json();
        return this.cache.roles;
    }

    /**
     * 获取单个角色配置
     */
    async getRole(roleId) {
        const response = await fetch(`${this.configBase}/roles/${roleId}`);
        if (!response.ok) {
            return null;
        }
        return await response.json();
    }

    /**
     * 获取所有 MCP Profile 配置
     */
    async getMCPProfiles(forceRefresh = false) {
        if (this.cache.mcpProfiles && !forceRefresh) {
            return this.cache.mcpProfiles;
        }

        const response = await fetch(`${this.configBase}/mcp-profiles`);
        if (!response.ok) {
            throw new Error(`Failed to load MCP profiles: ${response.statusText}`);
        }

        this.cache.mcpProfiles = await response.json();
        return this.cache.mcpProfiles;
    }

    /**
     * 获取系统配置
     */
    async getSystemConfig(forceRefresh = false) {
        if (this.cache.systemConfig && !forceRefresh) {
            return this.cache.systemConfig;
        }

        const response = await fetch(`${this.configBase}/system`);
        if (!response.ok) {
            throw new Error(`Failed to load system config: ${response.statusText}`);
        }

        this.cache.systemConfig = await response.json();
        return this.cache.systemConfig;
    }

    /**
     * 清除缓存
     */
    clearCache() {
        this.cache = {
            roles: null,
            mcpProfiles: null,
            promptTemplates: null,
            systemConfig: null
        };
    }
}

// 导出单例
const configManager = new ConfigManager();
```

然后在 `index.html` 中引入：

```html
<script src="config-manager.js"></script>
<script src="app-new.js"></script>
```

在 `app-new.js` 中使用：

```javascript
class AIRoundtableApp {
    async init() {
        this.bindEvents();

        // 使用配置管理器加载角色
        try {
            const roles = await configManager.getRoles();
            this.renderRoleCheckboxes(roles);

            // 加载系统配置
            const systemConfig = await configManager.getSystemConfig();
            this.godModeEnabled = systemConfig.godModeEnabled;

            this.showToast('AI Roundtable 已启动', 'success');
        } catch (error) {
            console.error('Failed to load configuration:', error);
            this.showToast('配置加载失败', 'error');
        }
    }
}
```

## 配置热更新

如果需要支持配置热更新，可以添加定时刷新：

```javascript
class AIRoundtableApp {
    constructor() {
        // ...
        this.configRefreshInterval = 5 * 60 * 1000; // 5 分钟
    }

    async init() {
        this.bindEvents();
        await this.loadRoles();
        this.startConfigRefresh();
    }

    startConfigRefresh() {
        setInterval(async () => {
            try {
                await configManager.getRoles(true);
                console.log('Configuration refreshed');
            } catch (error) {
                console.error('Failed to refresh configuration:', error);
            }
        }, this.configRefreshInterval);
    }
}
```

## 注意事项

1. **路径前缀**：所有 API 路径都需要包含 `/airt` 前缀（context-path）

2. **CORS**：如果前后端分离部署，需要配置 CORS

3. **缓存策略**：配置变化不频繁，建议使用缓存减少请求

4. **错误处理**：确保妥善处理配置加载失败的情况

5. **安全性**：配置 API 不包含敏感信息（如 API keys），可以安全暴露给前端
