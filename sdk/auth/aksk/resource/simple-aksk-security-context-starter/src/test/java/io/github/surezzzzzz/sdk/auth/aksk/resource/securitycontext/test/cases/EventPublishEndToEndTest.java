package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.event.AkskAccessEvent;
import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.constant.SimpleAkskSecurityContextConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.test.SimpleAkskSecurityContextTestApplication;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 事件发布端到端测试
 *
 * <p>测试场景：
 * <ul>
 *   <li>Header 认证成功后发布 AkskAccessEvent</li>
 *   <li>事件包含完整的请求、上下文信息</li>
 *   <li>事件监听器能正常接收事件</li>
 * </ul>
 *
 * @author surezzzzzz
 * @since 1.0.2
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskSecurityContextTestApplication.class)
@AutoConfigureMockMvc
public class EventPublishEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestEventListener testEventListener;

    @BeforeEach
    public void setUp() {
        log.info("========== 开始准备事件测试 ==========");
        testEventListener.reset();
        log.info("========== 事件测试准备完成 ==========");
    }

    @Test
    public void testAkskAccessEventPublish() throws Exception {
        // 准备：重置监听器
        testEventListener.reset();

        // 执行请求（带 Header）
        mockMvc.perform(get("/test/basic")
                        .header("x-sure-auth-aksk-client-id", "test-client-123")
                        .header("x-sure-auth-aksk-client-type", "platform")
                        .header("x-sure-auth-aksk-user-id", "user-456")
                        .header("x-sure-auth-aksk-username", "testuser"))
                .andExpect(status().isOk());

        // 等待事件处理（异步）
        boolean received = testEventListener.eventLatch.await(5, TimeUnit.SECONDS);

        // 验证：事件已发布并被监听到
        assertTrue(received, "AkskAccessEvent should be received");
        assertEquals(1, testEventListener.events.size(), "Should receive exactly one event");

        // 验证：事件内容
        AkskAccessEvent event = testEventListener.events.get(0);
        assertNotNull(event, "Event should not be null");
        assertNotNull(event.getClientId(), "Event clientId should not be null");
        assertNotNull(event.getClientType(), "Event clientType should not be null");
        assertNotNull(event.getUserId(), "Event userId should not be null");
        assertNotNull(event.getUsername(), "Event username should not be null");
        assertNotNull(event.getRequestUri(), "Event requestUri should not be null");
        assertNotNull(event.getHttpMethod(), "Event httpMethod should not be null");
        assertNotNull(event.getSource(), "Event source should not be null");
        assertNotNull(event.getContext(), "Event context should not be null");

        // 验证：source 类型
        assertEquals(SimpleAkskResourceConstant.ACCESS_SOURCE_HEADER, event.getSource(), "Source should be 'header'");

        // 验证：请求信息
        assertEquals("/test/basic", event.getRequestUri(), "Request URI should match");
        assertEquals("GET", event.getHttpMethod(), "HTTP method should match");

        // 验证：客户端信息
        assertEquals("test-client-123", event.getClientId(), "Client ID should match");
        assertEquals("platform", event.getClientType(), "Client type should match");
        assertEquals("user-456", event.getUserId(), "User ID should match");
        assertEquals("testuser", event.getUsername(), "Username should match");

        // 验证：时间戳
        assertNotNull(event.getTimestamp(), "Event timestamp should not be null");
        assertTrue(event.getTimestamp() > 0, "Event timestamp should be positive");

        log.info("AkskAccessEvent test passed: clientId={}, clientType={}, userId={}, username={}, uri={}, method={}, source={}",
                event.getClientId(),
                event.getClientType(),
                event.getUserId(),
                event.getUsername(),
                event.getRequestUri(),
                event.getHttpMethod(),
                event.getSource());
    }

    @Test
    public void testMultipleEventsPublish() throws Exception {
        // 准备：重置监听器（期望3个事件）
        testEventListener.reset(3);

        // 执行多次请求
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(get("/test/basic")
                            .header("x-sure-auth-aksk-client-id", "test-client-" + i)
                            .header("x-sure-auth-aksk-user-id", "user-" + i))
                    .andExpect(status().isOk());
        }

        // 等待所有事件处理
        boolean allReceived = testEventListener.eventLatch.await(5, TimeUnit.SECONDS);
        assertTrue(allReceived, "Should receive all 3 events within timeout");

        // 验证：收到3个事件
        assertEquals(3, testEventListener.events.size(), "Should receive 3 events");

        // 验证：所有事件的 source 都是 "header"
        for (AkskAccessEvent event : testEventListener.events) {
            assertEquals(SimpleAkskResourceConstant.ACCESS_SOURCE_HEADER, event.getSource(), "All events should have source='header'");
        }

        log.info("Multiple events test passed: received {} events", testEventListener.events.size());
    }

    @Test
    public void testEventContextContent() throws Exception {
        // 准备：重置监听器
        testEventListener.reset();

        // 执行请求
        mockMvc.perform(get("/test/basic")
                        .header("x-sure-auth-aksk-client-id", "test-client-123")
                        .header("x-sure-auth-aksk-trace-id", "trace-xyz"))
                .andExpect(status().isOk());

        // 等待事件处理
        boolean received = testEventListener.eventLatch.await(5, TimeUnit.SECONDS);
        assertTrue(received, "Event should be received");

        // 验证：context 包含必要字段
        AkskAccessEvent event = testEventListener.events.get(0);
        assertNotNull(event.getContext(), "Context should not be null");
        assertTrue(event.getContext().size() > 0, "Context should not be empty");

        // 验证：context 包含 clientId
        assertTrue(event.getContext().containsKey("clientId"), "Context should contain clientId");
        assertEquals(event.getClientId(), event.getContext().get("clientId"), "Context clientId should match event clientId");

        // 验证：traceId
        assertEquals("trace-xyz", event.getTraceId(), "TraceId should match");
        assertEquals("trace-xyz", event.getContext().get("traceId"), "Context traceId should match");

        log.info("Event context test passed: context size={}, traceId={}", event.getContext().size(), event.getTraceId());
    }

    @Test
    public void testEventWithoutHeaders() throws Exception {
        // 准备：重置监听器
        testEventListener.reset();

        // 执行请求（不带任何 AKSK Header）
        mockMvc.perform(get("/test/basic"))
                .andExpect(status().isOk());

        // 等待一段时间
        boolean received = testEventListener.eventLatch.await(1, TimeUnit.SECONDS);

        // 验证：没有事件发布（因为没有提取到上下文）
        assertFalse(received, "Should not receive event when no headers present");
        assertEquals(0, testEventListener.events.size(), "Should not receive any events");

        log.info("No event test passed: no events received when headers are absent");
    }

    /**
     * 测试事件监听器
     */
    @Component
    @Slf4j
    public static class TestEventListener {

        public final List<AkskAccessEvent> events = new CopyOnWriteArrayList<AkskAccessEvent>();
        public CountDownLatch eventLatch = new CountDownLatch(1);

        @EventListener
        public void onAkskAccessEvent(AkskAccessEvent event) {
            log.info("Received AkskAccessEvent: clientId={}, userId={}, uri={}, method={}, source={}",
                    event.getClientId(),
                    event.getUserId(),
                    event.getRequestUri(),
                    event.getHttpMethod(),
                    event.getSource());

            events.add(event);
            eventLatch.countDown();
        }

        public void reset() {
            reset(1);
        }

        public void reset(int count) {
            events.clear();
            eventLatch = new CountDownLatch(count);
        }
    }
}
