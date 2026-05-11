package io.github.surezzzzzz.sdk.limiter.redis.smart.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 测试用事件监听器，直接捕获 SmartRedisLimiterEvent 原始事件
 * 可被多个测试类共享使用
 */
@Slf4j
@Component
public class TestAuditEventListener {

    private final List<SmartRedisLimiterEvent> events = new CopyOnWriteArrayList<>();

    @EventListener
    public void onLimitEvent(SmartRedisLimiterEvent event) {
        log.info("[AuditTestListener] 捕获事件: source={}, passed={}, limitKey={}",
                event.getSource(), event.isPassed(), event.getLimitKey());
        events.add(event);
    }

    public List<SmartRedisLimiterEvent> getEvents() {
        return events;
    }

    public void clear() {
        events.clear();
    }

    public SmartRedisLimiterEvent findFirst() {
        return events.isEmpty() ? null : events.get(0);
    }

    public SmartRedisLimiterEvent findFirstBySource(String source) {
        return events.stream()
                .filter(e -> source.equals(e.getSource()))
                .findFirst()
                .orElse(null);
    }
}
