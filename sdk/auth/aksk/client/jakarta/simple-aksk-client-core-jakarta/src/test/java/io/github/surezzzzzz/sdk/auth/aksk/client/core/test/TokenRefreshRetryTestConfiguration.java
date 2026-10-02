package io.github.surezzzzzz.sdk.auth.aksk.client.core.test;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * Token 刷新重试测试配置：注册 {@link TestRetrySleeper} 覆盖默认的 ThreadRetrySleeper
 *
 * @author surezzzzzz
 */
@TestConfiguration
public class TokenRefreshRetryTestConfiguration {

    @Bean
    @Primary
    public TestRetrySleeper testRetrySleeper() {
        return new TestRetrySleeper();
    }
}
