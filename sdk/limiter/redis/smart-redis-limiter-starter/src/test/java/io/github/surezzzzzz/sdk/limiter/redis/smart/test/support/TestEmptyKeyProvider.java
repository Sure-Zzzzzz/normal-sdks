package io.github.surezzzzzz.sdk.limiter.redis.smart.test.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.generator.SmartRedisLimiterKeyProvider;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * 测试用 KeyProvider：永远返回空字符串（验证 fallback 到 keyStrategy）
 */
@Component("testEmptyKeyProvider")
public class TestEmptyKeyProvider implements SmartRedisLimiterKeyProvider {
    @Override
    public String provide(HttpServletRequest request, SmartRedisLimiterContext context) {
        return "";
    }
}
