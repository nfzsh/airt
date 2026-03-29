package com.airt.mcp.tool;

import com.airt.config.AirtProperties;
import com.airt.mcp.ToolExecutor;
import lombok.extern.slf4j.Slf4j;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * HTTP 指标查询工具执行器
 *
 * 通过调用配置的 HTTP API 获取业务/技术指标
 */
@Slf4j
public class HttpMetricsToolExecutor implements ToolExecutor {

    private final AirtProperties.ToolConfig config;
    private final HttpClient httpClient;

    public HttpMetricsToolExecutor(AirtProperties.ToolConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    @Override
    public String getName() {
        return "query_metrics";
    }

    @Override
    public String getDescription() {
        return "Query business or technical metrics (DAU, conversion rate, QPS, latency, error rate, etc.) from configured data sources.";
    }

    @Override
    public String execute(Map<String, Object> parameters) {
        String metric = getStringParam(parameters, "query");
        if (metric == null || metric.isBlank()) {
            // fallback: check "metric" key
            Object m = parameters.get("metric");
            metric = m != null ? m.toString() : null;
        }
        if (metric == null || metric.isBlank()) {
            return "Error: missing required parameter 'query' or 'metric'";
        }

        String endpoint = config != null ? config.getEndpoint() : null;
        if (endpoint == null || endpoint.isBlank()) {
            return buildFallbackMetric(metric);
        }

        try {
            String url = endpoint.contains("{metric}")
                    ? endpoint.replace("{metric}", java.net.URLEncoder.encode(metric, "UTF-8"))
                    : endpoint + "?metric=" + java.net.URLEncoder.encode(metric, "UTF-8");

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                return "Metric [" + metric + "]:\n" + response.body();
            }
            return "Failed to query metric: HTTP " + response.statusCode();
        } catch (Exception e) {
            log.error("Metrics query failed: {}", e.getMessage());
            return "Metrics query error: " + e.getMessage();
        }
    }

    private String buildFallbackMetric(String metric) {
        return String.format("""
                [Metrics - Demo Mode]
                Metric: "%s"
                Note: No metrics endpoint configured. Configure `airt.tools.metrics.endpoint` in application.yml to get real data.
                """, metric);
    }

    private String getStringParam(Map<String, Object> params, String key) {
        Object val = params.get(key);
        return val != null ? val.toString() : null;
    }
}
