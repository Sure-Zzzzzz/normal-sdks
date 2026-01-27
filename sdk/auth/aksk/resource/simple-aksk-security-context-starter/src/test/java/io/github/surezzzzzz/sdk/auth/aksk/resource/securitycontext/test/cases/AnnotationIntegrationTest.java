package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.test.SimpleAkskSecurityContextTestApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 权限注解集成测试
 *
 * <p>测试 @RequireContext、@RequireField、@RequireFieldValue、@RequireExpression 注解的权限校验功能
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@SpringBootTest(classes = SimpleAkskSecurityContextTestApplication.class)
@AutoConfigureMockMvc
public class AnnotationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * 测试 @RequireContext - 有上下文时通过
     */
    @Test
    public void testRequireContextWithContextSuccess() throws Exception {
        mockMvc.perform(get("/test/require-context")
                        .header("x-sure-auth-aksk-user-id", "user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Context exists"))
                .andExpect(jsonPath("$.userId").value("user123"));
    }

    /**
     * 测试 @RequireContext - 无上下文时失败
     */
    @Test
    public void testRequireContextWithoutContextFail() throws Exception {
        mockMvc.perform(get("/test/require-context"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试 @RequireField - 字段存在时通过
     */
    @Test
    public void testRequireFieldWithFieldSuccess() throws Exception {
        mockMvc.perform(get("/test/require-field")
                        .header("x-sure-auth-aksk-user-id", "user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Field userId exists"))
                .andExpect(jsonPath("$.userId").value("user123"));
    }

    /**
     * 测试 @RequireField - 字段不存在时失败
     */
    @Test
    public void testRequireFieldWithoutFieldFail() throws Exception {
        mockMvc.perform(get("/test/require-field")
                        .header("x-sure-auth-aksk-username", "testuser"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试 @RequireFieldValue - 字段值匹配时通过
     */
    @Test
    public void testRequireFieldValueMatchingValueSuccess() throws Exception {
        mockMvc.perform(get("/test/require-field-value")
                        .header("x-sure-auth-aksk-role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Role is admin"))
                .andExpect(jsonPath("$.role").value("admin"));
    }

    /**
     * 测试 @RequireFieldValue - 字段值不匹配时失败
     */
    @Test
    public void testRequireFieldValueMismatchingValueFail() throws Exception {
        mockMvc.perform(get("/test/require-field-value")
                        .header("x-sure-auth-aksk-role", "user"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试 @RequireExpression - 表达式为 true 时通过
     */
    @Test
    public void testRequireExpressionTrueExpressionSuccess() throws Exception {
        mockMvc.perform(get("/test/require-expression")
                        .header("x-sure-auth-aksk-user-id", "user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Expression passed"))
                .andExpect(jsonPath("$.userId").value("user123"));
    }

    /**
     * 测试 @RequireExpression - 表达式为 false 时失败（userId 为空）
     */
    @Test
    public void testRequireExpressionFalseExpressionFail() throws Exception {
        mockMvc.perform(get("/test/require-expression")
                        .header("x-sure-auth-aksk-username", "testuser"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试 @RequireExpression AND 逻辑 - 两个条件都满足时通过
     */
    @Test
    public void testRequireExpressionAndBothExistSuccess() throws Exception {
        mockMvc.perform(get("/test/require-expression-and")
                        .header("x-sure-auth-aksk-user-id", "user123")
                        .header("x-sure-auth-aksk-username", "testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Both userId and username exist"))
                .andExpect(jsonPath("$.userId").value("user123"))
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    /**
     * 测试 @RequireExpression AND 逻辑 - 只有一个条件满足时失败
     */
    @Test
    public void testRequireExpressionAndOnlyOneExistFail() throws Exception {
        mockMvc.perform(get("/test/require-expression-and")
                        .header("x-sure-auth-aksk-user-id", "user123"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试 @RequireExpression OR 逻辑 - userId 存在时通过
     */
    @Test
    public void testRequireExpressionOrUserIdExistsSuccess() throws Exception {
        mockMvc.perform(get("/test/require-expression-or")
                        .header("x-sure-auth-aksk-user-id", "user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Either userId or clientId exists"))
                .andExpect(jsonPath("$.userId").value("user123"));
    }

    /**
     * 测试 @RequireExpression OR 逻辑 - clientId 存在时通过
     */
    @Test
    public void testRequireExpressionOrClientIdExistsSuccess() throws Exception {
        mockMvc.perform(get("/test/require-expression-or")
                        .header("x-sure-auth-aksk-client-id", "client456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Either userId or clientId exists"))
                .andExpect(jsonPath("$.clientId").value("client456"));
    }

    /**
     * 测试 @RequireExpression OR 逻辑 - 两个都不存在时失败
     */
    @Test
    public void testRequireExpressionOrNeitherExistsFail() throws Exception {
        mockMvc.perform(get("/test/require-expression-or")
                        .header("x-sure-auth-aksk-username", "testuser"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试 @RequireExpression contains - username 包含 'admin' 时通过
     */
    @Test
    public void testRequireExpressionContainsMatchSuccess() throws Exception {
        mockMvc.perform(get("/test/require-expression-contains")
                        .header("x-sure-auth-aksk-username", "admin-user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Username contains 'admin'"))
                .andExpect(jsonPath("$.username").value("admin-user"));
    }

    /**
     * 测试 @RequireExpression contains - username 不包含 'admin' 时失败
     */
    @Test
    public void testRequireExpressionContainsNoMatchFail() throws Exception {
        mockMvc.perform(get("/test/require-expression-contains")
                        .header("x-sure-auth-aksk-username", "regular-user"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试 @RequireExpression startsWith - userId 以 'user' 开头时通过
     */
    @Test
    public void testRequireExpressionStartsWithMatchSuccess() throws Exception {
        mockMvc.perform(get("/test/require-expression-starts-with")
                        .header("x-sure-auth-aksk-user-id", "user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("UserId starts with 'user'"))
                .andExpect(jsonPath("$.userId").value("user123"));
    }

    /**
     * 测试 @RequireExpression startsWith - userId 不以 'user' 开头时失败
     */
    @Test
    public void testRequireExpressionStartsWithNoMatchFail() throws Exception {
        mockMvc.perform(get("/test/require-expression-starts-with")
                        .header("x-sure-auth-aksk-user-id", "admin123"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试 @RequireExpression length - userId 长度 >= 5 时通过
     */
    @Test
    public void testRequireExpressionLengthSufficientSuccess() throws Exception {
        mockMvc.perform(get("/test/require-expression-length")
                        .header("x-sure-auth-aksk-user-id", "user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("UserId length >= 5"))
                .andExpect(jsonPath("$.userId").value("user123"))
                .andExpect(jsonPath("$.length").value(7));
    }

    /**
     * 测试 @RequireExpression length - userId 长度 < 5 时失败
     */
    @Test
    public void testRequireExpressionLengthInsufficientFail() throws Exception {
        mockMvc.perform(get("/test/require-expression-length")
                        .header("x-sure-auth-aksk-user-id", "usr1"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试 @RequireExpression 复杂逻辑 - role 为 admin 时通过
     */
    @Test
    public void testRequireExpressionComplexAdminRoleSuccess() throws Exception {
        mockMvc.perform(get("/test/require-expression-complex")
                        .header("x-sure-auth-aksk-role", "admin")
                        .header("x-sure-auth-aksk-user-id", "user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Complex expression passed"))
                .andExpect(jsonPath("$.role").value("admin"))
                .andExpect(jsonPath("$.userId").value("user123"));
    }

    /**
     * 测试 @RequireExpression 复杂逻辑 - userId 为 special-user 时通过
     */
    @Test
    public void testRequireExpressionComplexSpecialUserSuccess() throws Exception {
        mockMvc.perform(get("/test/require-expression-complex")
                        .header("x-sure-auth-aksk-user-id", "special-user")
                        .header("x-sure-auth-aksk-role", "user"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Complex expression passed"))
                .andExpect(jsonPath("$.role").value("user"))
                .andExpect(jsonPath("$.userId").value("special-user"));
    }

    /**
     * 测试 @RequireExpression 复杂逻辑 - 两个条件都不满足时失败
     */
    @Test
    public void testRequireExpressionComplexNeitherConditionFail() throws Exception {
        mockMvc.perform(get("/test/require-expression-complex")
                        .header("x-sure-auth-aksk-user-id", "regular-user")
                        .header("x-sure-auth-aksk-role", "user"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试 @RequireExpression 安全上下文存在性 - securityContext 存在时通过
     */
    @Test
    public void testRequireExpressionSecurityContextExistsSuccess() throws Exception {
        mockMvc.perform(get("/test/require-expression-security-context-exists")
                        .header("x-sure-auth-aksk-security-context", "eyJhbGciOiJIUzI1NiJ9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context exists"))
                .andExpect(jsonPath("$.securityContext").value("eyJhbGciOiJIUzI1NiJ9"));
    }

    /**
     * 测试 @RequireExpression 安全上下文存在性 - securityContext 不存在时失败
     */
    @Test
    public void testRequireExpressionSecurityContextExistsFail() throws Exception {
        mockMvc.perform(get("/test/require-expression-security-context-exists")
                        .header("x-sure-auth-aksk-user-id", "user123"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试 @RequireExpression 安全上下文存在性 - securityContext 为空字符串时失败
     */
    @Test
    public void testRequireExpressionSecurityContextExistsEmptyFail() throws Exception {
        mockMvc.perform(get("/test/require-expression-security-context-exists")
                        .header("x-sure-auth-aksk-security-context", ""))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试 @RequireExpression JWT 格式检查 - securityContext 是 JWT 格式时通过
     */
    @Test
    public void testRequireExpressionSecurityContextJwtSuccess() throws Exception {
        mockMvc.perform(get("/test/require-expression-security-context-jwt")
                        .header("x-sure-auth-aksk-security-context", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJ1c2VyMTIzIn0.signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context is JWT format"))
                .andExpect(jsonPath("$.securityContext").value("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJ1c2VyMTIzIn0.signature"));
    }

    /**
     * 测试 @RequireExpression JWT 格式检查 - securityContext 不是 JWT 格式时失败
     */
    @Test
    public void testRequireExpressionSecurityContextJwtFail() throws Exception {
        mockMvc.perform(get("/test/require-expression-security-context-jwt")
                        .header("x-sure-auth-aksk-security-context", "plain-text-token"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试 @RequireExpression 长度检查 - securityContext 长度 >= 20 时通过
     */
    @Test
    public void testRequireExpressionSecurityContextLengthSuccess() throws Exception {
        mockMvc.perform(get("/test/require-expression-security-context-length")
                        .header("x-sure-auth-aksk-security-context", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context length is sufficient"))
                .andExpect(jsonPath("$.securityContext").value("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9"))
                .andExpect(jsonPath("$.length").value(36));
    }

    /**
     * 测试 @RequireExpression 长度检查 - securityContext 长度 < 20 时失败
     */
    @Test
    public void testRequireExpressionSecurityContextLengthFail() throws Exception {
        mockMvc.perform(get("/test/require-expression-security-context-length")
                        .header("x-sure-auth-aksk-security-context", "short-token"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试 @RequireExpression 组合条件 - userId 和 securityContext 都存在时通过
     */
    @Test
    public void testRequireExpressionUserAndContextBothExistSuccess() throws Exception {
        mockMvc.perform(get("/test/require-expression-user-and-context")
                        .header("x-sure-auth-aksk-user-id", "user123")
                        .header("x-sure-auth-aksk-security-context", "eyJhbGciOiJIUzI1NiJ9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Both userId and securityContext exist"))
                .andExpect(jsonPath("$.userId").value("user123"))
                .andExpect(jsonPath("$.securityContext").value("eyJhbGciOiJIUzI1NiJ9"));
    }

    /**
     * 测试 @RequireExpression 组合条件 - 只有 userId 存在时失败
     */
    @Test
    public void testRequireExpressionUserAndContextOnlyUserIdFail() throws Exception {
        mockMvc.perform(get("/test/require-expression-user-and-context")
                        .header("x-sure-auth-aksk-user-id", "user123"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试 @RequireExpression 组合条件 - 只有 securityContext 存在时失败
     */
    @Test
    public void testRequireExpressionUserAndContextOnlyContextFail() throws Exception {
        mockMvc.perform(get("/test/require-expression-user-and-context")
                        .header("x-sure-auth-aksk-security-context", "eyJhbGciOiJIUzI1NiJ9"))
                .andExpect(status().isForbidden());
    }

    // ==================== 实际业务场景测试 ====================

    /**
     * 测试管理员专属接口 - admin 角色可以访问
     */
    @Test
    public void testAdminDashboardWithAdminRoleSuccess() throws Exception {
        mockMvc.perform(get("/test/admin/dashboard")
                        .header("x-sure-auth-aksk-user-id", "admin001")
                        .header("x-sure-auth-aksk-role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Welcome to admin dashboard"))
                .andExpect(jsonPath("$.userId").value("admin001"))
                .andExpect(jsonPath("$.role").value("admin"))
                .andExpect(jsonPath("$.permissions").isArray());
    }

    /**
     * 测试管理员专属接口 - 非 admin 角色无法访问
     */
    @Test
    public void testAdminDashboardWithNonAdminRoleFail() throws Exception {
        mockMvc.perform(get("/test/admin/dashboard")
                        .header("x-sure-auth-aksk-user-id", "user001")
                        .header("x-sure-auth-aksk-role", "user"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试管理接口 - admin 角色可以访问
     */
    @Test
    public void testManagementUsersWithAdminRoleSuccess() throws Exception {
        mockMvc.perform(get("/test/management/users")
                        .header("x-sure-auth-aksk-user-id", "admin001")
                        .header("x-sure-auth-aksk-role", "admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User management interface"))
                .andExpect(jsonPath("$.role").value("admin"))
                .andExpect(jsonPath("$.accessLevel").value("full"));
    }

    /**
     * 测试管理接口 - manager 角色可以访问
     */
    @Test
    public void testManagementUsersWithManagerRoleSuccess() throws Exception {
        mockMvc.perform(get("/test/management/users")
                        .header("x-sure-auth-aksk-user-id", "manager001")
                        .header("x-sure-auth-aksk-role", "manager"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User management interface"))
                .andExpect(jsonPath("$.role").value("manager"))
                .andExpect(jsonPath("$.accessLevel").value("limited"));
    }

    /**
     * 测试管理接口 - 普通用户无法访问
     */
    @Test
    public void testManagementUsersWithRegularUserFail() throws Exception {
        mockMvc.perform(get("/test/management/users")
                        .header("x-sure-auth-aksk-user-id", "user001")
                        .header("x-sure-auth-aksk-role", "user"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试多租户隔离 - 有 tenantId 时可以访问
     */
    @Test
    public void testTenantDataWithTenantIdSuccess() throws Exception {
        mockMvc.perform(get("/test/tenant/data")
                        .header("x-sure-auth-aksk-user-id", "user001")
                        .header("x-sure-auth-aksk-tenant-id", "tenant-123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Tenant data access"))
                .andExpect(jsonPath("$.tenantId").value("tenant-123"))
                .andExpect(jsonPath("$.dataScope").value("tenant-tenant-123"));
    }

    /**
     * 测试多租户隔离 - 没有 tenantId 时无法访问
     */
    @Test
    public void testTenantDataWithoutTenantIdFail() throws Exception {
        mockMvc.perform(get("/test/tenant/data")
                        .header("x-sure-auth-aksk-user-id", "user001"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试权限检查 - 有 resource:delete 权限时可以访问
     */
    @Test
    public void testDeleteResourceWithPermissionSuccess() throws Exception {
        mockMvc.perform(get("/test/resource/delete")
                        .header("x-sure-auth-aksk-user-id", "user001")
                        .header("x-sure-auth-aksk-permissions", "resource:read,resource:write,resource:delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Resource deletion authorized"))
                .andExpect(jsonPath("$.action").value("delete"));
    }

    /**
     * 测试权限检查 - 没有 resource:delete 权限时无法访问
     */
    @Test
    public void testDeleteResourceWithoutPermissionFail() throws Exception {
        mockMvc.perform(get("/test/resource/delete")
                        .header("x-sure-auth-aksk-user-id", "user001")
                        .header("x-sure-auth-aksk-permissions", "resource:read,resource:write"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试高级功能 - VIP 用户可以访问
     */
    @Test
    public void testPremiumFeatureWithVipUserSuccess() throws Exception {
        mockMvc.perform(get("/test/premium/feature")
                        .header("x-sure-auth-aksk-user-id", "vip001")
                        .header("x-sure-auth-aksk-user-level", "vip"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Premium feature access granted"))
                .andExpect(jsonPath("$.userLevel").value("vip"));
    }

    /**
     * 测试高级功能 - 付费用户可以访问
     */
    @Test
    public void testPremiumFeatureWithPaidUserSuccess() throws Exception {
        mockMvc.perform(get("/test/premium/feature")
                        .header("x-sure-auth-aksk-user-id", "user001")
                        .header("x-sure-auth-aksk-user-level", "regular")
                        .header("x-sure-auth-aksk-subscription-status", "active")
                        .header("x-sure-auth-aksk-subscription-plan", "premium"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Premium feature access granted"))
                .andExpect(jsonPath("$.subscriptionStatus").value("active"))
                .andExpect(jsonPath("$.subscriptionPlan").value("premium"));
    }

    /**
     * 测试高级功能 - 普通用户无法访问
     */
    @Test
    public void testPremiumFeatureWithRegularUserFail() throws Exception {
        mockMvc.perform(get("/test/premium/feature")
                        .header("x-sure-auth-aksk-user-id", "user001")
                        .header("x-sure-auth-aksk-user-level", "regular")
                        .header("x-sure-auth-aksk-subscription-status", "inactive"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试地域限制 - CN 地区可以访问
     */
    @Test
    public void testRegionalContentWithCNRegionSuccess() throws Exception {
        mockMvc.perform(get("/test/regional/content")
                        .header("x-sure-auth-aksk-user-id", "user001")
                        .header("x-sure-auth-aksk-region", "CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Regional content access"))
                .andExpect(jsonPath("$.region").value("CN"));
    }

    /**
     * 测试地域限制 - 不支持的地区无法访问
     */
    @Test
    public void testRegionalContentWithUnsupportedRegionFail() throws Exception {
        mockMvc.perform(get("/test/regional/content")
                        .header("x-sure-auth-aksk-user-id", "user001")
                        .header("x-sure-auth-aksk-region", "JP"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试时间敏感操作 - token 未过期时可以访问
     */
    @Test
    public void testTimeSensitiveActionWithValidTokenSuccess() throws Exception {
        long futureTime = System.currentTimeMillis() + 3600000; // 1 hour later
        mockMvc.perform(get("/test/time-sensitive/action")
                        .header("x-sure-auth-aksk-user-id", "user001")
                        .header("x-sure-auth-aksk-expires-at", String.valueOf(futureTime)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Time-sensitive action authorized"));
    }

    /**
     * 测试时间敏感操作 - token 已过期时无法访问
     */
    @Test
    public void testTimeSensitiveActionWithExpiredTokenFail() throws Exception {
        long pastTime = System.currentTimeMillis() - 3600000; // 1 hour ago
        mockMvc.perform(get("/test/time-sensitive/action")
                        .header("x-sure-auth-aksk-user-id", "user001")
                        .header("x-sure-auth-aksk-expires-at", String.valueOf(pastTime)))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试资源编辑 - 管理员可以编辑任何资源
     */
    @Test
    public void testEditResourceAsAdminSuccess() throws Exception {
        mockMvc.perform(get("/test/resource/edit")
                        .header("x-sure-auth-aksk-user-id", "admin001")
                        .header("x-sure-auth-aksk-role", "admin")
                        .header("x-sure-auth-aksk-resource-owner-id", "user001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Resource edit authorized"))
                .andExpect(jsonPath("$.authReason").value("admin"));
    }

    /**
     * 测试资源编辑 - 资源所有者可以编辑自己的资源
     */
    @Test
    public void testEditResourceAsOwnerSuccess() throws Exception {
        mockMvc.perform(get("/test/resource/edit")
                        .header("x-sure-auth-aksk-user-id", "user001")
                        .header("x-sure-auth-aksk-role", "user")
                        .header("x-sure-auth-aksk-resource-owner-id", "user001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Resource edit authorized"))
                .andExpect(jsonPath("$.authReason").value("owner"));
    }

    /**
     * 测试资源编辑 - 非所有者无法编辑他人资源
     */
    @Test
    public void testEditResourceAsNonOwnerFail() throws Exception {
        mockMvc.perform(get("/test/resource/edit")
                        .header("x-sure-auth-aksk-user-id", "user002")
                        .header("x-sure-auth-aksk-role", "user")
                        .header("x-sure-auth-aksk-resource-owner-id", "user001"))
                .andExpect(status().isForbidden());
    }

    // ==================== 结构化数据验证测试 ====================

    /**
     * 测试用户配置验证 - userType 和 userStatus 都匹配时通过
     */
    @Test
    public void testValidateUserConfigSuccess() throws Exception {
        mockMvc.perform(get("/test/validate/user-config")
                        .header("x-sure-auth-aksk-user-type", "premium")
                        .header("x-sure-auth-aksk-user-status", "active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User config validated"))
                .andExpect(jsonPath("$.userType").value("premium"))
                .andExpect(jsonPath("$.userStatus").value("active"));
    }

    /**
     * 测试用户配置验证 - userType 不匹配时失败
     */
    @Test
    public void testValidateUserConfigWrongTypeFail() throws Exception {
        mockMvc.perform(get("/test/validate/user-config")
                        .header("x-sure-auth-aksk-user-type", "basic")
                        .header("x-sure-auth-aksk-user-status", "active"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试用户配置验证 - userStatus 不匹配时失败
     */
    @Test
    public void testValidateUserConfigWrongStatusFail() throws Exception {
        mockMvc.perform(get("/test/validate/user-config")
                        .header("x-sure-auth-aksk-user-type", "premium")
                        .header("x-sure-auth-aksk-user-status", "inactive"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试订阅信息验证 - 所有条件都满足时通过
     */
    @Test
    public void testValidateSubscriptionSuccess() throws Exception {
        long futureTime = System.currentTimeMillis() + 86400000; // 1 day later
        mockMvc.perform(get("/test/validate/subscription")
                        .header("x-sure-auth-aksk-subscription-type", "annual")
                        .header("x-sure-auth-aksk-subscription-level", "gold")
                        .header("x-sure-auth-aksk-subscription-expiry", String.valueOf(futureTime)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Subscription validated"))
                .andExpect(jsonPath("$.subscriptionType").value("annual"))
                .andExpect(jsonPath("$.subscriptionLevel").value("gold"));
    }

    /**
     * 测试订阅信息验证 - subscriptionType 不匹配时失败
     */
    @Test
    public void testValidateSubscriptionWrongTypeFail() throws Exception {
        long futureTime = System.currentTimeMillis() + 86400000;
        mockMvc.perform(get("/test/validate/subscription")
                        .header("x-sure-auth-aksk-subscription-type", "monthly")
                        .header("x-sure-auth-aksk-subscription-level", "gold")
                        .header("x-sure-auth-aksk-subscription-expiry", String.valueOf(futureTime)))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试订阅信息验证 - subscriptionLevel 不匹配时失败
     */
    @Test
    public void testValidateSubscriptionWrongLevelFail() throws Exception {
        long futureTime = System.currentTimeMillis() + 86400000;
        mockMvc.perform(get("/test/validate/subscription")
                        .header("x-sure-auth-aksk-subscription-type", "annual")
                        .header("x-sure-auth-aksk-subscription-level", "silver")
                        .header("x-sure-auth-aksk-subscription-expiry", String.valueOf(futureTime)))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试订阅信息验证 - 订阅已过期时失败
     */
    @Test
    public void testValidateSubscriptionExpiredFail() throws Exception {
        long pastTime = System.currentTimeMillis() - 86400000; // 1 day ago
        mockMvc.perform(get("/test/validate/subscription")
                        .header("x-sure-auth-aksk-subscription-type", "annual")
                        .header("x-sure-auth-aksk-subscription-level", "gold")
                        .header("x-sure-auth-aksk-subscription-expiry", String.valueOf(pastTime)))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试账户权限验证 - 所有条件都满足时通过
     */
    @Test
    public void testValidateAccountPermissionsSuccess() throws Exception {
        mockMvc.perform(get("/test/validate/account-permissions")
                        .header("x-sure-auth-aksk-account-type", "business")
                        .header("x-sure-auth-aksk-account-level", "5")
                        .header("x-sure-auth-aksk-account-permissions", "api:access,data:read,data:write"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account permissions validated"))
                .andExpect(jsonPath("$.accountType").value("business"))
                .andExpect(jsonPath("$.accountLevel").value("5"));
    }

    /**
     * 测试账户权限验证 - accountType 不匹配时失败
     */
    @Test
    public void testValidateAccountPermissionsWrongTypeFail() throws Exception {
        mockMvc.perform(get("/test/validate/account-permissions")
                        .header("x-sure-auth-aksk-account-type", "personal")
                        .header("x-sure-auth-aksk-account-level", "5")
                        .header("x-sure-auth-aksk-account-permissions", "api:access,data:read"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试账户权限验证 - accountLevel 不足时失败
     */
    @Test
    public void testValidateAccountPermissionsLowLevelFail() throws Exception {
        mockMvc.perform(get("/test/validate/account-permissions")
                        .header("x-sure-auth-aksk-account-type", "business")
                        .header("x-sure-auth-aksk-account-level", "2")
                        .header("x-sure-auth-aksk-account-permissions", "api:access,data:read"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试账户权限验证 - 缺少必需权限时失败
     */
    @Test
    public void testValidateAccountPermissionsMissingPermissionFail() throws Exception {
        mockMvc.perform(get("/test/validate/account-permissions")
                        .header("x-sure-auth-aksk-account-type", "business")
                        .header("x-sure-auth-aksk-account-level", "5")
                        .header("x-sure-auth-aksk-account-permissions", "data:read,data:write"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试组织关系验证 - orgRole 为 owner 时通过
     */
    @Test
    public void testValidateOrgRelationshipAsOwnerSuccess() throws Exception {
        mockMvc.perform(get("/test/validate/org-relationship")
                        .header("x-sure-auth-aksk-org-id", "org-12345")
                        .header("x-sure-auth-aksk-org-role", "owner")
                        .header("x-sure-auth-aksk-org-status", "verified"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Organization relationship validated"))
                .andExpect(jsonPath("$.orgId").value("org-12345"))
                .andExpect(jsonPath("$.orgRole").value("owner"))
                .andExpect(jsonPath("$.orgStatus").value("verified"));
    }

    /**
     * 测试组织关系验证 - orgRole 为 admin 时通过
     */
    @Test
    public void testValidateOrgRelationshipAsAdminSuccess() throws Exception {
        mockMvc.perform(get("/test/validate/org-relationship")
                        .header("x-sure-auth-aksk-org-id", "org-12345")
                        .header("x-sure-auth-aksk-org-role", "admin")
                        .header("x-sure-auth-aksk-org-status", "verified"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Organization relationship validated"))
                .andExpect(jsonPath("$.orgRole").value("admin"));
    }

    /**
     * 测试组织关系验证 - orgRole 为 member 时失败
     */
    @Test
    public void testValidateOrgRelationshipAsMemberFail() throws Exception {
        mockMvc.perform(get("/test/validate/org-relationship")
                        .header("x-sure-auth-aksk-org-id", "org-12345")
                        .header("x-sure-auth-aksk-org-role", "member")
                        .header("x-sure-auth-aksk-org-status", "verified"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试组织关系验证 - orgStatus 未验证时失败
     */
    @Test
    public void testValidateOrgRelationshipUnverifiedFail() throws Exception {
        mockMvc.perform(get("/test/validate/org-relationship")
                        .header("x-sure-auth-aksk-org-id", "org-12345")
                        .header("x-sure-auth-aksk-org-role", "owner")
                        .header("x-sure-auth-aksk-org-status", "pending"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试设备信息验证 - 所有条件都满足时通过（CN 地区）
     */
    @Test
    public void testValidateDeviceInfoCNSuccess() throws Exception {
        mockMvc.perform(get("/test/validate/device-info")
                        .header("x-sure-auth-aksk-device-type", "mobile")
                        .header("x-sure-auth-aksk-device-trust", "trusted")
                        .header("x-sure-auth-aksk-device-location", "CN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Device info validated"))
                .andExpect(jsonPath("$.deviceType").value("mobile"))
                .andExpect(jsonPath("$.deviceTrust").value("trusted"))
                .andExpect(jsonPath("$.deviceLocation").value("CN"));
    }

    /**
     * 测试设备信息验证 - 所有条件都满足时通过（US 地区）
     */
    @Test
    public void testValidateDeviceInfoUSSuccess() throws Exception {
        mockMvc.perform(get("/test/validate/device-info")
                        .header("x-sure-auth-aksk-device-type", "mobile")
                        .header("x-sure-auth-aksk-device-trust", "trusted")
                        .header("x-sure-auth-aksk-device-location", "US"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceLocation").value("US"));
    }

    /**
     * 测试设备信息验证 - deviceType 不匹配时失败
     */
    @Test
    public void testValidateDeviceInfoWrongTypeFail() throws Exception {
        mockMvc.perform(get("/test/validate/device-info")
                        .header("x-sure-auth-aksk-device-type", "desktop")
                        .header("x-sure-auth-aksk-device-trust", "trusted")
                        .header("x-sure-auth-aksk-device-location", "CN"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试设备信息验证 - deviceTrust 不可信时失败
     */
    @Test
    public void testValidateDeviceInfoUntrustedFail() throws Exception {
        mockMvc.perform(get("/test/validate/device-info")
                        .header("x-sure-auth-aksk-device-type", "mobile")
                        .header("x-sure-auth-aksk-device-trust", "untrusted")
                        .header("x-sure-auth-aksk-device-location", "CN"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试设备信息验证 - deviceLocation 不支持时失败
     */
    @Test
    public void testValidateDeviceInfoUnsupportedLocationFail() throws Exception {
        mockMvc.perform(get("/test/validate/device-info")
                        .header("x-sure-auth-aksk-device-type", "mobile")
                        .header("x-sure-auth-aksk-device-trust", "trusted")
                        .header("x-sure-auth-aksk-device-location", "JP"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试复杂组合验证 - userType 为 vip 时通过
     */
    @Test
    public void testValidateComplexComboVipSuccess() throws Exception {
        mockMvc.perform(get("/test/validate/complex-combo")
                        .header("x-sure-auth-aksk-user-type", "vip")
                        .header("x-sure-auth-aksk-account-status", "active")
                        .header("x-sure-auth-aksk-risk-level", "low")
                        .header("x-sure-auth-aksk-credit-score", "85"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Complex combination validated"))
                .andExpect(jsonPath("$.userType").value("vip"))
                .andExpect(jsonPath("$.accountStatus").value("active"))
                .andExpect(jsonPath("$.riskLevel").value("low"))
                .andExpect(jsonPath("$.creditScore").value("85"));
    }

    /**
     * 测试复杂组合验证 - memberLevel 为 platinum 时通过
     */
    @Test
    public void testValidateComplexComboPlatinumSuccess() throws Exception {
        mockMvc.perform(get("/test/validate/complex-combo")
                        .header("x-sure-auth-aksk-user-type", "regular")
                        .header("x-sure-auth-aksk-member-level", "platinum")
                        .header("x-sure-auth-aksk-account-status", "active")
                        .header("x-sure-auth-aksk-risk-level", "low")
                        .header("x-sure-auth-aksk-credit-score", "90"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberLevel").value("platinum"));
    }

    /**
     * 测试复杂组合验证 - accountStatus 不是 active 时失败
     */
    @Test
    public void testValidateComplexComboInactiveAccountFail() throws Exception {
        mockMvc.perform(get("/test/validate/complex-combo")
                        .header("x-sure-auth-aksk-user-type", "vip")
                        .header("x-sure-auth-aksk-account-status", "suspended")
                        .header("x-sure-auth-aksk-risk-level", "low")
                        .header("x-sure-auth-aksk-credit-score", "85"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试复杂组合验证 - riskLevel 不是 low 时失败
     */
    @Test
    public void testValidateComplexComboHighRiskFail() throws Exception {
        mockMvc.perform(get("/test/validate/complex-combo")
                        .header("x-sure-auth-aksk-user-type", "vip")
                        .header("x-sure-auth-aksk-account-status", "active")
                        .header("x-sure-auth-aksk-risk-level", "high")
                        .header("x-sure-auth-aksk-credit-score", "85"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试复杂组合验证 - creditScore 不足时失败
     */
    @Test
    public void testValidateComplexComboLowCreditScoreFail() throws Exception {
        mockMvc.perform(get("/test/validate/complex-combo")
                        .header("x-sure-auth-aksk-user-type", "vip")
                        .header("x-sure-auth-aksk-account-status", "active")
                        .header("x-sure-auth-aksk-risk-level", "low")
                        .header("x-sure-auth-aksk-credit-score", "75"))
                .andExpect(status().isForbidden());
    }

    // ==================== 解析 securityContext 字段内容的测试 ====================

    /**
     * 测试解析 securityContext - 包含 role:admin
     */
    @Test
    public void testParseSecurityContextContainsKeyValueSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/contains-key-value")
                        .header("x-sure-auth-aksk-security-context", "role:admin,level:5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context contains role:admin"))
                .andExpect(jsonPath("$.securityContext").value("role:admin,level:5"));
    }

    /**
     * 测试解析 securityContext - 不包含 role:admin 时失败
     */
    @Test
    public void testParseSecurityContextContainsKeyValueFail() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/contains-key-value")
                        .header("x-sure-auth-aksk-security-context", "role:user,level:1"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试解析 securityContext - 包含多个键值对（AND 逻辑）
     */
    @Test
    public void testParseSecurityContextMultipleKeyValuesSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/multiple-key-values")
                        .header("x-sure-auth-aksk-security-context", "role:admin,level:5,status:active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context contains both role:admin and level:5"));
    }

    /**
     * 测试解析 securityContext - 缺少其中一个键值对时失败
     */
    @Test
    public void testParseSecurityContextMultipleKeyValuesMissingOneFail() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/multiple-key-values")
                        .header("x-sure-auth-aksk-security-context", "role:admin,status:active"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试解析 securityContext - OR 逻辑（包含 admin）
     */
    @Test
    public void testParseSecurityContextOrValuesAdminSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/or-values")
                        .header("x-sure-auth-aksk-security-context", "role:admin,level:5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context contains admin or manager role"));
    }

    /**
     * 测试解析 securityContext - OR 逻辑（包含 manager）
     */
    @Test
    public void testParseSecurityContextOrValuesManagerSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/or-values")
                        .header("x-sure-auth-aksk-security-context", "role:manager,level:3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context contains admin or manager role"));
    }

    /**
     * 测试解析 securityContext - OR 逻辑（两者都不包含时失败）
     */
    @Test
    public void testParseSecurityContextOrValuesNeitherFail() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/or-values")
                        .header("x-sure-auth-aksk-security-context", "role:user,level:1"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试解析 securityContext - JSON 格式单个字段
     */
    @Test
    public void testParseSecurityContextJsonFieldSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/json-field")
                        .header("x-sure-auth-aksk-security-context", "{\"userType\":\"premium\",\"status\":\"active\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context contains userType:premium in JSON format"));
    }

    /**
     * 测试解析 securityContext - JSON 格式字段值不匹配时失败
     */
    @Test
    public void testParseSecurityContextJsonFieldFail() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/json-field")
                        .header("x-sure-auth-aksk-security-context", "{\"userType\":\"basic\",\"status\":\"active\"}"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试解析 securityContext - JSON 格式多个字段
     */
    @Test
    public void testParseSecurityContextJsonMultipleFieldsSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/json-multiple-fields")
                        .header("x-sure-auth-aksk-security-context", "{\"userType\":\"premium\",\"status\":\"active\",\"level\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context contains premium user with active status"));
    }

    /**
     * 测试解析 securityContext - JSON 格式缺少字段时失败
     */
    @Test
    public void testParseSecurityContextJsonMultipleFieldsMissingFail() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/json-multiple-fields")
                        .header("x-sure-auth-aksk-security-context", "{\"userType\":\"premium\",\"level\":5}"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试解析 securityContext - VIP 用户
     */
    @Test
    public void testParseSecurityContextVipSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/vip-or-high-level")
                        .header("x-sure-auth-aksk-security-context", "userType:vip,status:active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context indicates VIP or leveled user"));
    }

    /**
     * 测试解析 securityContext - 高级别用户
     */
    @Test
    public void testParseSecurityContextHighLevelSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/vip-or-high-level")
                        .header("x-sure-auth-aksk-security-context", "userType:regular,level:8"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context indicates VIP or leveled user"));
    }

    /**
     * 测试解析 securityContext - 权限列表完整
     */
    @Test
    public void testParseSecurityContextPermissionsSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/permissions")
                        .header("x-sure-auth-aksk-security-context", "permissions:read,write,delete,admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context contains full permissions"));
    }

    /**
     * 测试解析 securityContext - 权限列表不完整时失败
     */
    @Test
    public void testParseSecurityContextPermissionsIncompleteFail() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/permissions")
                        .header("x-sure-auth-aksk-security-context", "permissions:read,write"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试解析 securityContext - 特定权限存在
     */
    @Test
    public void testParseSecurityContextSpecificPermissionSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/specific-permission")
                        .header("x-sure-auth-aksk-security-context", "permissions:read,write,delete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context contains delete permission"));
    }

    /**
     * 测试解析 securityContext - 特定权限不存在时失败
     */
    @Test
    public void testParseSecurityContextSpecificPermissionFail() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/specific-permission")
                        .header("x-sure-auth-aksk-security-context", "permissions:read,write"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试解析 securityContext - 租户隔离
     */
    @Test
    public void testParseSecurityContextTenantIsolationSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/tenant-isolation")
                        .header("x-sure-auth-aksk-security-context", "tenantId:tenant-123,userId:user456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context contains tenant-123"));
    }

    /**
     * 测试解析 securityContext - 租户 ID 不匹配时失败
     */
    @Test
    public void testParseSecurityContextTenantIsolationFail() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/tenant-isolation")
                        .header("x-sure-auth-aksk-security-context", "tenantId:tenant-456,userId:user456"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试解析 securityContext - 租户 + admin 角色
     */
    @Test
    public void testParseSecurityContextTenantAndRoleAdminSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/tenant-and-role")
                        .header("x-sure-auth-aksk-security-context", "tenantId:tenant-123,role:admin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context contains tenant-123 with admin or owner role"));
    }

    /**
     * 测试解析 securityContext - 租户 + owner 角色
     */
    @Test
    public void testParseSecurityContextTenantAndRoleOwnerSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/tenant-and-role")
                        .header("x-sure-auth-aksk-security-context", "tenantId:tenant-123,role:owner"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context contains tenant-123 with admin or owner role"));
    }

    /**
     * 测试解析 securityContext - 租户匹配但角色不匹配时失败
     */
    @Test
    public void testParseSecurityContextTenantAndRoleWrongRoleFail() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/tenant-and-role")
                        .header("x-sure-auth-aksk-security-context", "tenantId:tenant-123,role:user"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试解析 securityContext - 订阅状态验证
     */
    @Test
    public void testParseSecurityContextSubscriptionStatusSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/subscription-status")
                        .header("x-sure-auth-aksk-security-context", "subscription:active,plan:premium,userId:user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context contains active premium subscription"));
    }

    /**
     * 测试解析 securityContext - 订阅状态不是 active 时失败
     */
    @Test
    public void testParseSecurityContextSubscriptionStatusInactiveFail() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/subscription-status")
                        .header("x-sure-auth-aksk-security-context", "subscription:expired,plan:premium,userId:user123"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试解析 securityContext - 地域限制（CN）
     */
    @Test
    public void testParseSecurityContextRegionCheckCNSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/region-check")
                        .header("x-sure-auth-aksk-security-context", "region:CN,userId:user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context contains allowed region (CN or US)"));
    }

    /**
     * 测试解析 securityContext - 地域限制（US）
     */
    @Test
    public void testParseSecurityContextRegionCheckUSSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/region-check")
                        .header("x-sure-auth-aksk-security-context", "region:US,userId:user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context contains allowed region (CN or US)"));
    }

    /**
     * 测试解析 securityContext - 地域不允许时失败
     */
    @Test
    public void testParseSecurityContextRegionCheckFail() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/region-check")
                        .header("x-sure-auth-aksk-security-context", "region:EU,userId:user123"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试解析 securityContext - 设备信任度验证
     */
    @Test
    public void testParseSecurityContextDeviceTrustSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/device-trust")
                        .header("x-sure-auth-aksk-security-context", "deviceTrust:trusted,deviceType:mobile,userId:user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context contains trusted mobile device"));
    }

    /**
     * 测试解析 securityContext - 设备不受信任时失败
     */
    @Test
    public void testParseSecurityContextDeviceTrustUntrustedFail() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/device-trust")
                        .header("x-sure-auth-aksk-security-context", "deviceTrust:untrusted,deviceType:mobile,userId:user123"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试解析 securityContext - 复杂组合验证
     */
    @Test
    public void testParseSecurityContextComplexCombinationSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/complex-combination")
                        .header("x-sure-auth-aksk-security-context", "userType:premium,subscription:active,permissions:read,write,api:access,userId:user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context validated: premium user with active subscription and API access"));
    }

    /**
     * 测试解析 securityContext - 复杂组合缺少 userType 时失败
     */
    @Test
    public void testParseSecurityContextComplexCombinationMissingUserTypeFail() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/complex-combination")
                        .header("x-sure-auth-aksk-security-context", "subscription:active,permissions:read,write,api:access,userId:user123"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试解析 securityContext - JWT token 格式
     */
    @Test
    public void testParseSecurityContextJwtContentSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/jwt-content")
                        .header("x-sure-auth-aksk-security-context", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJ1c2VyMTIzIiwicm9sZSI6ImFkbWluIn0.signature"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context is a valid JWT token format"));
    }

    /**
     * 测试解析 securityContext - 不是 JWT 格式时失败
     */
    @Test
    public void testParseSecurityContextJwtContentFail() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/jwt-content")
                        .header("x-sure-auth-aksk-security-context", "role:admin,level:5"))
                .andExpect(status().isForbidden());
    }

    /**
     * 测试解析 securityContext - Base64 编码格式
     */
    @Test
    public void testParseSecurityContextBase64EncodedSuccess() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/base64-encoded")
                        .header("x-sure-auth-aksk-security-context", "dXNlcklkOnVzZXIxMjMscm9sZTphZG1pbg=="))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Security context is Base64 encoded"));
    }

    /**
     * 测试解析 securityContext - 不是 Base64 格式时失败
     */
    @Test
    public void testParseSecurityContextBase64EncodedFail() throws Exception {
        mockMvc.perform(get("/test/parse-security-context/base64-encoded")
                        .header("x-sure-auth-aksk-security-context", "role:admin,level:5"))
                .andExpect(status().isForbidden());
    }
}
