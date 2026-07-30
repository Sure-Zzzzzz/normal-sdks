package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.resolver.KafkaRouteResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerConfiguration;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerIdempotencyConfiguration;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.KafkaConsumerConfigurationException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.NoOpKafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.RedisKafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Redis 幂等自动配置测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleKafkaConsumerIdempotencyConfigurationTest {

    @Test
    public void testDisabledConsumerDoesNotCreateRedisChecker() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SimpleKafkaConsumerIdempotencyConfiguration.class))
                .withBean(SimpleRedisRouteRegistry.class, () -> mock(SimpleRedisRouteRegistry.class))
                .run(context -> {
                    log.info("消费者关闭时 Redis 幂等检查器数量：{}",
                            context.getBeansOfType(KafkaConsumerIdempotencyChecker.class).size());
                    assertFalse(context.containsBean("redisKafkaConsumerIdempotencyChecker"));
                });
    }

    @Test
    public void testIdempotencyDisabledUsesMainConfigurationNoOpChecker() {
        mainRunner()
                .withPropertyValues("io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.enable=false")
                .run(context -> {
                    KafkaConsumerIdempotencyChecker checker = context.getBean(KafkaConsumerIdempotencyChecker.class);
                    log.info("关闭 Redis 幂等时检查器类型：{}", checker.getClass().getSimpleName());
                    assertTrue(checker instanceof NoOpKafkaConsumerIdempotencyChecker);
                    assertFalse(context.containsBean("redisKafkaConsumerIdempotencyChecker"));
                });
    }

    @Test
    public void testEnabledIdempotencyCreatesRedisCheckerWhenRegistryExists() {
        mainRunner()
                .withBean(SimpleRedisRouteRegistry.class, () -> mock(SimpleRedisRouteRegistry.class))
                .withPropertyValues("io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.enable=true")
                .run(context -> {
                    KafkaConsumerIdempotencyChecker checker = context.getBean(KafkaConsumerIdempotencyChecker.class);
                    log.info("启用 Redis 幂等时检查器类型：{}", checker.getClass().getSimpleName());
                    assertTrue(checker instanceof RedisKafkaConsumerIdempotencyChecker);
                });
    }

    @Test
    public void testEnabledIdempotencyFailsWithoutRedisRegistry() {
        mainRunner()
                .withPropertyValues("io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.enable=true")
                .run(context -> {
                    Throwable failure = context.getStartupFailure();
                    log.info("缺少 Redis registry 的启动错误：{}", failure == null ? null : failure.getMessage());
                    assertNotNull(failure);
                    KafkaConsumerConfigurationException exception = findConfigurationException(failure);
                    assertNotNull(exception);
                    assertEquals(ErrorCode.CONFIG_INVALID, exception.getErrorCode());
                });
    }

    @Test
    public void testCustomCheckerOverridesRedisAndNoOpDefaults() {
        mainRunner()
                .withUserConfiguration(CustomCheckerConfiguration.class)
                .withBean(SimpleRedisRouteRegistry.class, () -> mock(SimpleRedisRouteRegistry.class))
                .withPropertyValues("io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.enable=true")
                .run(context -> {
                    KafkaConsumerIdempotencyChecker checker = context.getBean(KafkaConsumerIdempotencyChecker.class);
                    log.info("自定义幂等检查器类型：{}", checker.getClass().getSimpleName());
                    assertSame(context.getBean("customChecker"), checker);
                });
    }

    private KafkaConsumerConfigurationException findConfigurationException(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof KafkaConsumerConfigurationException) {
                return (KafkaConsumerConfigurationException) current;
            }
            current = current.getCause();
        }
        return null;
    }

    private ApplicationContextRunner mainRunner() {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SimpleKafkaConsumerConfiguration.class,
                        SimpleKafkaConsumerIdempotencyConfiguration.class))
                .withBean(SimpleKafkaRouteRegistry.class, () -> mock(SimpleKafkaRouteRegistry.class))
                .withBean(KafkaRouteResolver.class, () -> mock(KafkaRouteResolver.class))
                .withBean(SimpleKafkaRouteProperties.class, SimpleKafkaRouteProperties::new)
                .withPropertyValues("io.github.surezzzzzz.sdk.messaging.kafka.consumer.enable=true");
    }

    @Configuration
    static class CustomCheckerConfiguration {

        @Bean
        public KafkaConsumerIdempotencyChecker customChecker() {
            return new NoOpKafkaConsumerIdempotencyChecker();
        }
    }
}
