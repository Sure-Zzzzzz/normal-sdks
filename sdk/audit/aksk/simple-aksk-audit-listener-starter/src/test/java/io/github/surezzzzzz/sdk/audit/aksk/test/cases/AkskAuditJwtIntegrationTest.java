package io.github.surezzzzzz.sdk.audit.aksk.test.cases;

import io.github.surezzzzzz.sdk.audit.aksk.model.AkskAuditRecord;
import io.github.surezzzzzz.sdk.audit.aksk.test.AkskAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.aksk.test.TestAkskAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.test.TestTraceIdProvider;
import io.github.surezzzzzz.sdk.audit.aksk.test.helper.OAuth2TokenHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * AKSK JWT认证审计集成测试
 *
 * <p>测试真实的HTTP请求 -> JWT认证 -> 审计事件发布 -> 审计记录捕获的完整链路
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(
        classes = AkskAuditListenerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "io.github.surezzzzzz.sdk.auth.aksk.resource.security-context.enable=false"  // 禁用Header认证，只测JWT认证
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("local")  // 激活 application-local.yml
public class AkskAuditJwtIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestAkskAuditHandler testAuditHandler;

    @Autowired
    private TestTraceIdProvider testTraceIdProvider;

    @Autowired
    private OAuth2TokenHelper tokenHelper;

    private String validToken;

    @BeforeEach
    public void setUp() {
        log.info("========== 开始准备 AKSK JWT 审计集成测试 ==========");
        testAuditHandler.reset();
        testTraceIdProvider.reset();

        // 从 aksk-server 获取真实的 JWT Token
        validToken = tokenHelper.getToken();
        assertNotNull(validToken, "Token should not be null");
        assertTrue(validToken.startsWith("eyJ"), "Token should be JWT format");
        log.info("========== AKSK JWT 审计集成测试准备完成 ==========");
    }

    @Test
    public void testJwtAuthenticationWithAudit() throws Exception {
        log.info("========== 测试：JWT认证 + 审计 ==========");

        // 准备：设置 traceId
        testTraceIdProvider.setTraceId("jwt-trace-123");

        // 发送带JWT Token的HTTP请求
        org.springframework.test.web.servlet.MvcResult result = mockMvc.perform(get("/test/api")
                        .header("Authorization", "Bearer " + validToken))
                .andReturn();
        log.info("HTTP Status: {}", result.getResponse().getStatus());
        log.info("Response Body: {}", result.getResponse().getContentAsString());

        // 等待异步审计处理
        boolean received = testAuditHandler.latch.await(5, TimeUnit.SECONDS);

        // 验证：审计事件已捕获
        log.info("验证审计事件是否被捕获");
        assertTrue(received, "Audit handler should receive the event from JWT authentication");
        assertEquals(1, testAuditHandler.records.size(), "Should receive exactly one audit record");

        // 验证：审计记录内容
        AkskAuditRecord record = testAuditHandler.records.get(0);
        log.info("验证审计记录内容: {}", record);
        assertNotNull(record.getClientId());
        assertNotNull(record.getClientType());
        assertEquals("/test/api", record.getRequestUri());
        assertEquals("GET", record.getHttpMethod());
        assertEquals("jwt", record.getSource());  // JWT认证的source是"jwt"
        assertNotNull(record.getTimestamp());

        // 验证：traceId 来自 Provider
        assertEquals("jwt-trace-123", record.getTraceId());

        log.info("JWT integration test passed: HTTP request -> JWT Auth -> Audit event -> Audit record captured");
    }

    @Test
    public void testJwtAuthenticationWithMultipleRequests() throws Exception {
        log.info("========== 测试：JWT认证多个请求的审计 ==========");

        testTraceIdProvider.setTraceId("jwt-multi-trace");

        // 发送多个带JWT的请求
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/api/jwt/test/" + i)
                    .header("Authorization", "Bearer " + validToken));
            // 不验证HTTP状态
        }

        // 等待处理
        Thread.sleep(1000);

        // 验证：收到2个审计记录
        log.info("验证是否收到2个审计记录");
        assertEquals(2, testAuditHandler.records.size(), "Should receive 2 audit records");

        // 验证：所有记录都是JWT认证
        for (AkskAuditRecord record : testAuditHandler.records) {
            assertEquals("jwt", record.getSource());
            assertEquals("jwt-multi-trace", record.getTraceId());
        }

        log.info("JWT multiple requests integration test passed: received {} audit records",
                testAuditHandler.records.size());
    }
}
