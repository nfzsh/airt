package com.airt.config;

import com.airt.model.MCPProfile;
import com.airt.model.PromptTemplate;
import com.airt.model.RoleDefinition;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;

/**
 * 配置加载器
 *
 * 从 classpath 加载角色定义、MCP Profile 和 Prompt 模板配置
 */
@Slf4j
@Component
public class ConfigurationLoader {

    private final Map<String, RoleDefinition> roleDefinitions = new HashMap<>();
    private final Map<String, MCPProfile> mcpProfiles = new HashMap<>();
    private final Map<String, PromptTemplate> promptTemplates = new HashMap<>();

    @PostConstruct
    public void loadConfigurations() {
        loadRoleDefinitions();
        loadMCPProfiles();
        loadPromptTemplates();
        log.info("Configuration loading completed: {} roles, {} MCP profiles, {} prompt templates",
                roleDefinitions.size(), mcpProfiles.size(), promptTemplates.size());
    }

    /**
     * 加载角色定义
     */
    private void loadRoleDefinitions() {
        try {
            // 尝试加载单个配置文件
            loadRoleDefinitionsFromFile("config/roles.yaml");

            // 尝试加载目录下的多个配置文件
            loadRoleDefinitionsFromDirectory("config/roles");
        } catch (Exception e) {
            log.warn("Failed to load role definitions: {}", e.getMessage());
        }
    }

    private void loadRoleDefinitionsFromFile(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                log.debug("Role definition file not found: {}", path);
                return;
            }

            Yaml yaml = createYaml();
            Map<String, Object> data = yaml.load(is);

