package com.airt.mcp;

import com.airt.model.MCPProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 服务
 *
 * 管理所有 MCP 工具的注册、调用和权限控制
 */
@Slf4j
@Service
public class MCPService {

    /**
     * 工具注册表
     */
    private final Map<String, MCPTool> tools = new ConcurrentHashMap<>();

    /**
     * 角色权限映射
     * roleId -> Set of allowed tool names
     */
    private final Map<String, Set<String>> rolePermissions = new ConcurrentHashMap<>();

    /**
     * 速率限制追踪
     */
    private final Map<String, RateLimitTracker> rateLimitTrackers = new ConcurrentHashMap<>();

    public MCPService() {
        // 注册默认工具
        registerDefaultTools();
    }

    /**
     * 注册默认 MCP 工具
     */
    private void registerDefaultTools() {
        // 搜索工具
        registerTool(MCPTool.builder()
                .name("search_knowledge_base")
                .description("搜索内部知识库，查找相关文档和信息")
                .category("knowledge")
                .build());

        // 查询指标工具
        registerTool(MCPTool.builder()
                .name("query_metrics")
                .description("查询业务指标数据，如DAU、转化率等")
                .category("metrics")
                .build());

        // 查询技术指标工具
        registerTool(MCPTool.builder()
                .name("query_tech_metrics")
                .description("查询技术指标，如QPS、延迟、错误率等")
                .category("technical")
                .build());

        // 验证陈述工具
        registerTool(MCPTool.builder()
                .name("verify_statement")
                .description("验证某个陈述的真实性，查找证据")
                .category("verification")
                .build());

        log.info("Registered {} default MCP tools", tools.size());
    }

    /**
     * 配置角色的 MCP 权限
     *
     * @param roleId 角色 ID
     * @param profile MCP Profile
     */
    public void configureRolePermissions(String roleId, MCPProfile profile) {
        Set<String> allowedTools = new HashSet<>();

        for (MCPProfile.MCPCapability capability : profile.getCapabilities()) {
            allowedTools.add(capability.getName());
        }

        rolePermissions.put(roleId, allowedTools);
        log.info("Configured permissions for role {}: {} tools", roleId, allowedTools.size());
    }

    /**
     * 检查角色是否有权限调用某个工具
     *
     * @param roleId 角色 ID
     * @param toolName 工具名称
     * @return 是否有权限
     */
    public boolean hasPermission(String roleId, String toolName) {
        Set<String> allowedTools = rolePermissions.get(roleId);
        return allowedTools != null && allowedTools.contains(toolName);
    }

    /**
     * 检查并执行速率限制
     *
     * @param roleId 角色 ID
     * @param toolName 工具名称
     * @param profile MCP Profile
     * @return 是否允许调用
     */
    public boolean checkRateLimit(String roleId, String toolName, MCPProfile profile) {
        String key = roleId + ":" + toolName;
        RateLimitTracker tracker = rateLimitTrackers.computeIfAbsent(
                key, k -> new RateLimitTracker());

        MCPProfile.MCPRateLimit rateLimit = profile.getRateLimit();
        return tracker.check(rateLimit.getPerMinute(), rateLimit.getPerHour());
    }

    /**
     * 执行 MCP 工具调用
     *
     * @param roleId 角色 ID
     * @param toolName 工具名称
     * @param parameters 参数
     * @return 执行结果
     */
    public MCPToolExecutionResult execute(String roleId, String toolName, Map<String, Object> parameters) {
        long startTime = System.currentTimeMillis();

        // 检查权限
        if (!hasPermission(roleId, toolName)) {
            log.warn("Role {} does not have permission to call tool {}", roleId, toolName);
            return MCPToolExecutionResult.builder()
                    .success(false)
                    .error("Permission denied: " + toolName)
                    .toolName(toolName)
                    .build();
        }

        // 获取工具
        MCPTool tool = tools.get(toolName);
        if (tool == null) {
            log.warn("Tool {} not found", toolName);
            return MCPToolExecutionResult.builder()
                    .success(false)
                    .error("Tool not found: " + toolName)
                    .toolName(toolName)
                    .build();
        }

        // 执行工具
        try {
            log.debug("Executing tool {} for role {} with parameters: {}", toolName, roleId, parameters);
            String result = executeTool(tool, parameters);

            return MCPToolExecutionResult.builder()
                    .success(true)
                    .toolName(toolName)
                    .result(result)
                    .duration(System.currentTimeMillis() - startTime)
                    .build();
        } catch (Exception e) {
            log.error("Error executing tool {}: {}", toolName, e.getMessage(), e);
            return MCPToolExecutionResult.builder()
                    .success(false)
                    .error(e.getMessage())
                    .toolName(toolName)
                    .build();
        }
    }

    /**
     * 实际执行工具
     */
    private String executeTool(MCPTool tool, Map<String, Object> parameters) {
        // 这里是模拟实现，实际应该调用真实的 MCP 服务
        // 可以集成 langchain4j 的 ToolSpecification

        switch (tool.getName()) {
            case "search_knowledge_base":
                return executeKnowledgeSearch(parameters);
            case "query_metrics":
                return executeMetricsQuery(parameters);
            case "query_tech_metrics":
                return executeTechMetricsQuery(parameters);
            case "verify_statement":
                return executeStatementVerification(parameters);
            default:
                return "Tool execution not implemented: " + tool.getName();
        }
    }

    private String executeKnowledgeSearch(Map<String, Object> parameters) {
        String query = (String) parameters.get("query");
        // 模拟返回
        return "Found 3 relevant documents for query: " + query + "\n" +
                "- Document 1: Architecture overview\n" +
                "- Document 2: API design guidelines\n" +
                "- Document 3: Best practices";
    }

    private String executeMetricsQuery(Map<String, Object> parameters) {
        String metric = (String) parameters.get("metric");
        // 模拟返回
        return String.format("Current %s: 15,432 (↑12% vs last week)", metric);
    }

    private String executeTechMetricsQuery(Map<String, Object> parameters) {
        String metric = (String) parameters.get("metric");
        // 模拟返回
        return String.format("Current %s: 45ms (p95), 12ms (p50)", metric);
    }

    private String executeStatementVerification(Map<String, Object> parameters) {
        String statement = (String) parameters.get("statement");
        // 模拟返回
        return "Statement: \"" + statement + "\"\n" +
                "Status: UNVERIFIED\n" +
                "Confidence: LOW\n" +
                "Recommendation: Requires primary source verification";
    }

    /**
     * 注册工具
     */
    public void registerTool(MCPTool tool) {
        tools.put(tool.getName(), tool);
        log.debug("Registered MCP tool: {}", tool.getName());
    }

    /**
     * 获取所有可用工具
     */
    public Collection<MCPTool> getAllTools() {
        return tools.values();
    }

    /**
     * 速率限制追踪器
     */
    private static class RateLimitTracker {
        private final Queue<Long> minuteCalls = new LinkedList<>();
        private final Queue<Long> hourCalls = new LinkedList<>();

        public synchronized boolean check(int perMinute, int perHour) {
            long now = System.currentTimeMillis();

            // 清理过期的调用记录
            while (!minuteCalls.isEmpty() && now - minuteCalls.peek() > 60_000) {
                minuteCalls.poll();
            }
            while (!hourCalls.isEmpty() && now - hourCalls.peek() > 3_600_000) {
                hourCalls.poll();
            }

            // 检查限制
            if (minuteCalls.size() >= perMinute || hourCalls.size() >= perHour) {
                return false;
            }

            // 记录本次调用
            minuteCalls.offer(now);
            hourCalls.offer(now);
            return true;
        }
    }
}
