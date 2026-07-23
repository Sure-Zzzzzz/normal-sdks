package io.github.surezzzzzz.sdk.limiter.redis.smart.test.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterUserProvider;
import org.springframework.stereotype.Component;

/**
 * 测试用 UserProvider：返回固定用户信息
 */
@Component
public class TestUserProvider implements SmartRedisLimiterUserProvider {
    @Override
    public String getClientId() {
        return "test-client-id";
    }

    @Override
    public String getClientType() {
        return "test-client-type";
    }

    @Override
    public String getUserId() {
        return "test-user-id";
    }

    @Override
    public String getUsername() {
        return "test-username";
    }
}
