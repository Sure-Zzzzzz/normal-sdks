package io.github.surezzzzzz.sdk.limiter.redis.smart.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterTraceIdProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterUserProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;

/**
 * 测试用事件监听器，捕获限流事件并转换为 Record
 * 可被多个测试类共享使用
 */
@Slf4j
@Component
public class TestEventListener {

    public final List<SmartRedisLimiterRecord> records = new ArrayList<>();
    public CountDownLatch limitEventLatch = new CountDownLatch(1);

    @Autowired
    private SmartRedisLimiterUserProvider userProvider;

    @Autowired
    private SmartRedisLimiterTraceIdProvider traceIdProvider;

    @EventListener
    public void onLimitEvent(SmartRedisLimiterEvent event) {
        log.info("[TestEventListener] 收到事件: source={}, passed={}, limitKey={}",
                event.getSource(), event.isPassed(), event.getLimitKey());

        SmartRedisLimiterRecord record = SmartRedisLimiterRecord.builder()
                .clientId(userProvider.getClientId())
                .clientType(userProvider.getClientType())
                .userId(userProvider.getUserId())
                .username(userProvider.getUsername())
                .traceId(traceIdProvider.getTraceId())
                .limitKey(event.getLimitKey())
                .keyStrategy(event.getKeyStrategy())
                .algorithm(event.getAlgorithm())
                .limitRules(event.getLimitRules())
                .passed(event.isPassed())
                .source(event.getSource())
                .requestUri(event.getRequestUri())
                .httpMethod(event.getHttpMethod())
                .clientIp(event.getClientIp())
                .matchedPathPattern(event.getMatchedPathPattern())
                .methodName(event.getMethodName())
                .methodQualifiedName(event.getMethodQualifiedName())
                .timestamp(event.getTimestamp())
                .build();

        records.add(record);
        limitEventLatch.countDown();
    }

    public void reset() {
        reset(1);
    }

    public void reset(int expectedCount) {
        records.clear();
        limitEventLatch = new CountDownLatch(expectedCount);
    }

    public void clear() {
        records.clear();
    }
}
