package io.github.surezzzzzz.sdk.limiter.redis.smart.test.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.generator.SmartRedisLimiterKeyProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * 测试用 KeyProvider：永远返回 null（验证回退到 keyStrategy）
 */
@Component("testNullingKeyProvider")
public class TestNullingKeyProvider implements SmartRedisLimiterKeyProvider {
    @Override
    public String provide(HttpServletRequest request, SmartRedisLimiterContext context) {
        return null;
    }
}
