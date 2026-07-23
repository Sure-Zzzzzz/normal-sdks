package io.github.surezzzzzz.sdk.limiter.redis.smart.test.cases;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.test.SmartRedisLimiterTestApplication;
import io.github.surezzzzzz.sdk.limiter.redis.smart.test.support.TestFallbackEventListener;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SmartRedisLimiter fallback 事件发布端到端测试
 *
 * <p>使用坏端口 datasource，验证生产执行器在 Redis 异常时发布事件，
 * 且 fallback=allow + logOnPass=false 时事件仍必须发布。
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = SmartRedisLimiterTestApplication.class)
@AutoConfigureMockMvc
public class SmartRedisLimiterFallbackEventTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestFallbackEventListener eventListener;

    @BeforeEach
    public void setup() {
        eventListener.reset(1);
    }

    @Test
    public void testFallbackAllowPublishesEventEvenWhenLogOnPassDisabled() throws Exception {
        log.info("=== 测试 fallback=allow + logOnPass=false 仍发布事件 ===");

        eventListener.reset(1);
        mockMvc.perform(get("/api/fallback/allow"))
                .andExpect(status().isOk());

        assertTrue(eventListener.latch.await(5, TimeUnit.SECONDS),
                "fallback=allow 时即使 logOnPass=false 也必须发布事件");
        assertEquals(1, eventListener.events.size(), "应恰好发布一个降级事件");
        SmartRedisLimiterEvent event = eventListener.events.get(0);
        log.info("fallback allow 事件: passed={}, fallbackReason={}",
                event.isPassed(), event.getFallbackReason());
        assertTrue(event.isPassed(), "fallback=allow 事件应标记 passed=true");
        assertTrue(
                SmartRedisLimiterConstant.FALLBACK_REASON_REDIS_ERROR.equals(event.getFallbackReason())
                        || SmartRedisLimiterConstant.FALLBACK_REASON_TIMEOUT.equals(event.getFallbackReason()),
                "坏端口 Redis 不可用应归类为 redis_error 或 timeout，实际: " + event.getFallbackReason());
        assertTrue(event.isRouteRequired(), "2.0.0 事件必须标记 routeRequired=true");

        log.info("=== fallback allow 事件发布测试通过 ===");
    }

    @Test
    public void testFallbackDenyPublishesEventWithPassedFalse() throws Exception {
        log.info("=== 测试 fallback=deny 发布事件且 passed=false ===");

        eventListener.reset(1);
        mockMvc.perform(post("/api/fallback/deny"))
                .andExpect(status().isTooManyRequests());

        assertTrue(eventListener.latch.await(5, TimeUnit.SECONDS),
                "fallback=deny 时必须发布事件");
        SmartRedisLimiterEvent event = eventListener.events.get(0);
        log.info("fallback deny 事件: passed={}, fallbackReason={}",
                event.isPassed(), event.getFallbackReason());
        assertFalse(event.isPassed(), "fallback=deny 事件应标记 passed=false");
        assertTrue(
                SmartRedisLimiterConstant.FALLBACK_REASON_REDIS_ERROR.equals(event.getFallbackReason())
                        || SmartRedisLimiterConstant.FALLBACK_REASON_TIMEOUT.equals(event.getFallbackReason()),
                "坏端口 Redis 不可用应归类为 redis_error 或 timeout，实际: " + event.getFallbackReason());

        log.info("=== fallback deny 事件发布测试通过 ===");
    }
}
