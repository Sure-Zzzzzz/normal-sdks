package io.github.surezzzzzz.sdk.auth.aksk.redis.tokenmanager.test;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.provider.SecurityContextProvider;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 为需要验证非空安全上下文的测试提供固定上下文。
 *
 * <p>未导入本配置的测试由 Starter 的默认实现提供空上下文，覆盖默认缓存键和 Pub/Sub 场景。
 *
 * @author surezzzzzz
 */
@TestConfiguration(proxyBeanMethods = false)
public class NonNullSecurityContextTestConfiguration {

    @Bean
    public SecurityContextProvider securityContextProvider() {
        return () -> SimpleAkskRedisTokenManagerTestApplication.TEST_SECURITY_CONTEXT;
    }
}
