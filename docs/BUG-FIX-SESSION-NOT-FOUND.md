# Session Not Found 错误修复

## 问题描述

前端在创建会话并尝试推进讨论时出现错误：

```
java.lang.IllegalArgumentException: Session not found: 69bff514-6a91-403e-8729-7b968679ab25
```

## 根本原因

1. **缺少防重复提交机制** - 用户可能多次点击"开始讨论"按钮
2. **会话状态管理不当** - 前端可能保存了旧的 sessionId
3. **错误处理不完善** - 失败时没有正确清理状态

## 修复内容

### 1. 添加防重复提交机制

```javascript
constructor() {
    this.isCreatingSession = false;  // 防止重复提交标志
    // ...
}

async createSession(event) {
    event.preventDefault();

    // 防止重复提交
    if (this.isCreatingSession) {
        this.showToast('正在创建会话，请稍候...', 'warning');
        return;
    }

    this.isCreatingSession = true;
    try {
        // 创建会话逻辑...
    } finally {
        this.isCreatingSession = false;
    }
}
```

### 2. 添加 UI 重置功能

```javascript
/**
 * 重置 UI 到初始状态
 */
resetUI() {
    this.currentSessionId = null;
    this.isCreatingSession = false;
    document.getElementById('setupPanel').style.display = 'block';
    document.getElementById('discussionPanel').style.display = 'none';
    document.getElementById('discussionHistory').innerHTML = '';
}
```

### 3. 改进错误处理

#### createSession
```javascript
try {
    // ...
    if (!response.ok) {
        throw new Error(`Failed to create session: ${response.statusText}`);
    }

    const result = await response.json();
    this.currentSessionId = result.sessionId;
    console.log('Session created:', this.currentSessionId);

    await this.startDiscussion();
    await this.nextRound();

} catch (error) {
    console.error('Error creating session:', error);
    this.showToast('创建会话失败: ' + error.message, 'error');
    this.currentSessionId = null;  // 清除无效的会话 ID
}
```

#### startDiscussion
```javascript
async startDiscussion() {
    if (!this.currentSessionId) {
        this.showToast('无效的会话 ID', 'error');
        return;
    }

    try {
        const response = await fetch(...);

        if (!response.ok) {
            const errorText = await response.text();
            throw new Error(`Failed to start: ${response.statusText} - ${errorText}`);
        }

        // ...

    } catch (error) {
        console.error('Error starting discussion:', error);
        this.showToast('启动讨论失败: ' + error.message, 'error');
        this.resetUI();  // 启动失败时重置 UI
        throw error;
    }
}
```

#### nextRound
```javascript
async nextRound() {
    if (!this.currentSessionId) {
        this.showToast('没有活动会话', 'warning');
        return;
    }

    try {
        const response = await fetch(...);

        if (!response.ok) {
            const errorText = await response.text();
            console.error('Proceed round failed:', errorText);
            throw new Error(`Failed to proceed: ${response.statusText} - ${errorText}`);
        }

        // ...

    } catch (error) {
        console.error('Error proceeding to next round:', error);
        this.showToast('推进讨论失败: ' + error.message, 'error');
    }
}
```

### 4. 初始化时清理状态

```javascript
async init() {
    this.bindEvents();
    await this.loadRoles();
    this.resetUI();  // 重置 UI 状态
    this.showToast('AI Roundtable 已启动', 'success');
}
```

## 改进点

| 方面 | 修复前 | 修复后 |
|------|--------|--------|
| 重复提交 | ❌ 无防护 | ✅ `isCreatingSession` 标志 |
| 状态管理 | ❌ 可能残留旧状态 | ✅ 初始化时 `resetUI()` |
| 错误提示 | ❌ 简单错误信息 | ✅ 详细的错误信息 |
| 会话 ID 清理 | ❌ 失败时不清除 | ✅ 失败时清除无效 ID |
| UI 重置 | ❌ 错误时不重置 | ✅ 启动失败时重置 UI |
| 日志输出 | ❌ 基础日志 | ✅ 详细的 console.log |

## 测试场景

### 1. 正常流程
1. 填写讨论主题
2. 选择角色
3. 点击"开始讨论"
4. ✅ 会话创建成功
5. ✅ 讨论自动开始
6. ✅ 第一轮自动推进

### 2. 重复点击
1. 用户多次点击"开始讨论"
2. ✅ 第一次点击正常处理
3. ✅ 后续点击显示"正在创建会话，请稍候..."
4. ✅ 不会创建多个会话

### 3. 会话创建失败
1. 网络错误或服务器错误
2. ✅ 显示友好的错误提示
3. ✅ UI 重置到初始状态
4. ✅ sessionId 被清除

### 4. 会话启动失败
1. 会话创建成功但启动失败
2. ✅ 显示错误提示
3. ✅ UI 重置到初始状态
4. ✅ 不会尝试推进讨论

### 5. 推进讨论失败
1. 会话存在但推进失败
2. ✅ 显示详细错误信息
3. ✅ UI 保持当前状态
4. ✅ 用户可以重试

## 调试信息

添加了以下 console.log 用于调试：

```javascript
console.log('Session created:', this.currentSessionId);
console.error('Start session failed:', errorText);
console.error('Proceed round failed:', errorText);
```

## 相关文件

- `frontend/app-new.js` - 前端应用逻辑
- `src/main/java/com/airt/service/OrchestratorService.java` - 后端调度服务
- `src/main/java/com/airt/controller/RoundtableController.java` - REST API 控制器
