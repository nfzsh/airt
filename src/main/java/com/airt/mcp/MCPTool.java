package com.airt.mcp;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP 工具定义
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MCPTool {

    /**
     * 工具名称
     */
    private String name;

    /**
     * 工具描述
     */
    private String description;

    /**
     * 工具分类
     */
    private String category;

    /**
     * 工具参数定义
     */
    @Builder.Default
    private Map<String, ToolParameter> parameters = Map.of();

    /**
     * 工具参数
     */
    @Data
    @Builder
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ToolParameter {
        private String name;
        private String type;
        private String description;
        private boolean required;
        private Object defaultValue;
    }
}
