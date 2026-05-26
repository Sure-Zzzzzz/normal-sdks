package io.github.surezzzzzz.sdk.audit.aksk.test.cases;

import io.github.surezzzzzz.sdk.audit.aksk.resource.model.AkskAuditRecord;
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
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * AKSK INTROSPECT 认证审计集成测试
 *
 * <p>测试真实的HTTP请求 -> INTROSPECT认证 -> 审计事件发布 -> 审计记录捕获的完整链路
 *
 * <p>前置条件：aksk-server 启动（端口 8080），application-local.yml 中配置 introspect.client-id
 *
 * @author surezzzzzz
 * @since 2.0.0
 */
@Slf4j
@SpringBootTest(
        classes = AkskAuditListenerTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
@AutoConfigureMockMvc
@ActiveProfiles("local")
public class AkskAuditIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestAkskAuditHandler testAuditHandler;

    @Autowired
    private TestTraceIdProvider testTraceIdProvider;

    @Autowired
    private OAuth2TokenHelper tokenHelper;

    @Autowired
    private io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration.SimpleAkskResourceServerProperties properties;

    private String validToken;

    @BeforeEach
    public void setUp() {
        log.info("========== 开始准备 AKSK INTROSPECT 审计集成测试 ==========");
        assumeTrue(StringUtils.hasText(properties.getIntrospect().getClientId()),
                "未配置 introspect.client-id，跳过集成测试");

        testAuditHandler.reset();
        testTraceIdProvider.reset();

        validToken = tokenHelper.getToken();
        assertNotNull(validToken, "Token should not be null");
        log.info("========== AKSK INTROSPECT 审计集成测试准备完成 ==========");
    }

    @Test
    public void testIntrospectAuthenticationWithAudit() throws Exception {
        log.info("========== 测试：INTROSPECT认证 + 审计 ==========");

        testTraceIdProvider.setTraceId("introspect-trace-123");

        mockMvc.perform(get("/test/api")
                        .header("Authorization", "Bearer " + validToken))
                .andReturn();

        boolean received = testAuditHandler.latch.await(5, TimeUnit.SECONDS);

        assertTrue(received, "Audit handler should receive the event from INTROSPECT authentication");
        assertEquals(1, testAuditHandler.records.size(), "Should receive exactly one audit record");

        AkskAuditRecord record = testAuditHandler.records.get(0);
        log.info("验证审计记录内容: {}", record);
        assertNotNull(record.getClientId());
        assertNotNull(record.getClientType());
        assertEquals("/test/api", record.getRequestUri());
        assertEquals("GET", record.getHttpMethod());
        assertEquals("introspect", record.getSource());
        assertNotNull(record.getTimestamp());
        assertEquals("introspect-trace-123", record.getTraceId());

        log.info("INTROSPECT integration test passed");
    }

    @Test
    public void testMultipleRequestsWithAudit() throws Exception {
        log.info("========== 测试：INTROSPECT认证多个请求的审计 ==========");

        testTraceIdProvider.setTraceId("introspect-multi-trace");

        for (int i = 0; i < 2; i++) {
            mockMvc.perform(get("/test/api")
                    .header("Authorization", "Bearer " + validToken));
        }

        Thread.sleep(1000);

        assertEquals(2, testAuditHandler.records.size(), "Should receive 2 audit records");

        for (AkskAuditRecord record : testAuditHandler.records) {
            assertEquals("introspect", record.getSource());
            assertEquals("introspect-multi-trace", record.getTraceId());
        }

        log.info("Multiple requests test passed: received {} audit records", testAuditHandler.records.size());
    }
}
