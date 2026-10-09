package io.github.surezzzzzz.sdk.iam.feign.client.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.manager.TokenManager;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.configuration.SimpleAkskFeignRedisClientAutoConfiguration;
import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.interceptor.AkskFeignRequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * IAM Feign 模块装配测试：底座拦截器条件链（本模块为契约接口层，无自有装配面）。
 *
 * @author surezzzzzz
 */
@Slf4j
class IamFeignAssemblyTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(SimpleAkskFeignRedisClientAutoConfiguration.class)
            .withPropertyValues(
                    "io.github.surezzzzzz.sdk.auth.aksk.client.enable=true",
                    "io.github.surezzzzzz.sdk.iam.client.base-url=https://iam.example.internal");

    private static TokenManager stubTokenManager() {
        return new TokenManager() {
            @Override
            public String getToken() {
                return "test-token";
            }

            @Override
            public void clearToken() {
                // 桩实现无缓存可清
            }
        };
    }

    @Test
    void shouldRegisterBaseInterceptorWhenTokenManagerPresent() {
        contextRunner.withBean(TokenManager.class, () -> stubTokenManager())
                .run(context -> assertEquals(1,
                        context.getBeanNamesForType(AkskFeignRequestInterceptor.class).length,
                        "TokenManager 在场时底座注册 Feign 认证拦截器"));
    }

    @Test
    void shouldStaySilentWithoutTokenManager() {
        contextRunner.run(context -> assertEquals(0,
                context.getBeanNamesForType(AkskFeignRequestInterceptor.class).length,
                "TokenManager 不在场时底座不装配"));
    }
}
