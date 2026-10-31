package io.github.surezzzzzz.sdk.license.server.limiter;

import io.github.surezzzzzz.sdk.license.core.spi.LimiterPolicyPort;
import io.github.surezzzzzz.sdk.license.server.limiter.configuration.SimpleLicenseLimiterPolicyAdaptorAutoConfiguration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.SmartRedisLimiterPolicyMutationClient;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.resttemplate.configuration.SmartRedisLimiterPolicyMutationClientAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 适配件引用即装配、缺少远端客户端时启动失败的容器契约。
 *
 * @author surezzzzzz
 */
class SimpleLicenseLimiterPolicyAdaptorAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleLicenseLimiterPolicyAdaptorAutoConfiguration.class));

    @Test
    void registersLicensePortWhenMutationClientExists() {
        runner.withBean(SmartRedisLimiterPolicyMutationClient.class,
                        () -> mock(SmartRedisLimiterPolicyMutationClient.class))
                .run(context -> assertThat(context).hasSingleBean(LimiterPolicyPort.class));
    }

    @Test
    void assemblesProjectTransportAndLicensePortInOneHost() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SmartRedisLimiterPolicyMutationClientAutoConfiguration.class,
                        SimpleLicenseLimiterPolicyAdaptorAutoConfiguration.class))
                .withPropertyValues(
                        "io.github.surezzzzzz.sdk.limiter.management.policy-mutation-client.enable=true",
                        "io.github.surezzzzzz.sdk.limiter.management.policy-mutation-client.base-url=http://localhost",
                        "io.github.surezzzzzz.sdk.license.limiter.customer-namespace=customer")
                .withBean("akskClientRestTemplate", RestTemplate.class, RestTemplate::new)
                .run(context -> {
                    assertThat(context).hasSingleBean(SmartRedisLimiterPolicyMutationClient.class);
                    assertThat(context).hasSingleBean(LimiterPolicyPort.class);
                });
    }

    @Test
    void failsStartupWhenMutationClientIsMissing() {
        runner.run(context -> assertThat(context).hasFailed());
    }
}
