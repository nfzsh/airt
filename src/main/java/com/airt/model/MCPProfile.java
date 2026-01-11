package com.airt.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP Profile 配置模型
 *
 * 定义一个角色可以使用的 MCP 能力域
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MCPProfile {

    /**
     * Profile 唯一标识
     */
    private String profileId;

    /**
     * Profile 描述
     */
    private String description;

    /**
     * 能力列表
     */
    @Builder.Default
    private List<MCPCapability> capabilities = List.of();

    /**
     * 速率限制配置
     */
    @Builder.Default
    private MCPRateLimit rateLimit = MCPRateLimit.builder().build();

    /**
     * MCP 能力定义
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MCPCapability {
        /**
         * 能力名称（工具名称）
         */
        private String name;

        /**
         * 能力描述
         */
        private String description;

        /**
         * 作用域
         * 限制该工具可以访问的数据范围
         */
        @Builder.Default
        private List<String> scope = List.of();

        /**
         * 是否必需
         * 标记为必需的能力必须在调用时提供
         */
        private boolean required;

        /**
         * 工具配置
         */
        @Builder.Default
        private Map<String, Object> config = Map.of();
    }

    /**
     * 速率限制配置
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class MCPRateLimit {
        /**
         * 每分钟最大调用次数
         */
        @Builder.Default
        private int perMinute = 10;

        /**
         * 每小时最大调用次数
         */
        @Builder.Default
        private int perHour = 100;

        /**
         * 超出限制时的行为
         * - throw: 抛出异常
         * - queue: 排队等待
         * - skip: 跳过调用
         */
        @Builder.Default
        private String onExceed = "throw";
    }
}
