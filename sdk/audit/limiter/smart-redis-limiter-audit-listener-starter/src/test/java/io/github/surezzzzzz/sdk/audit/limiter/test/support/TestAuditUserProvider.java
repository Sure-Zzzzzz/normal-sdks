package io.github.surezzzzzz.sdk.audit.limiter.test.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterUserProvider;
import org.springframework.stereotype.Component;

/**
 * 测试用的用户信息 Provider
 *
 * @author surezzzzzz
 */
@Component
public class TestAuditUserProvider implements SmartRedisLimiterUserProvider {

    @Override
    public String getClientId() {
        return "test-client";
    }

    @Override
    public String getClientType() {
        return "platform";
    }

    @Override
    public String getUserId() {
        return "user-001";
    }

    @Override
    public String getUsername() {
        return "testuser";
    }
}