            if (data != null && data.containsKey("roles")) {
                List<Map<String, Object>> rolesList = (List<Map<String, Object>>) data.get("roles");
                for (Map<String, Object> roleData : rolesList) {
                    RoleDefinition role = parseRoleDefinition(roleData);
                    roleDefinitions.put(role.getRoleId(), role);
                    log.debug("Loaded role definition: {}", role.getRoleId());
                }
            }
        } catch (IOException e) {
            log.warn("Error loading role definitions from {}: {}", path, e.getMessage());
        }
    }

    private void loadRoleDefinitionsFromDirectory(String path) {
        // 简化实现，实际可以扫描目录
        log.debug("Loading role definitions from directory: {}", path);
    }

    private RoleDefinition parseRoleDefinition(Map<String, Object> data) {
        return RoleDefinition.builder()
                .roleId((String) data.get("role_id"))
                .displayName((String) data.get("display_name"))
                .description((String) data.get("description"))
                .cognitiveStyle((List<String>) data.getOrDefault("cognitive_style", List.of()))
                .coreResponsibility((List<String>) data.getOrDefault("core_responsibility", List.of()))
                .allowedActions((List<String>) data.getOrDefault("allowed_actions", List.of()))
                .mcpProfile((String) data.get("mcp_profile"))
                .promptTemplate((String) data.get("prompt_template"))
                .recommendedModel((String) data.get("recommended_model"))
                .systemRole(Boolean.TRUE.equals(data.get("system_role")))
                .icon((String) data.get("icon"))
                .parameters((Map<String, Object>) data.getOrDefault("parameters", Map.of()))
                .build();
    }

    /**
     * 加载 MCP Profiles
     */
    private void loadMCPProfiles() {
        try {
            loadMCPProfilesFromFile("config/mcp-profiles.yaml");
            loadMCPProfilesFromDirectory("config/mcp-profiles");
        } catch (Exception e) {
            log.warn("Failed to load MCP profiles: {}", e.getMessage());
        }
    }

    private void loadMCPProfilesFromFile(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                log.debug("MCP profile file not found: {}", path);
                return;
            }

            Yaml yaml = createYaml();
            Map<String, Object> data = yaml.load(is);

            if (data != null && data.containsKey("profiles")) {
                List<Map<String, Object>> profilesList = (List<Map<String, Object>>) data.get("profiles");
                for (Map<String, Object> profileData : profilesList) {
                    MCPProfile profile = parseMCPProfile(profileData);
                    mcpProfiles.put(profile.getProfileId(), profile);
                    log.debug("Loaded MCP profile: {}", profile.getProfileId());
                }
            }
        } catch (IOException e) {
            log.warn("Error loading MCP profiles from {}: {}", path, e.getMessage());
        }
    }

    private void loadMCPProfilesFromDirectory(String path) {
        log.debug("Loading MCP profiles from directory: {}", path);
    }

    private MCPProfile parseMCPProfile(Map<String, Object> data) {
        List<Map<String, Object>> capabilitiesList = (List<Map<String, Object>>) data.getOrDefault("capabilities", List.of());
        List<MCPProfile.MCPCapability> capabilities = capabilitiesList.stream()
                .map(this::parseMCPCapability)
                .toList();

        Map<String, Object> rateLimitData = (Map<String, Object>) data.getOrDefault("rate_limit", Map.of());
        MCPProfile.MCPRateLimit rateLimit = MCPProfile.MCPRateLimit.builder()
                .perMinute(((Number) rateLimitData.getOrDefault("per_minute", 10)).intValue())
                .perHour(((Number) rateLimitData.getOrDefault("per_hour", 100)).intValue())
                .onExceed((String) rateLimitData.getOrDefault("on_exceed", "throw"))
                .build();

        return MCPProfile.builder()
                .profileId((String) data.get("profile_id"))
                .description((String) data.get("description"))
                .capabilities(capabilities)
                .rateLimit(rateLimit)
                .build();
    }

    private MCPProfile.MCPCapability parseMCPCapability(Map<String, Object> data) {
        return MCPProfile.MCPCapability.builder()
                .name((String) data.get("name"))
                .description((String) data.get("description"))
                .scope((List<String>) data.getOrDefault("scope", List.of()))
                .required(Boolean.TRUE.equals(data.get("required")))
                .config((Map<String, Object>) data.getOrDefault("config", Map.of()))
                .build();
    }

    /**
     * 加载 Prompt 模板
     */
    private void loadPromptTemplates() {
        try {
            loadPromptTemplatesFromFile("config/prompt-templates.yaml");
            loadPromptTemplatesFromDirectory("config/prompts");
        } catch (Exception e) {
            log.warn("Failed to load prompt templates: {}", e.getMessage());
        }
    }

    private void loadPromptTemplatesFromFile(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                log.debug("Prompt template file not found: {}", path);
                return;
            }

            Yaml yaml = createYaml();
            Map<String, Object> data = yaml.load(is);

            if (data != null && data.containsKey("templates")) {
                List<Map<String, Object>> templatesList = (List<Map<String, Object>>) data.get("templates");
                for (Map<String, Object> templateData : templatesList) {
                    PromptTemplate template = parsePromptTemplate(templateData);
                    promptTemplates.put(template.getTemplateId(), template);
                    log.debug("Loaded prompt template: {}", template.getTemplateId());
                }
            }
        } catch (IOException e) {
            log.warn("Error loading prompt templates from {}: {}", path, e.getMessage());
        }
    }

    private void loadPromptTemplatesFromDirectory(String path) {
        log.debug("Loading prompt templates from directory: {}", path);
    }

    private PromptTemplate parsePromptTemplate(Map<String, Object> data) {
        return PromptTemplate.builder()
                .templateId((String) data.get("template_id"))
                .systemPrompt((String) data.get("system_prompt"))
                .userMessageTemplate((String) data.get("user_message_template"))
                .variables((Map<String, Object>) data.getOrDefault("variables", Map.of()))
                .build();
    }

    private Yaml createYaml() {
        // 创建一个简单的 YAML 解析器，用于加载 Map 数据
        // 不需要特殊的 Constructor，因为我们只是加载为 Map
        return new Yaml();
    }

    // Getters
    public RoleDefinition getRoleDefinition(String roleId) {
        return roleDefinitions.get(roleId);
    }

    public Collection<RoleDefinition> getAllRoleDefinitions() {
        return roleDefinitions.values();
    }

    public MCPProfile getMCPProfile(String profileId) {
        return mcpProfiles.get(profileId);
    }

    public PromptTemplate getPromptTemplate(String templateId) {
        return promptTemplates.get(templateId);
    }
}
