package com.airt.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Agent 实例模型
 *
 * 这是从 RoleDefinition 创建的运行时实例
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentInstance {

    /**
     * Agent 实例 ID
     */
    private String instanceId;

    /**
     * 所属会话 ID
     */
    private String sessionId;

    /**
     * 角色定义
     */
    private RoleDefinition roleDefinition;

    /**
     * Prompt 模板
     */
    private PromptTemplate promptTemplate;

    /**
     * MCP Profile
     */
    private MCPProfile mcpProfile;

    /**
     * 使用的 LLM 模型
     */
    private String model;

    /**
     * Agent 参数
     */
    @Builder.Default
    private Map<String, Object> parameters = Map.of();

    /**
     * 发言次数
     */
    @Builder.Default
    private int speakCount = 0;

    /**
     * 最后发言时间
     */
    private Long lastSpeakTime;

    /**
     * 是否活跃
     */
    @Builder.Default
    private boolean active = true;
}
