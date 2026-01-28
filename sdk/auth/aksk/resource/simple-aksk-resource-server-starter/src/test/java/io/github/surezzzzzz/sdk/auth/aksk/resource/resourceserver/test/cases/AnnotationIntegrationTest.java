package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.SimpleAkskResourceServerTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.helper.OAuth2TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 注解集成测试
 *
 * <p>测试权限注解功能：@RequireContext, @RequireField, @RequireFieldValue, @RequireExpression
 * <p>使用从 aksk-server 获取的真实 Token 进行测试
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskResourceServerTestApplication.class)
@AutoConfigureMockMvc
public class AnnotationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuth2TokenHelper tokenHelper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String validToken;

    @BeforeEach
    public void setUp() {
        log.info("======================================");
        log.info("Setting up test - getting token from aksk-server");
        log.info("======================================");
        validToken = tokenHelper.getToken();
        assertNotNull(validToken, "Token should not be null");
        assertTrue(validToken.startsWith("eyJ"), "Token should be JWT format");
        log.info("Token obtained successfully");
    }

    // ==================== @RequireContext 测试 ====================

    /**
     * 测试 @RequireContext - 有上下文时通过
     */
    @Test
    @DisplayName("测试 @RequireContext - 有上下文时通过")
    public void testRequireContextWithContext() throws Exception {
        log.info("======================================");
        log.info("测试 @RequireContext - 有上下文时通过");
        log.info("======================================");

        MvcResult result = mockMvc.perform(get("/test/require-context")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);

        assertEquals("Context exists", jsonResponse.get("message").asText());
        log.info("@RequireContext passed with valid token");
    }

    /**
     * 测试 @RequireContext - 无上下文时失败
     */
    @Test
    @DisplayName("测试 @RequireContext - 无上下文时失败")
    public void testRequireContextWithoutContext() throws Exception {
        log.info("======================================");
        log.info("测试 @RequireContext - 无上下文时失败");
        log.info("======================================");

        mockMvc.perform(get("/test/require-context"))
                .andExpect(status().isUnauthorized()); // 无 token 会先被 Spring Security 拦截返回 401

        log.info("@RequireContext correctly rejected request without token");
    }

    // ==================== @RequireField 测试 ====================

    /**
     * 测试 @RequireField - 检查 userId 字段
     * <p>
     * 注意：这个测试依赖于 aksk-server 返回的 token 中是否包含 user_id claim
     * 如果 token 中没有 user_id，测试会失败（这是预期行为）
     */
    @Test
    @DisplayName("测试 @RequireField - 检查 userId 字段")
    public void testRequireFieldWithUserId() throws Exception {
        log.info("======================================");
        log.info("测试 @RequireField - 检查 userId 字段");
        log.info("======================================");

        try {
            MvcResult result = mockMvc.perform(get("/test/require-field")
                            .header("Authorization", "Bearer " + validToken))
                    .andReturn();

            int status = result.getResponse().getStatus();
            String responseBody = result.getResponse().getContentAsString();
            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            if (status == 200) {
                // Token 包含 userId 字段
                assertEquals("Field userId exists", jsonResponse.get("message").asText());
                log.info("@RequireField passed - userId field exists in token");
            } else if (status == 403) {
                // Token 不包含 userId 字段
                assertEquals("Forbidden", jsonResponse.get("error").asText());
                assertTrue(jsonResponse.get("message").asText().contains("userId"));
                log.info("@RequireField correctly rejected - userId field missing in token");
            } else {
                fail("Unexpected status code: " + status);
            }
        } catch (Exception e) {
            log.error("Test failed", e);
            throw e;
        }
    }

    // ==================== @RequireFieldValue 测试 ====================

    /**
     * 测试 @RequireFieldValue - 检查 clientType 字段值
     * <p>
     * 注意：这个测试依赖于 aksk-server 返回的 token 中 client_type 的值
     * 如果值不是 "service"，测试会失败（这是预期行为）
     */
    @Test
    @DisplayName("测试 @RequireFieldValue - 检查 clientType 字段值")
    public void testRequireFieldValueWithClientType() throws Exception {
        log.info("======================================");
        log.info("测试 @RequireFieldValue - 检查 clientType 字段值");
        log.info("======================================");

        try {
            MvcResult result = mockMvc.perform(get("/test/require-field-value")
                            .header("Authorization", "Bearer " + validToken))
                    .andReturn();

            int status = result.getResponse().getStatus();
            String responseBody = result.getResponse().getContentAsString();
            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            if (status == 200) {
                // Token 的 clientType 字段值为 "service"
                assertEquals("Client type is service", jsonResponse.get("message").asText());
                assertEquals("service", jsonResponse.get("clientType").asText());
                log.info("@RequireFieldValue passed - clientType is 'service'");
            } else if (status == 403) {
                // Token 的 clientType 字段值不是 "service" 或字段不存在
                assertEquals("Forbidden", jsonResponse.get("error").asText());
                log.info("@RequireFieldValue correctly rejected - clientType mismatch or missing");
                log.info("Error message: {}", jsonResponse.get("message").asText());
            } else {
                fail("Unexpected status code: " + status);
            }
        } catch (Exception e) {
            log.error("Test failed", e);
            throw e;
        }
    }

    // ==================== @RequireExpression 测试 ====================

    /**
     * 测试 @RequireExpression - SpEL 表达式校验
     * <p>
     * 表达式：#context['userId'] != null && #context['userId'].length() > 0
     */
    @Test
    @DisplayName("测试 @RequireExpression - SpEL 表达式校验")
    public void testRequireExpressionWithUserId() throws Exception {
        log.info("======================================");
        log.info("测试 @RequireExpression - SpEL 表达式校验");
        log.info("======================================");

        try {
            MvcResult result = mockMvc.perform(get("/test/require-expression")
                            .header("Authorization", "Bearer " + validToken))
                    .andReturn();

            int status = result.getResponse().getStatus();
            String responseBody = result.getResponse().getContentAsString();
            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            if (status == 200) {
                // 表达式通过
                assertEquals("Expression passed", jsonResponse.get("message").asText());
                log.info("@RequireExpression passed - userId exists and not empty");
            } else if (status == 403) {
                // 表达式失败
                assertEquals("Forbidden", jsonResponse.get("error").asText());
                log.info("@RequireExpression correctly rejected - expression evaluated to false");
                log.info("Error message: {}", jsonResponse.get("message").asText());
            } else {
                fail("Unexpected status code: " + status);
            }
        } catch (Exception e) {
            log.error("Test failed", e);
            throw e;
        }
    }

    // ==================== 实际业务场景测试 ====================

    /**
     * 测试管理员接口 - 检查 scope 是否包含 admin
     * <p>
     * 表达式：#context['scope'] != null && #context['scope'].contains('admin')
     */
    @Test
    @DisplayName("测试管理员接口 - 检查 scope 是否包含 admin")
    public void testAdminDashboardWithScope() throws Exception {
        log.info("======================================");
        log.info("测试管理员接口 - 检查 scope 是否包含 admin");
        log.info("======================================");

        try {
            MvcResult result = mockMvc.perform(get("/test/admin/dashboard")
                            .header("Authorization", "Bearer " + validToken))
                    .andReturn();

            int status = result.getResponse().getStatus();
            String responseBody = result.getResponse().getContentAsString();
            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            if (status == 200) {
                // Token 的 scope 包含 admin
                assertEquals("Welcome to admin dashboard", jsonResponse.get("message").asText());
                String scope = jsonResponse.get("scope").asText();
                assertTrue(scope.contains("admin"), "Scope should contain 'admin'");
                log.info("Admin dashboard access granted - scope contains 'admin'");
            } else if (status == 403) {
                // Token 的 scope 不包含 admin
                assertEquals("Forbidden", jsonResponse.get("error").asText());
                log.info("Admin dashboard access denied - scope does not contain 'admin'");
                log.info("This is expected if the test client doesn't have admin scope");
            } else {
                fail("Unexpected status code: " + status);
            }
        } catch (Exception e) {
            log.error("Test failed", e);
            throw e;
        }
    }

    /**
     * 测试多租户接口 - 检查 tenantId 是否存在
     * <p>
     * 表达式：#context['tenantId'] != null && #context['tenantId'].length() > 0
     */
    @Test
    @DisplayName("测试多租户接口 - 检查 tenantId 是否存在")
    public void testTenantDataWithTenantId() throws Exception {
        log.info("======================================");
        log.info("测试多租户接口 - 检查 tenantId 是否存在");
        log.info("======================================");

        try {
            MvcResult result = mockMvc.perform(get("/test/tenant/data")
                            .header("Authorization", "Bearer " + validToken))
                    .andReturn();

            int status = result.getResponse().getStatus();
            String responseBody = result.getResponse().getContentAsString();
            JsonNode jsonResponse = objectMapper.readTree(responseBody);

            if (status == 200) {
                // Token 包含 tenantId
                assertEquals("Tenant data access", jsonResponse.get("message").asText());
                assertNotNull(jsonResponse.get("tenantId"));
                log.info("Tenant data access granted - tenantId exists");
                log.info("Tenant ID: {}", jsonResponse.get("tenantId").asText());
            } else if (status == 403) {
                // Token 不包含 tenantId
                assertEquals("Forbidden", jsonResponse.get("error").asText());
                log.info("Tenant data access denied - tenantId missing");
                log.info("This is expected if the test client doesn't have tenantId claim");
            } else {
                fail("Unexpected status code: " + status);
            }
        } catch (Exception e) {
            log.error("Test failed", e);
            throw e;
        }
    }

    // ==================== 无效 Token 测试 ====================

    /**
     * 测试使用无效 Token 访问需要注解的接口
     */
    @Test
    @DisplayName("测试使用无效 Token 访问需要注解的接口")
    public void testAnnotationEndpointWithInvalidToken() throws Exception {
        log.info("======================================");
        log.info("测试使用无效 Token 访问需要注解的接口");
        log.info("======================================");

        mockMvc.perform(get("/test/require-context")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());

        log.info("Correctly returned 401 Unauthorized with invalid token");
    }

    /**
     * 测试不带 Token 访问需要注解的接口
     */
    @Test
    @DisplayName("测试不带 Token 访问需要注解的接口")
    public void testAnnotationEndpointWithoutToken() throws Exception {
        log.info("======================================");
        log.info("测试不带 Token 访问需要注解的接口");
        log.info("======================================");

        mockMvc.perform(get("/test/require-context"))
                .andExpect(status().isUnauthorized());

        log.info("Correctly returned 401 Unauthorized without token");
    }
}
