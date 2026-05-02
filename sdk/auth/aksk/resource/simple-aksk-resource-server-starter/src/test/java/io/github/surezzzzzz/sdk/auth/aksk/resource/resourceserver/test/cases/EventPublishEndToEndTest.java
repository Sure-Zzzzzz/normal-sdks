package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.event.AkskAccessEvent;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.SimpleAkskResourceServerTestApplication;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.helper.OAuth2TokenHelper;
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
 *   <li>JWT 认证成功后发布 AkskAccessEvent</li>
 *   <li>事件包含完整的请求、上下文信息</li>
 *   <li>事件监听器能正常接收事件</li>
 * </ul>
 *
 * @author surezzzzzz
 * @since 1.0.1
 */
@Slf4j
@SpringBootTest(classes = SimpleAkskResourceServerTestApplication.class)
@AutoConfigureMockMvc
public class EventPublishEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OAuth2TokenHelper tokenHelper;

    @Autowired
    private TestEventListener testEventListener;

    private String validToken;

    @BeforeEach
    public void setUp() {
        log.info("========== 开始准备事件测试 ==========");
        validToken = tokenHelper.getToken();
        assertNotNull(validToken, "Token should not be null");
        testEventListener.reset();
        log.info("========== 事件测试准备完成 ==========");
    }

    @Test
    public void testAkskAccessEventPublish() throws Exception {
        // 准备：重置监听器
        testEventListener.reset();

        // 执行请求（触发 JWT 认证）
        mockMvc.perform(get("/test/basic")
                        .header("Authorization", "Bearer " + validToken))
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
        assertNotNull(event.getRequestUri(), "Event requestUri should not be null");
        assertNotNull(event.getHttpMethod(), "Event httpMethod should not be null");
        assertNotNull(event.getSource(), "Event source should not be null");
        assertNotNull(event.getContext(), "Event context should not be null");

        // 验证：source 类型（与当前 verificationMode 一致）
        assertNotNull(event.getSource(), "Event source should not be null");
        assertTrue(event.getSource().equals("jwt") || event.getSource().equals("introspect"),
                "Source should be 'jwt' or 'introspect', but was: " + event.getSource());

        // 验证：请求信息
        assertEquals("/test/basic", event.getRequestUri(), "Request URI should match");
        assertEquals("GET", event.getHttpMethod(), "HTTP method should match");

        // 验证：时间戳
        assertNotNull(event.getTimestamp(), "Event timestamp should not be null");
        assertTrue(event.getTimestamp() > 0, "Event timestamp should be positive");

        log.info("AkskAccessEvent test passed: clientId={}, clientType={}, uri={}, method={}, source={}",
                event.getClientId(),
                event.getClientType(),
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
                            .header("Authorization", "Bearer " + validToken))
                    .andExpect(status().isOk());
        }

        // 等待所有事件处理
        boolean allReceived = testEventListener.eventLatch.await(5, TimeUnit.SECONDS);
        assertTrue(allReceived, "Should receive all 3 events within timeout");

        // 验证：收到3个事件
        assertEquals(3, testEventListener.events.size(), "Should receive 3 events");

        // 验证：所有事件的 source 与当前 verificationMode 一致
        for (AkskAccessEvent event : testEventListener.events) {
            assertTrue(event.getSource().equals("jwt") || event.getSource().equals("introspect"),
                    "Source should be 'jwt' or 'introspect', but was: " + event.getSource());
        }

        log.info("Multiple events test passed: received {} events", testEventListener.events.size());
    }

    @Test
    public void testEventContextContent() throws Exception {
        // 准备：重置监听器
        testEventListener.reset();

        // 执行请求
        mockMvc.perform(get("/test/basic")
                        .header("Authorization", "Bearer " + validToken))
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

        log.info("Event context test passed: context size={}", event.getContext().size());
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
            log.info("Received AkskAccessEvent: clientId={}, uri={}, method={}, source={}",
                    event.getClientId(),
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
