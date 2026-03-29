package com.airt.mcp;

import com.airt.config.AirtProperties;
import com.airt.model.MCPProfile;
import com.airt.mcp.tool.*;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MCP 服务
 *
 * 管理所有 MCP 工具的注册、调用和权限控制
 * 支持 ToolExecutor 可插拔实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MCPService {

    private final AirtProperties airtProperties;

    /**
     * 工具注册表（静态元数据）
     */
    private final Map<String, MCPTool> tools = new ConcurrentHashMap<>();

    /**
     * 工具执行器注册表（实际执行逻辑）
     */
    private final Map<String, ToolExecutor> executors = new ConcurrentHashMap<>();

    /**
     * 角色权限映射
     * roleId -> Set of allowed tool names
     */
    private final Map<String, Set<String>> rolePermissions = new ConcurrentHashMap<>();

    /**
     * 速率限制追踪
     */
    private final Map<String, RateLimitTracker> rateLimitTrackers = new ConcurrentHashMap<>();

    /**
     * 初始化默认工具和执行器
     */
    @PostConstruct
    public void init() {
        // 注册默认工具元数据
        registerDefaultTools();
        // 注册工具执行器
        registerDefaultExecutors();
        log.info("Registered {} MCP tools with executors", tools.size());
    }

    /**
     * 注册默认 MCP 工具元数据
     */
    private void registerDefaultTools() {
        registerTool(MCPTool.builder()
                .name("web_search")
                .description("Search the web for current information and facts")
                .category("search")
                .build());

        registerTool(MCPTool.builder()
                .name("search_knowledge_base")
                .description("Search internal knowledge base for documents and reference materials")
                .category("knowledge")
                .build());

        registerTool(MCPTool.builder()
                .name("query_metrics")
                .description("Query business or technical metrics (DAU, QPS, latency, etc.)")
                .category("metrics")
                .build());

        registerTool(MCPTool.builder()
                .name("query_tech_metrics")
                .description("Query technical metrics (QPS, latency, error rate)")
                .category("technical")
                .build());

        registerTool(MCPTool.builder()
                .name("verify_statement")
                .description("Verify the truthfulness of a statement by searching for evidence")
                .category("verification")
                .build());

        // 别名映射：让旧名称也能工作
        registerTool(MCPTool.builder()
                .name("search_internal_knowledge")
                .description("Search internal knowledge base")
                .category("knowledge")
                .build());
        registerTool(MCPTool.builder()
                .name("query_live_data")
                .description("Query real-time data")
                .category("metrics")
                .build());
        registerTool(MCPTool.builder()
                .name("query_business_metrics")
                .description("Query business metrics")
                .category("metrics")
                .build());
    }

    /**
     * 注册默认工具执行器
     */
    private void registerDefaultExecutors() {
        AirtProperties.ToolsConfig toolsConfig = airtProperties.getTools();

        // Web Search
        if (toolsConfig.getWebSearch().isEnabled()) {
            AirtProperties.ToolConfig searchCfg = new AirtProperties.ToolConfig();
            searchCfg.setEnabled(true);
            searchCfg.setProvider(toolsConfig.getWebSearch().getProvider());
            searchCfg.setApiKey(toolsConfig.getWebSearch().getApiKey());
            registerExecutor(new WebSearchToolExecutor(searchCfg));
        }

        // Knowledge Base
        if (toolsConfig.getKnowledgeBase().isEnabled()) {
            AirtProperties.ToolConfig kbCfg = new AirtProperties.ToolConfig();
            kbCfg.setEnabled(true);
            kbCfg.setPaths(toolsConfig.getKnowledgeBase().getPaths());
            registerExecutor(new KnowledgeBaseToolExecutor(kbCfg));
        }

        // Metrics
        if (toolsConfig.getMetrics().isEnabled()) {
            AirtProperties.ToolConfig metricsCfg = new AirtProperties.ToolConfig();
            metricsCfg.setEnabled(true);
            metricsCfg.setEndpoint(toolsConfig.getMetrics().getEndpoint());
            registerExecutor(new HttpMetricsToolExecutor(metricsCfg));
        }

        // Verify Statement (depends on web_search executor)
        AirtProperties.ToolConfig verifyCfg = new AirtProperties.ToolConfig();
        verifyCfg.setEnabled(true);
        registerExecutor(new VerifyStatementToolExecutor(this, verifyCfg));
    }

    /**
     * 配置角色的 MCP 权限
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
     */
    public boolean hasPermission(String roleId, String toolName) {
        Set<String> allowedTools = rolePermissions.get(roleId);
        return allowedTools != null && allowedTools.contains(toolName);
    }

    /**
     * 执行 MCP 工具调用
     */
    public MCPToolExecutionResult execute(String roleId, String toolName, Map<String, Object> parameters) {
        long startTime = System.currentTimeMillis();

        // "system" 角色跳过权限检查（内部工具互调）
        if (!"system".equals(roleId) && !hasPermission(roleId, toolName)) {
            log.warn("Role {} does not have permission to call tool {}", roleId, toolName);
            return MCPToolExecutionResult.builder()
                    .success(false).error("Permission denied: " + toolName).toolName(toolName)
                    .build();
        }

        // 尝试使用 ToolExecutor
        ToolExecutor executor = resolveExecutor(toolName);
        if (executor != null) {
            try {
                log.debug("Executing tool {} for role {} via ToolExecutor", toolName, roleId);
                String result = executor.execute(parameters);
                return MCPToolExecutionResult.builder()
                        .success(true).toolName(toolName).result(result)
                        .duration(System.currentTimeMillis() - startTime).build();
            } catch (Exception e) {
                log.error("ToolExecutor error for {}: {}", toolName, e.getMessage(), e);
                return MCPToolExecutionResult.builder()
                        .success(false).error(e.getMessage()).toolName(toolName)
                        .duration(System.currentTimeMillis() - startTime).build();
            }
        }

        // 工具未找到
        log.warn("No executor found for tool {}", toolName);
        return MCPToolExecutionResult.builder()
                .success(false).error("Tool not found: " + toolName).toolName(toolName)
                .duration(System.currentTimeMillis() - startTime).build();
    }

    /**
     * 解析工具执行器（支持别名）
     */
    private ToolExecutor resolveExecutor(String toolName) {
        ToolExecutor executor = executors.get(toolName);
        if (executor != null) return executor;

        // 别名映射
        return switch (toolName) {
            case "search_internal_knowledge" -> executors.get("search_knowledge_base");
            case "query_live_data", "query_business_metrics", "query_tech_metrics" -> executors.get("query_metrics");
            default -> null;
        };
    }

    /**
     * 注册工具元数据
     */
    public void registerTool(MCPTool tool) {
        tools.put(tool.getName(), tool);
    }

    /**
     * 注册工具执行器
     */
    public void registerExecutor(ToolExecutor executor) {
        executors.put(executor.getName(), executor);
        log.debug("Registered tool executor: {}", executor.getName());
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
            while (!minuteCalls.isEmpty() && now - minuteCalls.peek() > 60_000) minuteCalls.poll();
            while (!hourCalls.isEmpty() && now - hourCalls.peek() > 3_600_000) hourCalls.poll();
            if (minuteCalls.size() >= perMinute || hourCalls.size() >= perHour) return false;
            minuteCalls.offer(now);
            hourCalls.offer(now);
            return true;
        }
    }
}
