package com.airt.dto;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Agent 响应 DTO
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AgentResponse {

    /**
     * Agent 实例 ID
     */
    private String agentInstanceId;

    /**
     * 角色名称
     */
    private String roleName;

    /**
     * 角色显示名称
     */
    private String roleDisplayName;

    /**
     * 轮次号
     */
    private Integer round;

    /**
     * 思考过程
     * Agent 的内部推理
     */
    private String thinkingProcess;

    /**
     * 公开响应
     * 展示给用户的内容
     */
    private String publicResponse;

    /**
     * 提取的关键洞察
     */
    @Builder.Default
    private List<String> keyInsights = List.of();

    /**
     * 使用的 MCP 工具调用
     */
    @Builder.Default
    private List<MCPToolCall> toolCalls = List.of();

    /**
     * 响应时间戳
     */
    private long timestamp;

    /**
     * 扩展数据
     */
    @Builder.Default
    private Map<String, Object> metadata = Map.of();

    /**
     * 白板更新（记录员使用）
     */
    private BlackboardUpdate blackboardUpdate;

    /**
     * MCP 工具调用记录
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MCPToolCall {
        private String toolName;
        private String input;
        private String output;
        private long duration;
        private boolean success;
    }

    /**
     * 白板更新
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class BlackboardUpdate {
        /**
         * 新增共识点
         */
        @Builder.Default
        private List<String> newConsensusPoints = List.of();

        /**
         * 新增冲突点
         */
        @Builder.Default
        private List<String> newConflictPoints = List.of();

        /**
         * 新增待解决问题
         */
        @Builder.Default
        private List<String> newPendingQuestions = List.of();

        /**
         * 白板文本摘要（用于显示在记录员的响应中）
         */
        private String summary;
    }
}
