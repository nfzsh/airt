package com.airt.agent;

import com.airt.config.AirtProperties;
import com.airt.mcp.MCPService;
import com.airt.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Agent 工厂
 *
 * 根据角色定义创建 Agent 实例
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentFactory {

    private final AirtProperties airtProperties;
    private final MCPService mcpService;

    /**
     * 为会话创建一组 Agent 实例
     *
     * @param sessionId 会话 ID
     * @param roleIds 角色列表
     * @return Agent 实例列表
     */
    public List<AgentInstance> createAgents(String sessionId, List<String> roleIds) {
        return roleIds.stream()
                .map(roleId -> createAgent(sessionId, roleId))
                .collect(Collectors.toList());
    }

    /**
     * 创建单个 Agent 实例
     *
     * @param sessionId 会话 ID
     * @param roleId 角色 ID
     * @return Agent 实例
     */
    public AgentInstance createAgent(String sessionId, String roleId) {
        // 从 Spring 配置中加载角色定义
        AirtProperties.RoleConfig roleConfig = airtProperties.getRoles().stream()
                .filter(r -> r.getRoleId().equals(roleId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Role definition not found: " + roleId));

        // 转换为 RoleDefinition
        RoleDefinition roleDefinition = toRoleDefinition(roleConfig);

        // 加载 Prompt 模板
        PromptTemplate promptTemplate = null;
        if (roleConfig.getPromptTemplate() != null) {
            promptTemplate = airtProperties.getPromptTemplates().stream()
                    .filter(t -> t.getTemplateId().equals(roleConfig.getPromptTemplate()))
                    .findFirst()
                    .map(this::toPromptTemplate)
                    .orElse(null);
        }

        // 加载 MCP Profile
        MCPProfile mcpProfile = null;
        if (roleConfig.getMcpProfile() != null) {
            mcpProfile = airtProperties.getMcpProfiles().stream()
                    .filter(p -> p.getProfileId().equals(roleConfig.getMcpProfile()))
                    .findFirst()
                    .map(this::toMCPProfile)
                    .orElse(null);
            // 配置权限
            if (mcpProfile != null) {
                mcpService.configureRolePermissions(roleId, mcpProfile);
            }
        }

        // 确定使用的模型
        String model = roleConfig.getRecommendedModel();
        if (model == null) {
            model = "gpt-4"; // 默认模型
        }

        // 创建实例
        return AgentInstance.builder()
                .instanceId(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .roleDefinition(roleDefinition)
                .promptTemplate(promptTemplate)
                .mcpProfile(mcpProfile)
                .model(model)
                .parameters(roleConfig.getParameters())
                .build();
    }

    /**
     * 获取所有可用角色
     *
     * @return 角色定义列表
     */
    public List<RoleDefinition> getAvailableRoles() {
        return airtProperties.getRoles().stream()
                .map(this::toRoleDefinition)
                .collect(Collectors.toList());
    }

    /**
     * 转换配置为 RoleDefinition
     */
    private RoleDefinition toRoleDefinition(AirtProperties.RoleConfig config) {
        return RoleDefinition.builder()
                .roleId(config.getRoleId())
                .displayName(config.getDisplayName())
                .description(config.getDescription())
                .icon(config.getIcon())
                .cognitiveStyle(config.getCognitiveStyle())
                .coreResponsibility(config.getCoreResponsibility())
                .allowedActions(config.getAllowedActions())
                .mcpProfile(config.getMcpProfile())
                .promptTemplate(config.getPromptTemplate())
                .recommendedModel(config.getRecommendedModel())
                .systemRole(config.isSystemRole())
                .parameters(config.getParameters())
                .build();
    }

    /**
     * 转换配置为 PromptTemplate
     */
    private PromptTemplate toPromptTemplate(AirtProperties.PromptTemplateConfig config) {
        return PromptTemplate.builder()
                .templateId(config.getTemplateId())
                .systemPrompt(config.getSystemPrompt())
                .userMessageTemplate(config.getUserMessageTemplate())
                .variables(config.getVariables())
                .build();
    }

    /**
     * 转换配置为 MCPProfile
     */
    private MCPProfile toMCPProfile(AirtProperties.MCPProfileConfig config) {
        List<MCPProfile.MCPCapability> capabilities = config.getCapabilities().stream()
                .map(cap -> MCPProfile.MCPCapability.builder()
                        .name(cap.getName())
                        .description(cap.getDescription())
                        .scope(cap.getScope())
                        .required(cap.isRequired())
                        .config(cap.getConfig())
                        .build())
                .collect(Collectors.toList());

        MCPProfile.MCPRateLimit rateLimit = MCPProfile.MCPRateLimit.builder()
                .perMinute(config.getRateLimit().getPerMinute())
                .perHour(config.getRateLimit().getPerHour())
                .onExceed(config.getRateLimit().getOnExceed())
                .build();

        return MCPProfile.builder()
                .profileId(config.getProfileId())
                .description(config.getDescription())
                .capabilities(capabilities)
                .rateLimit(rateLimit)
                .build();
    }
}
