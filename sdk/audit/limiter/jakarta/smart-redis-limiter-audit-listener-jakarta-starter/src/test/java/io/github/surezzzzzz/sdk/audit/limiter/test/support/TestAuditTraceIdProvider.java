package io.github.surezzzzzz.sdk.audit.limiter.test.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterTraceIdProvider;
import org.springframework.stereotype.Component;

/**
 * 测试用的 TraceId Provider
 *
 * @author surezzzzzz
 */
@Component
public class TestAuditTraceIdProvider implements SmartRedisLimiterTraceIdProvider {

    @Override
    public String getTraceId() {
        return "trace-test-001";
    }
}
