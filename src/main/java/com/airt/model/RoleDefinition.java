package com.airt.model;

import lombok.Data;
import lombok.Builder;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 角色定义模型
 *
 * 这是角色的"模板"，定义了一个角色的基本属性、能力和约束
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleDefinition {

    /**
     * 角色唯一标识
     */
    private String roleId;

    /**
     * 角色显示名称
     */
    private String displayName;

    /**
     * 角色描述
     */
    private String description;

    /**
     * 认知风格标签
     * 定义角色的思维倾向
     */
    @Builder.Default
    private List<String> cognitiveStyle = List.of();

    /**
     * 核心职责列表
     */
    @Builder.Default
    private List<String> coreResponsibility = List.of();

    /**
     * 允许的行为
     */
    @Builder.Default
    private List<String> allowedActions = List.of();

    /**
     * MCP Profile ID
     * 关联到角色可使用的 MCP 能力域
     */
    private String mcpProfile;

    /**
     * Prompt 模板 ID
     */
    private String promptTemplate;

    /**
     * 推荐的 LLM 模型
     */
    private String recommendedModel;

    /**
     * 角色参数配置
     * 可以包含温度、最大token等参数
     */
    @Builder.Default
    private Map<String, Object> parameters = Map.of();

    /**
     * 是否为系统角色
     * 系统角色不可删除
     */
    private boolean systemRole;

    /**
     * 角色图标/头像
     */
    private String icon;
}
