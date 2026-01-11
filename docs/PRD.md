# AI Roundtable (AIRT) - 产品需求文档 (PRD)

## 1. 产品定义

### 1.1 产品名称
**AI Roundtable (AIRT)** - 智能认知圆桌

### 1.2 核心定位
一个"人在环路（Human-in-the-loop）"的群体智能思考系统。

### 1.3 价值主张
通过多智能体协作与对抗，将单点 AI 的"生成能力"转化为系统的"认知能力"，为人类决策者提供去偏见、多视角的深度分析。

---

## 2. 核心功能模块

### 2.1 角色体系 (The Cast)

系统预置不同"认知模型"的 Agent，而非简单的 Prompt 差异。

| 角色 (Role) | 核心职责 (Core Responsibility) | 行为特征 (Behavior) |
|---|---|---|
| **Moderator (主持人)** | 控场、归纳、推进议程 | 冷静、客观、关注时间线 |
| **Domain Expert (专家)** | 提供基于知识库的深度见解 | 严谨、引用数据 (调用 MCP) |
| **Devil's Advocate (挑战者)** | 寻找逻辑漏洞、提出反直觉观点 | 尖锐、批判性、不论资排辈 |
| **Product/Biz (价值官)** | 评估可行性、ROI、商业价值 | 务实、关注落地 |
| **Scribe (记录员)** | 实时维护"公共白板" | 结构化、不发言只记录 |

### 2.2 核心交互：动态认知圆桌

#### A. 议程设置 (Setup Phase)

- **上下文注入**：用户上传文档或输入 Topic
- **MCP 预加载**：系统自动调用 MCP (如 Jira, GitHub, 内部 Wiki) 抓取相关背景数据
- **选角**：用户或系统自动挑选 3-5 个适合该议题的 Agent

#### B. 讨论进行时 (The Fishbowl View)

界面不仅是聊天窗口，而是 **"左侧讨论流 + 右侧认知板"**。

##### 公共白板 (Shared Blackboard)

- **位置**：右侧常驻区域，实时更新
- **显示内容**：
  - 当前共识
  - 主要分歧点
  - 待验证事实
- **价值**：防止 AI 车轮战，强制 AI 基于"当前已知"发言

##### 上帝视角控制台 (God Mode)

- **暂停/继续**：控制讨论节奏
- **定向提问**：点击某个 Agent 头像，"@RiskAgent，你对刚才专家的乐观估计怎么看？"
- **耳语 (Whisper)**：给 Moderator 发私信，"下一轮讨论聚焦在成本上，不要谈技术了"
- **强制 MCP**：选中一句话，点击"Verify"，强制触发搜索/查询

### 2.3 产出物 (Artifacts)

会议结束不只是一段总结，而是一份 **结构化决策包**：

- **Executive Summary**：一页纸结论
- **Conflict Map**：哪些点达成了共识，哪些点由于基本假设不同永远无法达成共识
- **Fact Log**：讨论中引用的所有数据源及链接

---

## 3. 核心升级点（优化版）

### 3.1 角色体系升级

| 维度 | 当前方案 | 优化后方案 |
|------|----------|------------|
| **角色** | 固定角色集合 | **角色 = 可配置认知插件** |
| **MCP** | 全局工具 | **角色 → MCP Capability Map** |
| **Prompt** | 模板化 | **Prompt = 角色契约 + 能力声明** |
| **Orchestrator** | 控流程 | **Orchestrator = 权限 + 能力裁决者** |
| **扩展性** | 新角色要改代码 | **新角色 = 配置 + Prompt + MCP 绑定** |

> **核心思想**：Agent 不再是"会说话的 Prompt"，而是"带权限的认知实体"

---

## 4. 角色定义标准模型

### 4.1 角色定义结构

```yaml
role_id: product_manager
display_name: 产品经理
cognitive_style:
  - user_value_driven
  - tradeoff_sensitive
  - scope_control
core_responsibility:
  - 澄清需求目标
  - 评估需求优先级
  - 防止过度设计
allowed_actions:
  - propose
  - challenge
  - request_fact_check
mcp_profile: pm_default
prompt_template: pm_v1
```

**关键点说明**：

- `cognitive_style`：给 LLM 一个"思维偏置标签"
- `allowed_actions`：后端真实生效，不是给模型看的
- `mcp_profile`：MCP 权限绑定

### 4.2 会议创建时的角色选择

```json
POST /roundtable/session/create
{
  "topic": "是否要重构订单系统",
  "roles": [
    "product_manager",
    "backend_engineer",
    "frontend_engineer",
    "business_owner",
    "devil_advocate",
    "moderator",
    "scribe"
  ]
}
```

