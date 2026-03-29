package com.airt.mcp;

import java.util.Map;

/**
 * 工具执行器接口
 *
 * 每个 MCP 工具实现此接口，支持可插拔的工具注册
 */
public interface ToolExecutor {

    /**
     * 工具名称（对应 MCP capability name）
     */
    String getName();

    /**
     * 工具描述（用于生成 LLM 的 ToolSpecification）
     */
    String getDescription();

    /**
     * 执行工具
     *
     * @param parameters 工具参数
     * @return 执行结果文本
     */
    String execute(Map<String, Object> parameters);
}
