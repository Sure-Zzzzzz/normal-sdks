package io.github.surezzzzzz.sdk.audit.aksk.test.cases;

import io.github.surezzzzzz.sdk.audit.aksk.model.AkskAuditRecord;
import io.github.surezzzzzz.sdk.audit.aksk.test.AkskAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.aksk.test.TestAkskAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.test.TestTraceIdProvider;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.event.AkskAccessEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AKSK 审计监听器端到端测试
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@SpringBootTest(
        classes = AkskAuditListenerTestApplication.class,
        properties = {
                "io.github.surezzzzzz.sdk.auth.aksk.resource.security-context.enable=false",  // 禁用Header认证
                "io.github.surezzzzzz.sdk.auth.aksk.resource.server.enabled=false"  // 禁用JWT认证
        }
)
public class AkskAuditListenerEndToEndTest {

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TestAkskAuditHandler testAuditHandler;

    @Autowired
    private TestTraceIdProvider testTraceIdProvider;

    @BeforeEach
    public void setUp() {
        log.info("========== 开始准备 AKSK 审计测试 ==========");
        testAuditHandler.reset();
        testTraceIdProvider.reset();
        log.info("========== AKSK 审计测试准备完成 ==========");
    }

    @Test
    public void testAkskAuditEventHandling() throws InterruptedException {
        log.info("========== 测试：AKSK 审计事件处理 ==========");

        // 准备：设置 traceId
        testTraceIdProvider.setTraceId("test-trace-123");

        // 构建事件
        Map<String, String> context = new HashMap<String, String>();
        context.put("clientId", "test-client");
        context.put("userId", "user-123");

        AkskAccessEvent event = new AkskAccessEvent(
                this,
                "test-client",
                "platform",
                "user-123",
                "testuser",
                "admin",
                "read write",
                "/api/test",
                "GET",
                "127.0.0.1",
                "Mozilla/5.0",
                "header",
                "old-trace-id",  // 事件里的 traceId 应该被忽略
                context
        );

        // 发布事件
        log.info("发布 AKSK 审计事件: clientId={}, userId={}", "test-client", "user-123");
        eventPublisher.publishEvent(event);

        // 等待异步处理
        boolean received = testAuditHandler.latch.await(5, TimeUnit.SECONDS);

        // 验证：事件已处理
        log.info("验证审计事件是否被处理");
        assertTrue(received, "Audit handler should receive the event");
        assertEquals(1, testAuditHandler.records.size(), "Should receive exactly one record");

        // 验证：审计记录内容
        AkskAuditRecord record = testAuditHandler.records.get(0);
        log.info("验证审计记录内容: {}", record);
        assertEquals("test-client", record.getClientId());
        assertEquals("platform", record.getClientType());
        assertEquals("user-123", record.getUserId());
        assertEquals("testuser", record.getUsername());
        assertEquals("admin", record.getRoles());
        assertEquals("read write", record.getScope());
        assertEquals("/api/test", record.getRequestUri());
        assertEquals("GET", record.getHttpMethod());
        assertEquals("127.0.0.1", record.getRemoteAddr());
        assertEquals("Mozilla/5.0", record.getUserAgent());
        assertEquals("header", record.getSource());
        assertNotNull(record.getTimestamp());
        assertNotNull(record.getContext());

        // 验证：traceId 来自 Provider，不是事件里的
        assertEquals("test-trace-123", record.getTraceId(), "TraceId should come from provider");

        log.info("AKSK audit test passed: clientId={}, userId={}, traceId={}",
                record.getClientId(), record.getUserId(), record.getTraceId());
    }

    @Test
    public void testMultipleEvents() throws InterruptedException {
        log.info("========== 测试：多个审计事件处理 ==========");

        // 准备
        testTraceIdProvider.setTraceId("trace-multi");

        // 发布多个事件
        for (int i = 0; i < 3; i++) {
            Map<String, String> context = new HashMap<String, String>();
            AkskAccessEvent event = new AkskAccessEvent(
                    this,
                    "client-" + i,
                    "user",
                    "user-" + i,
                    "user" + i,
                    null,
                    null,
                    "/api/test/" + i,
                    "POST",
                    "127.0.0.1",
                    null,
                    "jwt",
                    null,
                    context
            );
            log.info("发布第 {} 个事件: clientId={}", i + 1, "client-" + i);
            eventPublisher.publishEvent(event);
        }

        // 等待处理
        Thread.sleep(1000);

        // 验证：收到3个记录
        log.info("验证是否收到3个审计记录");
        assertEquals(3, testAuditHandler.records.size(), "Should receive 3 records");

        // 验证：所有记录的 traceId 都来自 Provider
        for (AkskAuditRecord record : testAuditHandler.records) {
            assertEquals("trace-multi", record.getTraceId());
        }

        log.info("Multiple events test passed: received {} records", testAuditHandler.records.size());
    }

    @Test
    public void testWithoutTraceIdProvider() throws InterruptedException {
        log.info("========== 测试：无 TraceId Provider 场景 ==========");

        // 准备：清空 traceId
        testTraceIdProvider.setTraceId(null);

        // 发布事件
        Map<String, String> context = new HashMap<String, String>();
        AkskAccessEvent event = new AkskAccessEvent(
                this,
                "test-client",
                "platform",
                null,
                null,
                null,
                null,
                "/api/test",
                "GET",
                "127.0.0.1",
                null,
                "header",
                "should-be-ignored",
                context
        );
        log.info("发布事件（Provider返回null）");
        eventPublisher.publishEvent(event);

        // 等待处理
        boolean received = testAuditHandler.latch.await(5, TimeUnit.SECONDS);
        assertTrue(received);

        // 验证：traceId 为 null
        AkskAuditRecord record = testAuditHandler.records.get(0);
        log.info("验证 traceId 为 null");
        assertNull(record.getTraceId(), "TraceId should be null when provider returns null");

        log.info("No traceId test passed");
    }
}
