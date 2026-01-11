package com.airt.mcp;

import lombok.Builder;
import lombok.Data;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * MCP 工具执行结果
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class MCPToolExecutionResult {

    /**
     * 工具名称
     */
    private String toolName;

    /**
     * 是否成功
     */
    private boolean success;

    /**
     * 执行结果
     */
    private String result;

    /**
     * 错误信息
     */
    private String error;

    /**
     * 执行时长（毫秒）
     */
    private long duration;
}
