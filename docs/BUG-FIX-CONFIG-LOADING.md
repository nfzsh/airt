# 配置加载问题修复总结

## 问题描述

前端页面显示所有角色的 `displayName` 为 `undefined`：

```
🎯
undefined 负责控场、归纳总结、推进议程

👔
undefined 关注用户价值、需求优先级、防止范围膨胀
```

## 根本原因

**`@ConfigurationProperties` 没有被 Spring 扫描到！**

虽然 `AirtProperties` 类标注了 `@ConfigurationProperties(prefix = "airt")`，但主应用类没有启用 `@EnableConfigurationProperties`，导致 Spring Boot 没有扫描并绑定 YAML 配置。

结果：`airtProperties.getRoles()` 返回的是空列表，所有字段都是 `null`。

## 修复方案

### 1. 启用 @ConfigurationProperties 扫描

**文件**: `AirtApplication.java`

```java
@SpringBootApplication
@EnableConfigurationProperties({AirtProperties.class, LlmProperties.class})
public class AirtApplication {
    public static void main(String[] args) {
        SpringApplication.run(AirtApplication.class, args);
    }
}
```

### 2. 添加 @JsonProperty 注解（可选）

**文件**: `ConfigController.java`

为了确保 JSON 序列化正确，在 DTO 字段上添加 `@JsonProperty` 注解：

```java
public static class RoleConfigDTO {
    @JsonProperty("roleId")
    private String roleId;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("description")
    private String description;

    // ...
}
```

### 3. 添加调试日志

**文件**: `ConfigController.java`

```java
@GetMapping("/roles")
public ResponseEntity<List<RoleConfigDTO>> getRoles() {
    System.out.println("=== DEBUG: Loading roles ===");
    System.out.println("Total roles: " + airtProperties.getRoles().size());

    List<RoleConfigDTO> roles = airtProperties.getRoles().stream()
            .map(role -> {
                System.out.println("Role: " + role.getRoleId() + ", displayName: " + role.getDisplayName());
                return toRoleConfigDTO(role);
            })
            .collect(Collectors.toList());

    return ResponseEntity.ok(roles);
}
```

## 配置绑定说明

### YAML 配置（application.yml）

```yaml
airt:
  roles:
    - role-id: moderator          # 连字符命名
      display-name: 主持人
      description: 负责控场
      icon: "🎯"
```

### Java 配置类（AirtProperties.java）

```java
@ConfigurationProperties(prefix = "airt")
public class AirtProperties {
    private List<RoleConfig> roles = new ArrayList<>();

    public static class RoleConfig {
        private String roleId;        // 驼峰命名
        private String displayName;
        private String description;
        private String icon;
        // getters & setters
    }
}
```

### Spring Boot 的松散绑定

Spring Boot 自动处理以下命名转换：
- `display-name` (YAML) → `displayName` (Java)
- `role_id` (YAML) → `roleId` (Java)
- `core-responsibility` (YAML) → `coreResponsibility` (Java)

## 验证步骤

1. **重启应用**
2. **查看日志输出**，应该看到：
   ```
   === DEBUG: Loading roles ===
   Total roles: 7
   Role: moderator, displayName: 主持人
   Role: product_manager, displayName: 产品经理
   ...
   ```
3. **刷新前端页面**，角色名称应该正确显示

## 完整修复文件

1. ✅ `AirtApplication.java` - 添加 `@EnableConfigurationProperties`
2. ✅ `ConfigController.java` - 添加 `@JsonProperty` 和调试日志
3. ✅ `AgentFactory.java` - 使用 `AirtProperties` 而不是 `ConfigurationLoader`
4. ✅ `AgentRuntime.java` - 使用 `LlmProperties` 而不是环境变量

## 相关问题修复

同时修复了以下相关问题：
- ✅ `Role definition not found: MODERATOR` - 改用 `AirtProperties`
- ✅ API Key 从环境变量读取改为从配置文件读取
- ✅ 前端 API 路径错误（404）

## 测试

重启应用后，访问：
- 前端：http://localhost:8080/airt/
- API：http://localhost:8080/airt/api/config/roles

应该返回正确的角色列表，所有字段都有值。