**系统行为**：

1. 加载 `role-definition`
2. 创建 Agent 实例
3. 注入 **角色专属 Prompt + MCP 权限**
4. 注册到 FSM

---

## 5. MCP 深度优化

### 5.1 核心原则

> **Agent 只能调用"它被授权的 MCP 能力"**

### 5.2 MCP Profile（能力域配置）

#### 产品经理的 MCP

```yaml
profile_id: pm_default
description: 产品经理常用信息源
capabilities:
  - name: search_internal_knowledge
    scope:
      - product_docs
      - roadmap
  - name: query_metrics
    scope:
      - DAU
      - conversion_rate
  - name: verify_statement
    enabled: true
rate_limit:
  per_minute: 5
```

#### 后端工程师的 MCP

```yaml
profile_id: backend_eng
capabilities:
  - name: query_live_data
    scope:
      - qps
      - latency
      - error_rate
  - name: search_internal_knowledge
    scope:
      - architecture_docs
      - ADR
```

#### 挑战者的 MCP

```yaml
verify_statement: required
```

> 强制：每次反驳必须带证据

### 5.3 Orchestrator = MCP 裁决者

```java
if (!mcpPolicy.canInvoke(agent, tool)) {
    throw new ForbiddenToolException(
        agent.getRole() + " is not allowed to call " + tool
    );
}
```

**这是企业级系统的"护城河"**

---

## 6. Prompt 结构优化

### 6.1 新 Prompt 模板

```text
[System]
你是【产品经理 Agent】。
你的职责是：澄清需求目标，防止范围膨胀，平衡成本与收益。

你【可以使用】以下信息能力：
- 内部产品文档查询
- 指标数据查询（DAU、转化率）

你【不能使用】：
- 架构实现细节
- 底层技术性能推导

如果你需要不在权限内的信息，你必须明确指出"信息缺失"。

[Constraints]
- 不允许对未验证的技术指标下结论
- 不允许重复公共白板中的已共识观点
```

**这会极大降低幻觉率**
**同时让不同角色"真的不一样"**

---

## 7. Orchestrator 智能调度

### 7.1 角色感知调度

不再只是"轮流发言"，而是：

```java
Agent next = selector.choose(
    history,
    blackboard,
    strategy = {
        if (conflictOn("cost")) prefer("product_manager");
        if (factMissing()) prefer("domain_expert");
        if (groupthinkDetected()) force("devil_advocate");
    }
);
```

### 7.2 自动触发角色（无需人干预）

| 场景 | 自动行为 |
|------|----------|
| 观点高度一致 | 强制 Devil's Advocate |
| 数据缺失 | Domain Expert |
| 讨论发散 | Moderator |
| 白板混乱 | Scribe |

---

## 8. 应用场景

### 8.1 架构/技术评审会议

- 代替 5 个人先吵一轮
- 人只做最终裁决

### 8.2 PRD/需求评审

- PM vs RD vs Biz 自动对抗
- 输出「不可调和分歧点」

### 8.3 事故复盘（非常适合）

- SRE / RD / 管理者角色
- MCP 拉日志/指标
- Devil's Advocate 专找系统性问题

### 8.4 新人培训

- 看一场"高质量思考是如何发生的"

---

## 9. 用户故事

### 9.1 作为一个产品经理
我希望能够创建一个多角色讨论会，包含 PM、RD、FE、业务等角色，以便从不同角度评估需求。

### 9.2 作为一个技术负责人
我希望每个角色只能访问其权限范围内的信息源，以免产生不专业的建议。

### 9.3 作为一个决策者
我希望在讨论过程中随时可以暂停、定向提问或强制验证，以便掌控讨论方向。

### 9.4 作为一个团队成员
我希望看到结构化的决策报告，包含共识点、分歧点和事实依据，以便快速理解讨论结果。

---

## 10. 非功能需求

### 10.1 性能要求
- 讨论响应时间 < 10秒
- WebSocket 实时推送延迟 < 500ms
- 支持至少 10 个并发讨论会话

### 10.2 可扩展性
- 新增角色只需配置，无需修改代码
- MCP 工具可插拔
- Prompt 模板外部化

### 10.3 可用性
- 界面直观，学习成本低
- 支持 SSE 流式输出
- 讨论过程可视化

---

## 11. 成功指标

- 用户满意度：≥ 4.5/5
- 讨论质量：80% 的用户认为系统提供了新视角
- 效率提升：比人工会议节省 60% 时间
- 决策准确性：建议采纳率 ≥ 70%
