package com.airt.controller;

import com.airt.config.AirtProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 配置查询控制器
 * 提供给前端的配置查询接口
 */
@Slf4j
@RestController
@RequestMapping("/api/config")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class ConfigController {

    private final AirtProperties airtProperties;

    /**
     * 获取所有角色配置
     * 前端调用示例: GET /airt/api/config/roles
     */
    @GetMapping("/roles")
    public ResponseEntity<List<RoleConfigDTO>> getRoles() {
        List<RoleConfigDTO> roles = airtProperties.getRoles().stream()
                .map(this::toRoleConfigDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(roles);
    }

    /**
     * 获取单个角色配置
     * 前端调用示例: GET /airt/api/config/roles/product_manager
     */
    @GetMapping("/roles/{roleId}")
    public ResponseEntity<RoleConfigDTO> getRole(@PathVariable String roleId) {
        return airtProperties.getRoles().stream()
                .filter(role -> roleId.equals(role.getRoleId()))
                .findFirst()
                .map(this::toRoleConfigDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取所有 MCP Profile 配置
     * 前端调用示例: GET /airt/api/config/mcp-profiles
     */
    @GetMapping("/mcp-profiles")
    public ResponseEntity<List<MCPProfileConfigDTO>> getMCPProfiles() {
        List<MCPProfileConfigDTO> profiles = airtProperties.getMcpProfiles().stream()
                .map(this::toMCPProfileConfigDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(profiles);
    }

    /**
     * 获取单个 MCP Profile 配置
     * 前端调用示例: GET /airt/api/config/mcp-profiles/pm_default
     */
    @GetMapping("/mcp-profiles/{profileId}")
    public ResponseEntity<MCPProfileConfigDTO> getMCPProfile(@PathVariable String profileId) {
        return airtProperties.getMcpProfiles().stream()
                .filter(profile -> profileId.equals(profile.getProfileId()))
                .findFirst()
                .map(this::toMCPProfileConfigDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取所有 Prompt 模板配置
     * 前端调用示例: GET /airt/api/config/prompt-templates
     */
    @GetMapping("/prompt-templates")
    public ResponseEntity<List<PromptTemplateConfigDTO>> getPromptTemplates() {
        List<PromptTemplateConfigDTO> templates = airtProperties.getPromptTemplates().stream()
                .map(this::toPromptTemplateConfigDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(templates);
    }

    /**
     * 获取单个 Prompt 模板配置
     * 前端调用示例: GET /airt/api/config/prompt-templates/pm_v1
     */
    @GetMapping("/prompt-templates/{templateId}")
    public ResponseEntity<PromptTemplateConfigDTO> getPromptTemplate(@PathVariable String templateId) {
        return airtProperties.getPromptTemplates().stream()
                .filter(template -> templateId.equals(template.getTemplateId()))
                .findFirst()
                .map(this::toPromptTemplateConfigDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取系统配置
     * 前端调用示例: GET /airt/api/config/system
     */
    @GetMapping("/system")
    public ResponseEntity<SystemConfigDTO> getSystemConfig() {
        SystemConfigDTO config = SystemConfigDTO.builder()
                .sessionTimeout(airtProperties.getSession().getTimeout())
                .maxRounds(airtProperties.getSession().getMaxRounds())
                .godModeEnabled(airtProperties.getSession().getGodMode().isEnabled())
                .whisperEnabled(airtProperties.getSession().getGodMode().isWhisperEnabled())
                .forceVerification(airtProperties.getSession().getGodMode().isForceVerification())
                .llmRetryMaxAttempts(airtProperties.getSession().getLlmRetry().getMaxAttempts())
                .build();
        return ResponseEntity.ok(config);
    }

    // 转换方法
    private RoleConfigDTO toRoleConfigDTO(AirtProperties.RoleConfig role) {
        return RoleConfigDTO.builder()
                .roleId(role.getRoleId())
                .displayName(role.getDisplayName())
                .description(role.getDescription())
                .icon(role.getIcon())
                .cognitiveStyle(role.getCognitiveStyle())
                .coreResponsibility(role.getCoreResponsibility())
                .allowedActions(role.getAllowedActions())
                .mcpProfile(role.getMcpProfile())
                .promptTemplate(role.getPromptTemplate())
                .recommendedModel(role.getRecommendedModel())
                .systemRole(role.isSystemRole())
                .parameters(role.getParameters())
                .build();
    }

    private MCPProfileConfigDTO toMCPProfileConfigDTO(AirtProperties.MCPProfileConfig profile) {
        MCPProfileConfigDTO.MCPProfileConfigDTOBuilder builder = MCPProfileConfigDTO.builder()
                .profileId(profile.getProfileId())
                .description(profile.getDescription())
                .capabilities(profile.getCapabilities() != null ? profile.getCapabilities().stream()
                        .map(cap -> CapabilityDTO.builder()
                                .name(cap.getName())
                                .description(cap.getDescription())
                                .scope(cap.getScope())
                                .required(cap.isRequired())
                                .config(cap.getConfig())
                                .build())
                        .collect(Collectors.toList()) : List.of());

        if (profile.getRateLimit() != null) {
            builder.rateLimit(RateLimitDTO.builder()
                    .perMinute(profile.getRateLimit().getPerMinute())
                    .perHour(profile.getRateLimit().getPerHour())
                    .onExceed(profile.getRateLimit().getOnExceed())
                    .build());
        }

        return builder.build();
    }

    private PromptTemplateConfigDTO toPromptTemplateConfigDTO(AirtProperties.PromptTemplateConfig template) {
        return PromptTemplateConfigDTO.builder()
                .templateId(template.getTemplateId())
                .systemPrompt(template.getSystemPrompt())
                .userMessageTemplate(template.getUserMessageTemplate())
                .variables(template.getVariables())
                .build();
    }

    // DTO 类定义
    @lombok.Data
    @lombok.Builder
    public static class RoleConfigDTO {
        @JsonProperty("roleId")
        private String roleId;

        @JsonProperty("displayName")
        private String displayName;

        @JsonProperty("description")
        private String description;

        @JsonProperty("icon")
        private String icon;

        @JsonProperty("cognitiveStyle")
        private List<String> cognitiveStyle;

        @JsonProperty("coreResponsibility")
        private List<String> coreResponsibility;

        @JsonProperty("allowedActions")
        private List<String> allowedActions;

        @JsonProperty("mcpProfile")
        private String mcpProfile;

        @JsonProperty("promptTemplate")
        private String promptTemplate;

        @JsonProperty("recommendedModel")
        private String recommendedModel;

        @JsonProperty("systemRole")
        private boolean systemRole;

        @JsonProperty("parameters")
        private Map<String, Object> parameters;
    }

    @lombok.Data
    @lombok.Builder
    public static class MCPProfileConfigDTO {
        private String profileId;
        private String description;
        private List<CapabilityDTO> capabilities;
        private RateLimitDTO rateLimit;
    }

    @lombok.Data
    @lombok.Builder
    public static class CapabilityDTO {
        private String name;
        private String description;
        private List<String> scope;
        private boolean required;
        private Map<String, Object> config;
    }

    @lombok.Data
    @lombok.Builder
    public static class RateLimitDTO {
        private int perMinute;
        private int perHour;
        private String onExceed;
    }

    @lombok.Data
    @lombok.Builder
    public static class PromptTemplateConfigDTO {
        private String templateId;
        private String systemPrompt;
        private String userMessageTemplate;
        private Map<String, Object> variables;
    }

    @lombok.Data
    @lombok.Builder
    public static class SystemConfigDTO {
        private int sessionTimeout;
        private int maxRounds;
        private boolean godModeEnabled;
        private boolean whisperEnabled;
        private boolean forceVerification;
        private int llmRetryMaxAttempts;
    }
}
