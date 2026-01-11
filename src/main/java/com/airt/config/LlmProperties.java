package com.airt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * LLM 配置属性
 * 从 application.yml 中加载 LLM 配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "llm")
public class LlmProperties {

    /**
     * 默认提供商（用于没有指定提供商的模型）
     */
    private String defaultProvider = "openai";

    /**
     * OpenAI 兼容配置（包括 DeepSeek、通义千问等）
     */
    private OpenAIConfig openai = new OpenAIConfig();

    /**
     * Anthropic 配置
     */
    private AnthropicConfig anthropic = new AnthropicConfig();

    /**
     * 自定义提供商配置
     */
    private Map<String, ProviderConfig> providers = new HashMap<>();

    /**
     * OpenAI 兼容配置
     */
    @Data
    public static class OpenAIConfig {
        /**
         * API 密钥
         * 支持环境变量：${OPENAI_API_KEY:default-key}
         */
        private String apiKey;

        /**
         * API 基础 URL
         * 默认: https://api.openai.com
         * DeepSeek: https://api.deepseek.com
         * 通义千问: https://dashscope.aliyuncs.com/compatible-mode/v1
         */
        private String baseUrl = "https://api.openai.com";

        /**
         * 默认模型名称
         */
        private String model = "gpt-4";

        /**
         * 超时时间（秒）
         */
        private Integer timeout = 30;

        /**
         * 温度参数（0-2）
         */
        private Double temperature = 0.7;

        /**
         * 最大 token 数
         */
        private Integer maxTokens = 2000;

        /**
         * 获取超时时间作为 Duration
         */
        public Duration getTimeoutAsDuration() {
            return Duration.ofSeconds(timeout != null ? timeout : 30);
        }

        /**
         * 检查是否配置了有效的 API key
         */
        public boolean hasValidApiKey() {
            return apiKey != null && !apiKey.isEmpty()
                    && !apiKey.equals("your-api-key-here")
                    && !apiKey.equals("sk-884cfbbafcd04ecb9864964f7e67b353"); // 示例 key
        }
    }

    /**
     * Anthropic 配置
     */
    @Data
    public static class AnthropicConfig {
        /**
         * API 密钥
         * 支持环境变量：${ANTHROPIC_API_KEY:default-key}
         */
        private String apiKey;

        /**
         * API 基础 URL
         * 默认: https://api.anthropic.com
         */
        private String baseUrl = "https://api.anthropic.com";

        /**
         * 默认模型名称
         */
        private String model = "claude-3-sonnet-20240229";

        /**
         * 超时时间（秒）
         */
        private Integer timeout = 30;

        /**
         * 温度参数（0-1）
         */
        private Double temperature = 0.7;

        /**
         * 最大 token 数
         */
        private Integer maxTokens = 2000;

        /**
         * 获取超时时间作为 Duration
         */
        public Duration getTimeoutAsDuration() {
            return Duration.ofSeconds(timeout != null ? timeout : 30);
        }

        /**
         * 检查是否配置了有效的 API key
         */
        public boolean hasValidApiKey() {
            return apiKey != null && !apiKey.isEmpty()
                    && !apiKey.equals("your-api-key-here")
                    && !apiKey.equals("your-anthropic-key-here");
        }
    }

    /**
     * 通用提供商配置
     */
    @Data
    public static class ProviderConfig {
        private String apiKey;
        private String baseUrl;
        private String model;
        private Integer timeout = 30;
        private Double temperature = 0.7;
        private Integer maxTokens = 2000;
    }

    // ========== 便捷方法 ==========

    /**
     * 根据模型名称判断使用哪个提供商配置
     */
    public ProviderType getProviderType(String modelName) {
        if (modelName == null) {
            return ProviderType.OPENAI;
        }

        String lowerModel = modelName.toLowerCase();

        // OpenAI 模型
        if (lowerModel.startsWith("gpt")) {
            return ProviderType.OPENAI;
        }

        // Claude 模型
        if (lowerModel.startsWith("claude")) {
            return ProviderType.ANTHROPIC;
        }

        // DeepSeek 模型
        if (lowerModel.startsWith("deepseek")) {
            return ProviderType.OPENAI; // DeepSeek 使用 OpenAI 兼容 API
        }

        // 通义千问
        if (lowerModel.startsWith("qwen") || lowerModel.contains("tongyi")) {
            return ProviderType.OPENAI; // 通义千问使用 OpenAI 兼容 API
        }

        // 默认使用 OpenAI
        return ProviderType.OPENAI;
    }

    /**
     * 获取指定提供商的 API key
     */
    public String getApiKey(String modelName) {
        ProviderType type = getProviderType(modelName);

        switch (type) {
            case ANTHROPIC:
                return anthropic.getApiKey();
            case OPENAI:
            default:
                return openai.getApiKey();
        }
    }

    /**
     * 获取指定提供商的 Base URL
     */
    public String getBaseUrl(String modelName) {
        ProviderType type = getProviderType(modelName);

        switch (type) {
            case ANTHROPIC:
                return anthropic.getBaseUrl();
            case OPENAI:
            default:
                return openai.getBaseUrl();
        }
    }

    /**
     * 获取温度参数
     */
    public Double getTemperature(String modelName) {
        ProviderType type = getProviderType(modelName);

        switch (type) {
            case ANTHROPIC:
                return anthropic.getTemperature();
            case OPENAI:
            default:
                return openai.getTemperature();
        }
    }

    /**
     * 获取最大 tokens
     */
    public Integer getMaxTokens(String modelName) {
        ProviderType type = getProviderType(modelName);

        switch (type) {
            case ANTHROPIC:
                return anthropic.getMaxTokens();
            case OPENAI:
            default:
                return openai.getMaxTokens();
        }
    }

    /**
     * 提供商类型枚举
     */
    public enum ProviderType {
        OPENAI,
        ANTHROPIC
    }
}
