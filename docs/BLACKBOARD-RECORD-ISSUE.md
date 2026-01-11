# 白板记录问题分析与修复

## 问题描述

用户发现公共白板记录完全不正确，显示：
- 🔍 关键洞察：暂无关键洞察
- ✅ 已达成共识：暂无共识点
- ⚔️ 分歧点：📌 点。（显示不完整）
- ❓ 待解决问题：暂无待解决问题

但实际上讨论已经进行了 7 轮，包含了丰富的内容。

## 问题根源分析

### 1. 记录员发言解析问题

记录员的发言内容：
```
更新后的白板状态：
- 共识点：暂无明确共识。
- 争议点：是否应该启动重构项目；Rust是否为合适的重构语言。
- 待验证陈述：需明确挑战者提出的具体风险、成本或技术问题。
```

**原正则表达式问题**：
- 匹配到"暂无明确共识"后，因为包含"暂无"而跳过
- 匹配到"是否应该启动重构项目"和"Rust是否为合适的重构语言"，但因为是列表格式（分号分隔），没有被正确分割
- 匹配到"需明确挑战者提出的具体风险..."但可能因为其他条件被跳过

### 2. 其他角色没有提取关键洞察

**其他 6 个角色的发言都没有被提取为 keyInsights**：
- 主持人：讨论框架
- 产品经理：质疑、标准
- 后端工程师：技术分析
- 前端工程师：技术分析
- 业务专家：ROI 关注
- 挑战者：反直觉观点

**原因**：`extractKeyInsights()` 方法只查找特定格式的关键词（"关键观点"、"Key point"、"Insight"），但实际发言中没有使用这些标记。

### 3. 前端显示问题

"⚔️ 分歧点"显示为"📌 点。"，说明：
- 文本被截断
- 可能是 CSS 样式问题（缺少 word-wrap 或 overflow-wrap）

---

## 修复方案

### 修复 1：改进白板更新解析

**文件**: `src/main/java/com/airt/agent/AgentRuntime.java`

**改进内容**：

1. **过滤空值描述**：
```java
// 排除空值描述
if (!point.isEmpty() && !point.matches("^(暂无|无|没有|没有明确|没有达成).*")) {
    newConsensusPoints.add(point);
}
```

2. **支持列表格式**：
```java
// 分割列表格式的冲突点（分号、换行、数字编号）
String[] conflicts = conflictText.split("[;；]|\\n|\\d+[.、]\\s*");
for (String conflict : conflicts) {
    conflict = conflict.trim();
    if (!conflict.isEmpty() && !conflict.matches("^(暂无|无|没有).*")) {
        newConflictPoints.add(conflict);
    }
}
```

3. **扩展问题关键词**：
```java
Pattern questionPattern = Pattern.compile("(?:❓|待解决|问题|待验证|待澄清|需要|需明确)[:：]?\\s*([^\\n]+)", Pattern.CASE_INSENSITIVE);
```

4. **添加调试日志**：
```java
log.debug("Parsed blackboard update - Consensus: {}, Conflicts: {}, Questions: {}",
        newConsensusPoints, newConflictPoints, newPendingQuestions);
```

**预期结果**：
- 共识点：（仍然为空，因为记录员说"暂无明确共识"）
- 分歧点：["是否应该启动重构项目", "Rust是否为合适的重构语言"]
- 待解决问题：["需明确挑战者提出的具体风险、成本或技术问题"]

---

### 修复 2：修复前端文本显示

**文件**: `frontend/styles.css`

**改进内容**：
```css
.conflict-item {
    border-left: 3px solid #f56565;
    background-color: #fff5f5;
    word-wrap: break-word;      /* 允许长单词换行 */
    overflow-wrap: break-word;  /* 现代浏览器支持 */
}
```

---

### 修复 3：改进关键洞察提取（可选）

如果希望从所有角色的发言中提取关键洞察，可以改进 `extractKeyInsights()` 方法：

