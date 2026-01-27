package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.test.controller;

import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.annotation.*;
import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.context.AkskUserContext;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试 Controller
 *
 * <p>提供各种带注解的测试接口
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@RestController
@RequestMapping("/test")
public class TestController {

    /**
     * 无注解接口 - 测试基本功能
     */
    @GetMapping("/basic")
    public Map<String, Object> basic() {
        Map<String, Object> result = new HashMap<>();
        result.put("userId", AkskUserContext.getUserId());
        result.put("username", AkskUserContext.getUsername());
        result.put("clientId", AkskUserContext.getClientId());
        result.put("roles", AkskUserContext.getRoles());
        result.put("scope", AkskUserContext.getScope());
        result.put("allContext", AkskUserContext.getAll());
        return result;
    }

    /**
     * @RequireContext 注解接口 - 测试上下文校验
     */
    @GetMapping("/require-context")
    @RequireContext
    public Map<String, Object> requireContext() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Context exists");
        result.put("userId", AkskUserContext.getUserId());
        return result;
    }

    /**
     * @RequireField 注解接口 - 测试字段存在校验
     */
    @GetMapping("/require-field")
    @RequireField("userId")
    public Map<String, Object> requireField() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Field userId exists");
        result.put("userId", AkskUserContext.getUserId());
        return result;
    }

    /**
     * @RequireFieldValue 注解接口 - 测试字段值匹配校验
     */
    @GetMapping("/require-field-value")
    @RequireFieldValue(field = "role", value = "admin")
    public Map<String, Object> requireFieldValue() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Role is admin");
        result.put("role", AkskUserContext.get("role"));
        return result;
    }

    /**
     * @RequireExpression 注解接口 - 测试 SpEL 表达式校验
     */
    @GetMapping("/require-expression")
    @RequireExpression("#context['userId'] != null && #context['userId'].length() > 0")
    public Map<String, Object> requireExpression() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Expression passed");
        result.put("userId", AkskUserContext.getUserId());
        return result;
    }

    /**
     * @RequireExpression - 测试多条件 AND 逻辑
     */
    @GetMapping("/require-expression-and")
    @RequireExpression("#context['userId'] != null && #context['username'] != null")
    public Map<String, Object> requireExpressionAnd() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Both userId and username exist");
        result.put("userId", AkskUserContext.getUserId());
        result.put("username", AkskUserContext.getUsername());
        return result;
    }

    /**
     * @RequireExpression - 测试多条件 OR 逻辑
     */
    @GetMapping("/require-expression-or")
    @RequireExpression("#context['userId'] != null || #context['clientId'] != null")
    public Map<String, Object> requireExpressionOr() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Either userId or clientId exists");
        result.put("userId", AkskUserContext.getUserId());
        result.put("clientId", AkskUserContext.getClientId());
        return result;
    }

    /**
     * @RequireExpression - 测试字符串包含判断
     */
    @GetMapping("/require-expression-contains")
    @RequireExpression("#context['username'] != null && #context['username'].contains('admin')")
    public Map<String, Object> requireExpressionContains() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Username contains 'admin'");
        result.put("username", AkskUserContext.getUsername());
        return result;
    }

    /**
     * @RequireExpression - 测试字符串前缀判断
     */
    @GetMapping("/require-expression-starts-with")
    @RequireExpression("#context['userId'] != null && #context['userId'].startsWith('user')")
    public Map<String, Object> requireExpressionStartsWith() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "UserId starts with 'user'");
        result.put("userId", AkskUserContext.getUserId());
        return result;
    }

    /**
     * @RequireExpression - 测试字符串长度判断
     */
    @GetMapping("/require-expression-length")
    @RequireExpression("#context['userId'] != null && #context['userId'].length() >= 5")
    public Map<String, Object> requireExpressionLength() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "UserId length >= 5");
        result.put("userId", AkskUserContext.getUserId());
        result.put("length", AkskUserContext.getUserId().length());
        return result;
    }

    /**
     * @RequireExpression - 测试复杂业务逻辑（管理员或特定用户）
     */
    @GetMapping("/require-expression-complex")
    @RequireExpression("#context['role'] == 'admin' || (#context['userId'] != null && #context['userId'] == 'special-user')")
    public Map<String, Object> requireExpressionComplex() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Complex expression passed");
        result.put("role", AkskUserContext.get("role"));
        result.put("userId", AkskUserContext.getUserId());
        return result;
    }

    /**
     * @RequireExpression - 测试安全上下文原文存在性检查
     */
    @GetMapping("/require-expression-security-context-exists")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].length() > 0")
    public Map<String, Object> requireExpressionSecurityContextExists() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context exists");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * @RequireExpression - 测试安全上下文原文前缀检查（JWT token）
     */
    @GetMapping("/require-expression-security-context-jwt")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].startsWith('eyJ')")
    public Map<String, Object> requireExpressionSecurityContextJwt() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context is JWT format");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * @RequireExpression - 测试安全上下文原文长度检查
     */
    @GetMapping("/require-expression-security-context-length")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].length() >= 20")
    public Map<String, Object> requireExpressionSecurityContextLength() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context length is sufficient");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        result.put("length", AkskUserContext.getSecurityContext().length());
        return result;
    }

    /**
     * @RequireExpression - 测试组合条件：userId 和 securityContext 都存在
     */
    @GetMapping("/require-expression-user-and-context")
    @RequireExpression("#context['userId'] != null && #context['securityContext'] != null")
    public Map<String, Object> requireExpressionUserAndContext() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Both userId and securityContext exist");
        result.put("userId", AkskUserContext.getUserId());
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    // ==================== 实际业务场景：权限控制 ====================

    /**
     * 管理员专属接口 - 只有 admin 角色可以访问
     */
    @GetMapping("/admin/dashboard")
    @RequireExpression("#context['role'] == 'admin'")
    public Map<String, Object> adminDashboard() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Welcome to admin dashboard");
        result.put("userId", AkskUserContext.getUserId());
        result.put("role", AkskUserContext.get("role"));
        result.put("permissions", List.of("user:read", "user:write", "user:delete", "system:config"));
        return result;
    }

    /**
     * 管理接口 - admin 或 manager 角色可以访问
     */
    @GetMapping("/management/users")
    @RequireExpression("#context['role'] == 'admin' || #context['role'] == 'manager'")
    public Map<String, Object> managementUsers() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "User management interface");
        result.put("userId", AkskUserContext.getUserId());
        result.put("role", AkskUserContext.get("role"));
        result.put("accessLevel", AkskUserContext.get("role").equals("admin") ? "full" : "limited");
        return result;
    }

    /**
     * 多租户隔离 - 只能访问自己租户的数据
     */
    @GetMapping("/tenant/data")
    @RequireExpression("#context['tenantId'] != null && #context['tenantId'].length() > 0")
    public Map<String, Object> tenantData() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Tenant data access");
        result.put("tenantId", AkskUserContext.get("tenantId"));
        result.put("userId", AkskUserContext.getUserId());
        result.put("dataScope", "tenant-" + AkskUserContext.get("tenantId"));
        return result;
    }

    /**
     * 权限检查 - 需要特定权限才能访问
     */
    @GetMapping("/resource/delete")
    @RequireExpression("#context['permissions'] != null && #context['permissions'].contains('resource:delete')")
    public Map<String, Object> deleteResource() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Resource deletion authorized");
        result.put("userId", AkskUserContext.getUserId());
        result.put("permissions", AkskUserContext.get("permissions"));
        result.put("action", "delete");
        return result;
    }

    /**
     * 复杂业务规则 - VIP 用户或付费用户可以访问高级功能
     */
    @GetMapping("/premium/feature")
    @RequireExpression("#context['userLevel'] == 'vip' || (#context['subscriptionStatus'] == 'active' && #context['subscriptionPlan'] == 'premium')")
    public Map<String, Object> premiumFeature() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Premium feature access granted");
        result.put("userId", AkskUserContext.getUserId());
        result.put("userLevel", AkskUserContext.get("userLevel"));
        result.put("subscriptionStatus", AkskUserContext.get("subscriptionStatus"));
        result.put("subscriptionPlan", AkskUserContext.get("subscriptionPlan"));
        return result;
    }

    /**
     * 地域限制 - 只允许特定地区访问
     */
    @GetMapping("/regional/content")
    @RequireExpression("#context['region'] != null && (#context['region'] == 'CN' || #context['region'] == 'US' || #context['region'] == 'EU')")
    public Map<String, Object> regionalContent() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Regional content access");
        result.put("region", AkskUserContext.get("region"));
        result.put("userId", AkskUserContext.getUserId());
        result.put("contentType", "region-specific");
        return result;
    }

    /**
     * 时间敏感 - 检查 token 是否过期（通过 expiresAt 字段）
     */
    @GetMapping("/time-sensitive/action")
    @RequireExpression("#context['expiresAt'] != null && T(Long).parseLong(#context['expiresAt']) > T(System).currentTimeMillis()")
    public Map<String, Object> timeSensitiveAction() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Time-sensitive action authorized");
        result.put("userId", AkskUserContext.getUserId());
        result.put("expiresAt", AkskUserContext.get("expiresAt"));
        result.put("currentTime", System.currentTimeMillis());
        return result;
    }

    /**
     * 组合条件 - 管理员或资源所有者可以编辑
     */
    @GetMapping("/resource/edit")
    @RequireExpression("#context['role'] == 'admin' || (#context['userId'] != null && #context['resourceOwnerId'] != null && #context['userId'] == #context['resourceOwnerId'])")
    public Map<String, Object> editResource() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Resource edit authorized");
        result.put("userId", AkskUserContext.getUserId());
        result.put("role", AkskUserContext.get("role"));
        result.put("resourceOwnerId", AkskUserContext.get("resourceOwnerId"));
        result.put("authReason", AkskUserContext.get("role") != null && AkskUserContext.get("role").equals("admin") ? "admin" : "owner");
        return result;
    }

    // ==================== 结构化数据验证场景 ====================

    /**
     * 验证用户配置 - 检查 userType 和 userStatus 的组合
     */
    @GetMapping("/validate/user-config")
    @RequireExpression("#context['userType'] == 'premium' && #context['userStatus'] == 'active'")
    public Map<String, Object> validateUserConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "User config validated");
        result.put("userType", AkskUserContext.get("userType"));
        result.put("userStatus", AkskUserContext.get("userStatus"));
        return result;
    }

    /**
     * 验证订阅信息 - 检查 subscriptionType、subscriptionLevel 和 subscriptionExpiry 的组合
     */
    @GetMapping("/validate/subscription")
    @RequireExpression("#context['subscriptionType'] == 'annual' && #context['subscriptionLevel'] == 'gold' && T(Long).parseLong(#context['subscriptionExpiry']) > T(System).currentTimeMillis()")
    public Map<String, Object> validateSubscription() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Subscription validated");
        result.put("subscriptionType", AkskUserContext.get("subscriptionType"));
        result.put("subscriptionLevel", AkskUserContext.get("subscriptionLevel"));
        result.put("subscriptionExpiry", AkskUserContext.get("subscriptionExpiry"));
        return result;
    }

    /**
     * 验证账户权限 - 检查 accountType、accountLevel 和 accountPermissions 的组合
     */
    @GetMapping("/validate/account-permissions")
    @RequireExpression("#context['accountType'] == 'business' && #context['accountLevel'] != null && T(Integer).parseInt(#context['accountLevel']) >= 3 && #context['accountPermissions'] != null && #context['accountPermissions'].contains('api:access')")
    public Map<String, Object> validateAccountPermissions() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Account permissions validated");
        result.put("accountType", AkskUserContext.get("accountType"));
        result.put("accountLevel", AkskUserContext.get("accountLevel"));
        result.put("accountPermissions", AkskUserContext.get("accountPermissions"));
        return result;
    }

    /**
     * 验证组织关系 - 检查 orgId、orgRole 和 orgStatus 的组合
     */
    @GetMapping("/validate/org-relationship")
    @RequireExpression("#context['orgId'] != null && (#context['orgRole'] == 'owner' || #context['orgRole'] == 'admin') && #context['orgStatus'] == 'verified'")
    public Map<String, Object> validateOrgRelationship() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Organization relationship validated");
        result.put("orgId", AkskUserContext.get("orgId"));
        result.put("orgRole", AkskUserContext.get("orgRole"));
        result.put("orgStatus", AkskUserContext.get("orgStatus"));
        return result;
    }

    /**
     * 验证设备信息 - 检查 deviceType、deviceTrust 和 deviceLocation 的组合
     */
    @GetMapping("/validate/device-info")
    @RequireExpression("#context['deviceType'] == 'mobile' && #context['deviceTrust'] == 'trusted' && (#context['deviceLocation'] == 'CN' || #context['deviceLocation'] == 'US')")
    public Map<String, Object> validateDeviceInfo() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Device info validated");
        result.put("deviceType", AkskUserContext.get("deviceType"));
        result.put("deviceTrust", AkskUserContext.get("deviceTrust"));
        result.put("deviceLocation", AkskUserContext.get("deviceLocation"));
        return result;
    }

    /**
     * 复杂组合验证 - 检查多个维度的数据
     */
    @GetMapping("/validate/complex-combo")
    @RequireExpression("(#context['userType'] == 'vip' || #context['memberLevel'] == 'platinum') && #context['accountStatus'] == 'active' && #context['riskLevel'] == 'low' && T(Integer).parseInt(#context['creditScore']) >= 80")
    public Map<String, Object> validateComplexCombo() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Complex combination validated");
        result.put("userType", AkskUserContext.get("userType"));
        result.put("memberLevel", AkskUserContext.get("memberLevel"));
        result.put("accountStatus", AkskUserContext.get("accountStatus"));
        result.put("riskLevel", AkskUserContext.get("riskLevel"));
        result.put("creditScore", AkskUserContext.get("creditScore"));
        return result;
    }

    /**
     * 测试数组字段提取
     */
    @GetMapping("/array-fields")
    public Map<String, Object> arrayFields() {
        Map<String, Object> result = new HashMap<>();
        List<String> roles = AkskUserContext.getRoles();
        List<String> scope = AkskUserContext.getScope();
        result.put("roles", roles);
        result.put("scope", scope);
        result.put("rolesCount", roles.size());
        result.put("scopeCount", scope.size());
        return result;
    }

    /**
     * 测试自定义字段获取
     */
    @GetMapping("/custom-fields")
    public Map<String, Object> customFields() {
        Map<String, Object> result = new HashMap<>();
        result.put("tenantId", AkskUserContext.get("tenantId"));
        result.put("orgId", AkskUserContext.get("orgId"));
        result.put("customField", AkskUserContext.get("customField"));
        return result;
    }

    /**
     * 测试 getSecurityContext()
     */
    @GetMapping("/security-context")
    public Map<String, Object> securityContext() {
        Map<String, Object> result = new HashMap<>();
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    // ==================== 解析 securityContext 字段内容的测试 ====================

    /**
     * 解析 securityContext - 检查是否包含特定键值对（简单格式：key:value）
     */
    @GetMapping("/parse-security-context/contains-key-value")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].contains('role:admin')")
    public Map<String, Object> parseSecurityContextContainsKeyValue() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context contains role:admin");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * 解析 securityContext - 检查多个键值对（AND 逻辑）
     */
    @GetMapping("/parse-security-context/multiple-key-values")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].contains('role:admin') && #context['securityContext'].contains('level:5')")
    public Map<String, Object> parseSecurityContextMultipleKeyValues() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context contains both role:admin and level:5");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * 解析 securityContext - 检查多个可能的值（OR 逻辑）
     */
    @GetMapping("/parse-security-context/or-values")
    @RequireExpression("#context['securityContext'] != null && (#context['securityContext'].contains('role:admin') || #context['securityContext'].contains('role:manager'))")
    public Map<String, Object> parseSecurityContextOrValues() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context contains admin or manager role");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * 解析 securityContext - 检查 JSON 格式的字段（使用 contains 检查键值对）
     */
    @GetMapping("/parse-security-context/json-field")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].contains('\"userType\":\"premium\"')")
    public Map<String, Object> parseSecurityContextJsonField() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context contains userType:premium in JSON format");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * 解析 securityContext - 检查 JSON 格式的多个字段
     */
    @GetMapping("/parse-security-context/json-multiple-fields")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].contains('\"userType\":\"premium\"') && #context['securityContext'].contains('\"status\":\"active\"')")
    public Map<String, Object> parseSecurityContextJsonMultipleFields() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context contains premium user with active status");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * 解析 securityContext - 复杂业务场景：VIP 用户或高级别用户
     */
    @GetMapping("/parse-security-context/vip-or-high-level")
    @RequireExpression("#context['securityContext'] != null && (#context['securityContext'].contains('userType:vip') || (#context['securityContext'].contains('level:') && #context['securityContext'].indexOf('level:') >= 0))")
    public Map<String, Object> parseSecurityContextVipOrHighLevel() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context indicates VIP or leveled user");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * 解析 securityContext - 检查权限列表（逗号分隔）
     */
    @GetMapping("/parse-security-context/permissions")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].contains('permissions:') && #context['securityContext'].contains('read,write,delete')")
    public Map<String, Object> parseSecurityContextPermissions() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context contains full permissions");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * 解析 securityContext - 检查特定权限存在
     */
    @GetMapping("/parse-security-context/specific-permission")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].contains('permissions:') && #context['securityContext'].contains('delete')")
    public Map<String, Object> parseSecurityContextSpecificPermission() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context contains delete permission");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * 解析 securityContext - 租户隔离场景
     */
    @GetMapping("/parse-security-context/tenant-isolation")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].contains('tenantId:') && #context['securityContext'].contains('tenant-123')")
    public Map<String, Object> parseSecurityContextTenantIsolation() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context contains tenant-123");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * 解析 securityContext - 多租户 + 角色组合验证
     */
    @GetMapping("/parse-security-context/tenant-and-role")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].contains('tenantId:tenant-123') && (#context['securityContext'].contains('role:admin') || #context['securityContext'].contains('role:owner'))")
    public Map<String, Object> parseSecurityContextTenantAndRole() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context contains tenant-123 with admin or owner role");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * 解析 securityContext - 订阅状态验证
     */
    @GetMapping("/parse-security-context/subscription-status")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].contains('subscription:active') && #context['securityContext'].contains('plan:premium')")
    public Map<String, Object> parseSecurityContextSubscriptionStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context contains active premium subscription");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * 解析 securityContext - 地域限制验证
     */
    @GetMapping("/parse-security-context/region-check")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].contains('region:') && (#context['securityContext'].contains('region:CN') || #context['securityContext'].contains('region:US'))")
    public Map<String, Object> parseSecurityContextRegionCheck() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context contains allowed region (CN or US)");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * 解析 securityContext - 设备信任度验证
     */
    @GetMapping("/parse-security-context/device-trust")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].contains('deviceTrust:trusted') && #context['securityContext'].contains('deviceType:mobile')")
    public Map<String, Object> parseSecurityContextDeviceTrust() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context contains trusted mobile device");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * 解析 securityContext - 复杂组合：用户类型 + 订阅 + 权限
     */
    @GetMapping("/parse-security-context/complex-combination")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].contains('userType:premium') && #context['securityContext'].contains('subscription:active') && #context['securityContext'].contains('permissions:') && #context['securityContext'].contains('api:access')")
    public Map<String, Object> parseSecurityContextComplexCombination() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context validated: premium user with active subscription and API access");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * 解析 securityContext - 检查 JWT token 格式并验证内容
     */
    @GetMapping("/parse-security-context/jwt-content")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].startsWith('eyJ') && #context['securityContext'].length() > 50")
    public Map<String, Object> parseSecurityContextJwtContent() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context is a valid JWT token format");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }

    /**
     * 解析 securityContext - 检查 Base64 编码的数据
     */
    @GetMapping("/parse-security-context/base64-encoded")
    @RequireExpression("#context['securityContext'] != null && #context['securityContext'].matches('[A-Za-z0-9+/=]+')")
    public Map<String, Object> parseSecurityContextBase64Encoded() {
        Map<String, Object> result = new HashMap<>();
        result.put("message", "Security context is Base64 encoded");
        result.put("securityContext", AkskUserContext.getSecurityContext());
        return result;
    }
}
