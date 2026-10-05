package io.github.surezzzzzz.sdk.limiter.redis.smart.test.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 测试用限流事件监听器，保留原始 SmartRedisLimiterEvent 供 fallback 字段断言
 *
 * @author surezzzzzz
 */
@Slf4j
@Component
public class TestFallbackEventListener {

    public final List<SmartRedisLimiterEvent> events = new ArrayList<>();
    public CountDownLatch latch = new CountDownLatch(1);

    @EventListener
    public void onLimitEvent(SmartRedisLimiterEvent event) {
        log.info("[TestFallbackEventListener] 收到事件: passed={}, fallbackReason={}, routeKey={}",
                event.isPassed(), event.getFallbackReason(), event.getRouteKey());
        events.add(event);
        latch.countDown();
    }

    public void reset(int expectedCount) {
        events.clear();
        latch = new CountDownLatch(expectedCount);
    }
}