```java
/**
 * 提取关键洞察
 */
private List<String> extractKeyInsights(String response) {
    List<String> insights = new ArrayList<>();

    // 1. 查找标记的关键点
    Pattern pattern = Pattern.compile("(?:关键观点|Key point|Insight)[:：](.*?)(?:\\n|$)", Pattern.CASE_INSENSITIVE);
    Matcher matcher = pattern.matcher(response);
    while (matcher.find()) {
        insights.add(matcher.group(1).trim());
    }

    // 2. 如果没有标记的关键点，提取重要的陈述句
    if (insights.isEmpty()) {
        // 查找包含"建议"、"认为"、"需要"等重要动词的句子
        Pattern importantSentence = Pattern.compile("[^。！？]*?(?:建议|认为|需要|提议|质疑)[^。！？]*[。！？]", Pattern.CASE_INSENSITIVE);
        Matcher sentenceMatcher = importantSentence.matcher(response);
        while (sentenceMatcher.find()) {
            String sentence = sentenceMatcher.group().trim();
            if (sentence.length() > 10 && sentence.length() < 200) {
                insights.add(sentence);
            }
        }

        // 限制数量，避免过多
        if (insights.size() > 3) {
            insights = insights.subList(0, 3);
        }
    }

    return insights;
}
```

---

## 测试验证

### 测试用例 1：记录员发言（列表格式）

**输入**：
```
- 争议点：是否应该启动重构项目；Rust是否为合适的重构语言。
- 待验证陈述：需明确挑战者提出的具体风险。
```

**预期输出**：
```json
{
    "newConflictPoints": [
        "是否应该启动重构项目",
        "Rust是否为合适的重构语言"
    ],
    "newPendingQuestions": [
        "需明确挑战者提出的具体风险"
    ]
}
```

### 测试用例 2：过滤空值

**输入**：
```
- 共识点：暂无明确共识
- 争议点：无
```

**预期输出**：
```json
{
    "newConsensusPoints": [],
    "newConflictPoints": []
}
```

### 测试用例 3：数字编号列表

**输入**：
```
- 待解决问题：
  1. 当前网关性能基准数据
  2. 团队Rust熟练度
  3. POC验证结果
```

**预期输出**：
```json
{
    "newPendingQuestions": [
        "当前网关性能基准数据",
        "团队Rust熟练度",
        "POC验证结果"
    ]
}
```

---

## 当前问题的根本原因

**更深层次的问题**：记录员的 LLM Prompt 可能没有正确引导它生成结构化的白板更新格式。

**建议改进记录员 Prompt**（application.yml）：

```yaml
- template-id: scribe_v1
  system-prompt: |
    你是【记录员】。
    你的职责是：实时维护公共白板，记录共识和分歧。

    输出格式要求：
    【白板更新】
    - 共识点：[具体内容，如果没有则留空]
    - 争议点：[使用分号分隔多个点]
    - 待解决问题：[使用分号分隔多个问题]

    示例：
    【白板更新】
    - 共识点：都认为需要先做POC验证；都认为需要明确业务目标
    - 争议点：是否值得投入重构成本；Rust生态是否足够成熟
    - 待解决问题：当前网关性能数据；团队Rust熟练度；迁移时间评估
```

---

## 建议的下一步操作

1. **立即测试**：
   - 重启应用
   - 创建新会话
   - 进行几轮讨论
   - 查看白板是否正确更新

2. **如果仍然不正确**：
   - 查看后端日志：`log.debug("Parsed blackboard update...")` 的输出
   - 查看前端日志：`console.log('Updating blackboard:', blackboard)`
   - 对比实际提取的数据和期望数据

3. **长期优化**：
   - 改进记录员的 Prompt，引导其生成更结构化的输出
   - 考虑使用特殊的标记格式（如 XML 标签）来包裹白板更新内容
   - 或者让记录员调用专门的 MCP 工具来更新白板，而不是文本解析

---

## 文件修改清单

| 文件 | 修改内容 |
|------|---------|
| `src/main/java/com/airt/agent/AgentRuntime.java` | 改进 `parseBlackboardUpdate()` 方法 |
| `frontend/styles.css` | 添加 `word-wrap` 和 `overflow-wrap` 到 `.conflict-item` |
