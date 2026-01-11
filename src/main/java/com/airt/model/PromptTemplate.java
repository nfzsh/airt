package com.airt.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Prompt 模板模型
 *
 * 定义角色的系统提示词模板
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PromptTemplate {

    /**
     * 模板 ID
     */
    private String templateId;

    /**
     * 系统提示词模板
     * 支持变量占位符，如 {ROLE_NAME}, {CORE_GOAL} 等
     */
    private String systemPrompt;

    /**
     * 用户消息模板
     */
    private String userMessageTemplate;

    /**
     * 变量定义
     * 定义模板中支持的变量及其默认值
     */
    @Builder.Default
    private java.util.Map<String, Object> variables = new java.util.HashMap<>();

    /**
     * 渲染 Prompt
     *
     * @param context 上下文变量
     * @return 渲染后的提示词
     */
    public String render(java.util.Map<String, Object> context) {
        String result = systemPrompt;

        // 替换变量
        if (context != null) {
            for (java.util.Map.Entry<String, Object> entry : context.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                if (result.contains(placeholder)) {
                    result = result.replace(placeholder, String.valueOf(entry.getValue()));
                }
            }
        }

        // 替换默认变量
        if (variables != null) {
            for (java.util.Map.Entry<String, Object> entry : variables.entrySet()) {
                String placeholder = "{" + entry.getKey() + "}";
                if (result.contains(placeholder)) {
                    result = result.replace(placeholder, String.valueOf(entry.getValue()));
                }
            }
        }

        return result;
    }
}
