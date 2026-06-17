package io.github.surezzzzzz.sdk.limiter.redis.smart.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.generator.SmartRedisLimiterKeyProvider;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;

/**
 * 测试用 KeyProvider：从 X-Client-Id header 提取 keyPart
 */
@Component("testHeaderKeyProvider")
public class TestHeaderKeyProvider implements SmartRedisLimiterKeyProvider {
    @Override
    public String provide(HttpServletRequest request, SmartRedisLimiterContext context) {
        String clientId = request.getHeader("X-Client-Id");
        if (clientId == null || clientId.isEmpty()) {
            return null;
        }
        return context.getMatchedPathPattern() + ":" + clientId;
    }
}
