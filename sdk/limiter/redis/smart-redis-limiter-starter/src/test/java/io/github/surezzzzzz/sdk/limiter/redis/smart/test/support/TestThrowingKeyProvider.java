package io.github.surezzzzzz.sdk.limiter.redis.smart.test.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.generator.SmartRedisLimiterKeyProvider;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * 测试用 KeyProvider：永远抛 RuntimeException（验证 fallback 处理）
 */
@Component("testThrowingKeyProvider")
public class TestThrowingKeyProvider implements SmartRedisLimiterKeyProvider {
    @Override
    public String provide(HttpServletRequest request, SmartRedisLimiterContext context) {
        throw new RuntimeException("intentional provider failure for test");
    }
}
