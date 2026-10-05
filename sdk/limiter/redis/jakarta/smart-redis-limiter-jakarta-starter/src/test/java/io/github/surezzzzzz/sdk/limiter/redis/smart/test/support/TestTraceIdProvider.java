package io.github.surezzzzzz.sdk.limiter.redis.smart.test.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterTraceIdProvider;
import org.springframework.stereotype.Component;

/**
 * 测试用 TraceIdProvider：返回固定 traceId
 */
@Component
public class TestTraceIdProvider implements SmartRedisLimiterTraceIdProvider {
    @Override
    public String getTraceId() {
        return "test-trace-id-12345";
    }
}
