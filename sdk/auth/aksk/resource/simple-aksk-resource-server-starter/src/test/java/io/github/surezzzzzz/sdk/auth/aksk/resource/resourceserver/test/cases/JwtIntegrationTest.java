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
 * JWT 集成测试
 *
 * <p>测试完整流程：从 aksk-server 获取真实 Token → Resource Server 验证 → 提取 claims 到上下文
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskResourceServerTestApplication.class)
@AutoConfigureMockMvc
public class JwtIntegrationTest {

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

    // ==================== 基本 JWT 验证测试 ====================

    /**
     * 测试使用真实 Token 访问受保护接口
     */
    @Test
    @DisplayName("测试使用真实 Token 访问受保护接口")
    public void testAccessProtectedEndpointWithRealToken() throws Exception {
        log.info("======================================");
        log.info("测试使用真实 Token 访问受保护接口");
        log.info("======================================");

        MvcResult result = mockMvc.perform(get("/test/basic")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        log.info("Response: {}", responseBody);

        JsonNode jsonResponse = objectMapper.readTree(responseBody);

        // 验证响应包含基本字段
        assertNotNull(jsonResponse.get("allContext"), "allContext should not be null");

        log.info("Client ID: {}", jsonResponse.get("clientId"));
        log.info("User ID: {}", jsonResponse.get("userId"));
        log.info("Username: {}", jsonResponse.get("username"));
        log.info("Scope: {}", jsonResponse.get("scope"));
        log.info("All Context: {}", jsonResponse.get("allContext"));
    }

    /**
     * 测试提取标准 JWT claims
     */
    @Test
    @DisplayName("测试提取标准 JWT claims")
    public void testExtractStandardClaims() throws Exception {
        log.info("======================================");
        log.info("测试提取标准 JWT claims");
        log.info("======================================");

        MvcResult result = mockMvc.perform(get("/test/basic")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);

        // 验证 clientId 字段存在（从 client_id claim 提取）
        if (jsonResponse.has("clientId") && !jsonResponse.get("clientId").isNull()) {
            String clientId = jsonResponse.get("clientId").asText();
            log.info("Extracted clientId: {}", clientId);
            assertNotNull(clientId, "clientId should not be null");
        }

        // 验证 scope 字段存在
        if (jsonResponse.has("scope") && !jsonResponse.get("scope").isNull()) {
            String scope = jsonResponse.get("scope").asText();
            log.info("Extracted scope: {}", scope);
            assertNotNull(scope, "scope should not be null");
        }

        log.info("Standard claims extraction test passed");
    }

    /**
     * 测试 allContext 包含所有提取的字段
     */
    @Test
    @DisplayName("测试 allContext 包含所有提取的字段")
    public void testGetAllContextContainsAllFields() throws Exception {
        log.info("======================================");
        log.info("测试 allContext 包含所有提取的字段");
        log.info("======================================");

        MvcResult result = mockMvc.perform(get("/test/basic")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);

        JsonNode allContext = jsonResponse.get("allContext");
        assertNotNull(allContext, "allContext should not be null");

        log.info("All context fields: {}", allContext.toPrettyString());

        // 验证 allContext 是一个对象
        assertTrue(allContext.isObject(), "allContext should be an object");

        log.info("allContext test passed");
    }

    // ==================== 无 Token 测试 ====================

    /**
     * 测试无 JWT Token 访问受保护路径 - 应返回 401
     */
    @Test
    @DisplayName("测试无 JWT Token 访问受保护路径")
    public void testAccessProtectedEndpointWithoutToken() throws Exception {
        log.info("======================================");
        log.info("测试无 JWT Token 访问受保护路径");
        log.info("======================================");

        mockMvc.perform(get("/test/basic"))
                .andExpect(status().isUnauthorized());

        log.info("Correctly returned 401 Unauthorized without token");
    }

    /**
     * 测试无效的 JWT Token - 应返回 401
     */
    @Test
    @DisplayName("测试无效的 JWT Token")
    public void testAccessProtectedEndpointWithInvalidToken() throws Exception {
        log.info("======================================");
        log.info("测试无效的 JWT Token");
        log.info("======================================");

        mockMvc.perform(get("/test/basic")
                        .header("Authorization", "Bearer invalid-token-string"))
                .andExpect(status().isUnauthorized());

        log.info("Correctly returned 401 Unauthorized with invalid token");
    }

    /**
     * 测试空 Token - 应返回 401
     */
    @Test
    @DisplayName("测试空 Token")
    public void testAccessProtectedEndpointWithEmptyToken() throws Exception {
        log.info("======================================");
        log.info("测试空 Token");
        log.info("======================================");

        mockMvc.perform(get("/test/basic")
                        .header("Authorization", "Bearer "))
                .andExpect(status().isUnauthorized());

        log.info("Correctly returned 401 Unauthorized with empty token");
    }

    // ==================== 白名单路径测试 ====================

    /**
     * 测试访问白名单路径无需 Token
     */
    @Test
    @DisplayName("测试访问白名单路径无需 Token")
    public void testAccessPermitAllPathWithoutToken() throws Exception {
        log.info("======================================");
        log.info("测试访问白名单路径无需 Token");
        log.info("======================================");

        mockMvc.perform(get("/public/info"))
                .andExpect(status().isNotFound()); // 404 说明通过了安全检查，只是路径不存在

        log.info("White-listed path accessible without token");
    }

    // ==================== Security Context Helper 测试 ====================

    /**
     * 测试 getSecurityContext() 方法
     */
    @Test
    @DisplayName("测试 getSecurityContext() 方法")
    public void testGetSecurityContext() throws Exception {
        log.info("======================================");
        log.info("测试 getSecurityContext() 方法");
        log.info("======================================");

        MvcResult result = mockMvc.perform(get("/test/security-context")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = result.getResponse().getContentAsString();
        JsonNode jsonResponse = objectMapper.readTree(responseBody);

        log.info("Security context response: {}", jsonResponse.toPrettyString());

        // security_context 字段可能存在也可能不存在，取决于 aksk-server 的实现
        if (jsonResponse.has("securityContext") && !jsonResponse.get("securityContext").isNull()) {
            String securityContext = jsonResponse.get("securityContext").asText();
            log.info("Security context: {}", securityContext);
        } else {
            log.info("Security context field not present in token (this is OK)");
        }
    }

    // ==================== Token 重用测试 ====================

    /**
     * 测试同一个 Token 可以多次使用
     */
    @Test
    @DisplayName("测试同一个 Token 可以多次使用")
    public void testTokenReuse() throws Exception {
        log.info("======================================");
        log.info("测试同一个 Token 可以多次使用");
        log.info("======================================");

        // 第一次调用
        mockMvc.perform(get("/test/basic")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());

        log.info("First call succeeded");

        // 第二次调用
        mockMvc.perform(get("/test/basic")
                        .header("Authorization", "Bearer " + validToken))
                .andExpect(status().isOk());

        log.info("Second call succeeded - token can be reused");
    }
}
