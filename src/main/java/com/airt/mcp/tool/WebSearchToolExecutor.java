package com.airt.mcp.tool;

import com.airt.config.AirtProperties;
import com.airt.mcp.ToolExecutor;
import com.airt.config.LlmProperties;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Web 搜索工具执行器
 *
 * 支持 Tavily / SerpAPI / 自定义搜索 API
 */
@Slf4j
public class WebSearchToolExecutor implements ToolExecutor {

    private final AirtProperties.ToolConfig config;
    private final HttpClient httpClient;

    public WebSearchToolExecutor(AirtProperties.ToolConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public String getName() {
        return "web_search";
    }

    @Override
    public String getDescription() {
        return "Search the web for current information, news, and facts. Use this to verify claims or find up-to-date data.";
    }

    @Override
    public String execute(Map<String, Object> parameters) {
        String query = getStringParam(parameters, "query");
        if (query == null || query.isBlank()) {
            return "Error: missing required parameter 'query'";
        }

        String provider = config != null && config.getProvider() != null ? config.getProvider() : "tavily";
        String apiKey = config != null ? config.getApiKey() : null;

        if (apiKey == null || apiKey.isBlank() || apiKey.startsWith("$")) {
            log.warn("Web search API key not configured, returning fallback");
            return buildFallbackResult(query);
        }

        try {
            return switch (provider) {
                case "serpapi" -> executeSerpApi(query, apiKey);
                case "tavily" -> executeTavily(query, apiKey);
                default -> executeTavily(query, apiKey);
            };
        } catch (Exception e) {
            log.error("Web search failed: {}", e.getMessage());
            return "Search failed: " + e.getMessage();
        }
    }

    private String executeTavily(String query, String apiKey) throws Exception {
        String json = String.format("{\"query\":\"%s\",\"max_results\":3,\"include_answer\":true}",
                query.replace("\"", "\\\""));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.tavily.com/search"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("Tavily API returned " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    private String executeSerpApi(String query, String apiKey) throws Exception {
        String url = String.format("https://serpapi.com/search.json?q=%s&api_key=%s&num=3",
                java.net.URLEncoder.encode(query, "UTF-8"), apiKey);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new RuntimeException("SerpAPI returned " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    private String buildFallbackResult(String query) {
        return String.format("""
                [Web Search - Demo Mode]
                Query: "%s"
                Note: API key not configured. Configure `airt.tools.web-search.api-key` in application.yml.
                To get real results, set up a Tavily or SerpAPI key.
                """, query);
    }

    private String getStringParam(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }
}
