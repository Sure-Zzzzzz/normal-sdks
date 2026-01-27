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
 * 安全上下文集成测试
 *
 * <p>测试完整流程：HTTP Header → Filter 提取 → Request Attribute → AkskUserContext API
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@SpringBootTest(classes = SimpleAkskSecurityContextTestApplication.class)
@AutoConfigureMockMvc
public class SecurityContextIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ==================== 基本字段提取测试 ====================

    /**
     * 测试提取单个字段
     */
    @Test
    public void testExtractSingleField() throws Exception {
        mockMvc.perform(get("/test/basic")
                        .header("x-sure-auth-aksk-user-id", "user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user123"));
    }

    /**
     * 测试提取多个字段
     */
    @Test
    public void testExtractMultipleFields() throws Exception {
        mockMvc.perform(get("/test/basic")
                        .header("x-sure-auth-aksk-user-id", "user123")
                        .header("x-sure-auth-aksk-username", "testuser")
                        .header("x-sure-auth-aksk-client-id", "client456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user123"))
                .andExpect(jsonPath("$.username").value("testuser"))
                .andExpect(jsonPath("$.clientId").value("client456"));
    }

    // ==================== Header 名称转换测试 ====================

    /**
     * 测试 Header 名称转换为 camelCase
     */
    @Test
    public void testHeaderNameConversionToCamelCase() throws Exception {
        mockMvc.perform(get("/test/custom-fields")
                        .header("x-sure-auth-aksk-tenant-id", "tenant123")
                        .header("x-sure-auth-aksk-org-id", "org456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant123"))
                .andExpect(jsonPath("$.orgId").value("org456"));
    }

    // ==================== 数组字段提取测试 ====================

    /**
     * 测试提取数组字段（roles）
     */
    @Test
    public void testExtractArrayFieldRoles() throws Exception {
        mockMvc.perform(get("/test/array-fields")
                        .header("x-sure-auth-aksk-roles0", "admin")
                        .header("x-sure-auth-aksk-roles1", "operator")
                        .header("x-sure-auth-aksk-roles2", "viewer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("admin"))
                .andExpect(jsonPath("$.roles[1]").value("operator"))
                .andExpect(jsonPath("$.roles[2]").value("viewer"))
                .andExpect(jsonPath("$.rolesCount").value(3));
    }

    /**
     * 测试提取数组字段（scope）
     */
    @Test
    public void testExtractArrayFieldScope() throws Exception {
        mockMvc.perform(get("/test/array-fields")
                        .header("x-sure-auth-aksk-scope0", "read")
                        .header("x-sure-auth-aksk-scope1", "write"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scope[0]").value("read"))
                .andExpect(jsonPath("$.scope[1]").value("write"))
                .andExpect(jsonPath("$.scopeCount").value(2));
    }

    // ==================== 自定义字段提取测试 ====================

    /**
     * 测试提取自定义字段
     */
    @Test
    public void testExtractCustomFields() throws Exception {
        mockMvc.perform(get("/test/custom-fields")
                        .header("x-sure-auth-aksk-tenant-id", "tenant123")
                        .header("x-sure-auth-aksk-org-id", "org456")
                        .header("x-sure-auth-aksk-custom-field", "customValue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId").value("tenant123"))
                .andExpect(jsonPath("$.orgId").value("org456"))
                .andExpect(jsonPath("$.customField").value("customValue"));
    }

    /**
     * 测试获取 security_context
     */
    @Test
    public void testGetSecurityContext() throws Exception {
        mockMvc.perform(get("/test/security-context")
                        .header("x-sure-auth-aksk-security-context", "eyJhbGciOiJIUzI1NiJ9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securityContext").value("eyJhbGciOiJIUzI1NiJ9"));
    }

    /**
     * 测试获取完整的安全上下文（包含多个字段）
     */
    @Test
    public void testGetSecurityContextWithMultipleFields() throws Exception {
        mockMvc.perform(get("/test/security-context")
                        .header("x-sure-auth-aksk-user-id", "user123")
                        .header("x-sure-auth-aksk-username", "testuser")
                        .header("x-sure-auth-aksk-client-id", "client456")
                        .header("x-sure-auth-aksk-security-context", "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJ1c2VyMTIzIn0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securityContext").value("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1c2VySWQiOiJ1c2VyMTIzIn0"));
    }

    /**
     * 测试安全上下文为空时的行为
     */
    @Test
    public void testGetSecurityContextWhenEmpty() throws Exception {
        mockMvc.perform(get("/test/security-context")
                        .header("x-sure-auth-aksk-user-id", "user123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.securityContext").isEmpty());
    }

    /**
     * 测试 allContext 包含所有提取的字段
     */
    @Test
    public void testGetAllContextContainsAllFields() throws Exception {
        mockMvc.perform(get("/test/basic")
                        .header("x-sure-auth-aksk-user-id", "user123")
                        .header("x-sure-auth-aksk-username", "testuser")
                        .header("x-sure-auth-aksk-client-id", "client456")
                        .header("x-sure-auth-aksk-tenant-id", "tenant789")
                        .header("x-sure-auth-aksk-security-context", "eyJhbGciOiJIUzI1NiJ9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allContext.userId").value("user123"))
                .andExpect(jsonPath("$.allContext.username").value("testuser"))
                .andExpect(jsonPath("$.allContext.clientId").value("client456"))
                .andExpect(jsonPath("$.allContext.tenantId").value("tenant789"))
                .andExpect(jsonPath("$.allContext.securityContext").value("eyJhbGciOiJIUzI1NiJ9"));
    }

    // ==================== 边界情况测试 ====================

    /**
     * 测试无 Header 时的行为
     */
    @Test
    public void testNoHeaders() throws Exception {
        mockMvc.perform(get("/test/basic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isEmpty())
                .andExpect(jsonPath("$.username").isEmpty())
                .andExpect(jsonPath("$.roles").isEmpty());
    }

    /**
     * 测试空 Header 值
     */
    @Test
    public void testEmptyHeaderValue() throws Exception {
        mockMvc.perform(get("/test/basic")
                        .header("x-sure-auth-aksk-user-id", "")
                        .header("x-sure-auth-aksk-username", "testuser"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").isEmpty())
                .andExpect(jsonPath("$.username").value("testuser"));
    }

    /**
     * 测试忽略非前缀 Header
     */
    @Test
    public void testIgnoreNonPrefixedHeaders() throws Exception {
        mockMvc.perform(get("/test/basic")
                        .header("x-sure-auth-aksk-user-id", "user123")
                        .header("Authorization", "Bearer token")
                        .header("Content-Type", "application/json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user123"))
                .andExpect(jsonPath("$.allContext.authorization").doesNotExist())
                .andExpect(jsonPath("$.allContext.contentType").doesNotExist());
    }
}
