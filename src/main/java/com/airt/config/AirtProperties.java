package com.airt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AI Roundtable 配置属性
 * 从 application.yml 中加载配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "airt")
public class AirtProperties {

    /**
     * 会话配置
     */
    private SessionConfig session = new SessionConfig();

    /**
     * 角色定义列表
     */
    private List<RoleConfig> roles = new ArrayList<>();

    /**
     * MCP Profile 配置列表
     */
    private List<MCPProfileConfig> mcpProfiles = new ArrayList<>();

    /**
     * Prompt 模板配置列表
     */
    private List<PromptTemplateConfig> promptTemplates = new ArrayList<>();

    /**
     * 会话配置
     */
    @Data
    public static class SessionConfig {
        /**
         * 会话超时时间（分钟）
         */
        private int timeout = 60;

        /**
         * 最大讨论轮数
         */
        private int maxRounds = 50;

        /**
         * LLM 重试配置
         */
        private LLMRetryConfig llmRetry = new LLMRetryConfig();

        /**
         * 上帝模式配置
         */
        private GodModeConfig godMode = new GodModeConfig();
    }

    /**
     * LLM 重试配置
     */
    @Data
    public static class LLMRetryConfig {
        private int maxAttempts = 3;
        private String backoffDelay = "1000ms";
    }

    /**
     * 上帝模式配置
     */
    @Data
    public static class GodModeConfig {
        private boolean enabled = true;
        private boolean whisperEnabled = true;
        private boolean forceVerification = true;
    }

    /**
     * 角色配置
     */
    @Data
    public static class RoleConfig {
        private String roleId;
        private String displayName;
        private String description;
        private String icon;
        private List<String> cognitiveStyle = new ArrayList<>();
        private List<String> coreResponsibility = new ArrayList<>();
        private List<String> allowedActions = new ArrayList<>();
        private String mcpProfile;
        private String promptTemplate;
        private String recommendedModel;
        private boolean systemRole;
        private Map<String, Object> parameters = new HashMap<>();
    }

    /**
     * MCP Profile 配置
     */
    @Data
    public static class MCPProfileConfig {
        private String profileId;
        private String description;
        private List<CapabilityConfig> capabilities = new ArrayList<>();
        private RateLimitConfig rateLimit = new RateLimitConfig();
    }

    /**
     * 能力配置
     */
    @Data
    public static class CapabilityConfig {
        private String name;
        private String description;
        private List<String> scope = new ArrayList<>();
        private boolean required;
        private Map<String, Object> config = new HashMap<>();
    }

    /**
     * 速率限制配置
     */
    @Data
    public static class RateLimitConfig {
        private int perMinute = 5;
        private int perHour = 100;
        private String onExceed = "throw";
    }

    /**
     * Prompt 模板配置
     */
    @Data
    public static class PromptTemplateConfig {
        private String templateId;
        private String systemPrompt;
        private String userMessageTemplate;
        private Map<String, Object> variables = new HashMap<>();
    }
}
