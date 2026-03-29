package com.airt.mcp.tool;

import com.airt.config.AirtProperties;
import com.airt.mcp.MCPService;
import com.airt.mcp.ToolExecutor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * 事实验证工具执行器
 *
 * 组合搜索工具验证某个陈述的真实性
 */
@Slf4j
public class VerifyStatementToolExecutor implements ToolExecutor {

    private final MCPService mcpService;
    private final AirtProperties.ToolConfig config;

    public VerifyStatementToolExecutor(MCPService mcpService, AirtProperties.ToolConfig config) {
        this.mcpService = mcpService;
        this.config = config;
    }

    @Override
    public String getName() {
        return "verify_statement";
    }

    @Override
    public String getDescription() {
        return "Verify the truthfulness of a statement by searching for evidence. Returns verification status and supporting sources.";
    }

    @Override
    public String execute(Map<String, Object> parameters) {
        String statement = getStringParam(parameters, "query");
        if (statement == null || statement.isBlank()) {
            Object s = parameters.get("statement");
            statement = s != null ? s.toString() : null;
        }
        if (statement == null || statement.isBlank()) {
            return "Error: missing required parameter 'query' or 'statement'";
        }

        // 使用 web_search 工具搜索验证信息
        try {
            Map<String, Object> searchParams = Map.of("query", "verify fact check: " + statement);
            var searchResult = mcpService.execute("system", "web_search", searchParams);

            if (searchResult.isSuccess()) {
                return String.format("""
                        [Fact Verification]
                        Statement: "%s"

                        Search Results:
                        %s

                        Status: EVIDENCE_FOUND
                        Note: Based on web search results. Please evaluate the evidence yourself.
                        """, statement, searchResult.getResult());
            }

            return String.format("""
                    [Fact Verification]
                    Statement: "%s"
                    Status: UNVERIFIED
                    Reason: Search returned no results
                    Recommendation: Requires manual verification from primary sources
                    """, statement);
        } catch (Exception e) {
            log.error("Verification failed: {}", e.getMessage());
            return String.format("""
                    [Fact Verification]
                    Statement: "%s"
                    Status: ERROR
                    Reason: %s
                    """, statement, e.getMessage());
        }
    }

    private String getStringParam(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }
}
