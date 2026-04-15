package io.github.surezzzzzz.sdk.audit.aksk.test.cases;

import io.github.surezzzzzz.sdk.audit.aksk.resource.model.AkskAuditRecord;
import io.github.surezzzzzz.sdk.audit.aksk.test.AkskAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.aksk.test.TestAkskAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.test.TestTraceIdProvider;
import io.github.surezzzzzz.sdk.audit.aksk.test.config.TestSecurityConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * AKSK 审计集成测试
 *
 * <p>测试真实的HTTP请求 -> 认证 -> 审计事件发布 -> 审计记录捕获的完整链路
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(
        classes = AkskAuditListenerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled=false",  // 禁用JWT认证，只测Header认证
                "test.security.permit-all=true"  // 启用TestSecurityConfig，允许所有请求通过
        }
)
@AutoConfigureMockMvc
@Import(TestSecurityConfig.class)  // 导入测试Security配置，允许所有请求通过
public class AkskAuditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestAkskAuditHandler testAuditHandler;

    @Autowired
    private TestTraceIdProvider testTraceIdProvider;

    @BeforeEach
    public void setUp() {
        log.info("========== 开始准备 AKSK 审计集成测试 ==========");
        testAuditHandler.reset();
        testTraceIdProvider.reset();
        log.info("========== AKSK 审计集成测试准备完成 ==========");
    }

    @Test
    public void testHeaderAuthenticationWithAudit() throws Exception {
        log.info("========== 测试：Header认证 + 审计 ==========");

        // 准备：设置 traceId
        testTraceIdProvider.setTraceId("integration-trace-123");

        // 发送带认证Header的HTTP请求
        mockMvc.perform(get("/test/api")
                .header("x-sure-auth-aksk-client-id", "integration-client")
                .header("x-sure-auth-aksk-client-type", "platform")
                .header("x-sure-auth-aksk-user-id", "integration-user-123")
                .header("x-sure-auth-aksk-username", "integrationuser")
                .header("x-sure-auth-aksk-roles", "admin,user")
                .header("x-sure-auth-aksk-scope", "read write"));
        // 不验证HTTP状态，因为可能是401/403/404，只要审计事件被捕获就行

        // 等待异步审计处理
        boolean received = testAuditHandler.latch.await(5, TimeUnit.SECONDS);

        // 验证：审计事件已捕获
        log.info("验证审计事件是否被捕获");
        assertTrue(received, "Audit handler should receive the event from real HTTP request");
        assertEquals(1, testAuditHandler.records.size(), "Should receive exactly one audit record");

        // 验证：审计记录内容
        AkskAuditRecord record = testAuditHandler.records.get(0);
        log.info("验证审计记录内容: {}", record);
        assertEquals("integration-client", record.getClientId());
        assertEquals("platform", record.getClientType());
        assertEquals("integration-user-123", record.getUserId());
        assertEquals("integrationuser", record.getUsername());
        assertEquals("admin,user", record.getRoles());
        assertEquals("read write", record.getScope());
        assertEquals("/test/api", record.getRequestUri());
        assertEquals("GET", record.getHttpMethod());
        assertEquals("header", record.getSource());
        assertNotNull(record.getTimestamp());

        // 验证：traceId 来自 Provider
        assertEquals("integration-trace-123", record.getTraceId());

        log.info("Integration test passed: HTTP request -> Auth -> Audit event -> Audit record captured");
    }

    @Test
    public void testMultipleRequestsWithAudit() throws Exception {
        log.info("========== 测试：多个HTTP请求的审计 ==========");

        testTraceIdProvider.setTraceId("multi-trace");

        // 发送多个请求
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/api/test/" + i)
                    .header("x-sure-auth-aksk-client-id", "client-" + i)
                    .header("x-sure-auth-aksk-client-type", "user")
                    .header("x-sure-auth-aksk-user-id", "user-" + i));
            // 不验证HTTP状态
        }

        // 等待处理
        Thread.sleep(1000);

        // 验证：收到3个审计记录
        log.info("验证是否收到3个审计记录");
        assertEquals(3, testAuditHandler.records.size(), "Should receive 3 audit records");

        // 验证：所有记录的 traceId 都来自 Provider
        for (AkskAuditRecord record : testAuditHandler.records) {
            assertEquals("multi-trace", record.getTraceId());
            assertEquals("header", record.getSource());
        }

        log.info("Multiple requests integration test passed: received {} audit records",
                testAuditHandler.records.size());
    }
}
