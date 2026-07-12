package io.github.surezzzzzz.sdk.lock.redis.cases;

import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.lock.redis.configuration.LockConfiguration;
import io.github.surezzzzzz.sdk.lock.redis.configuration.RedisLockRouteConfiguration;
import io.github.surezzzzzz.sdk.lock.redis.configuration.RedisLockRouteMissingConfiguration;
import io.github.surezzzzzz.sdk.lock.redis.executor.DefaultRedisLockExecutor;
import io.github.surezzzzzz.sdk.lock.redis.executor.RedisLockExecutor;
import io.github.surezzzzzz.sdk.lock.redis.executor.RouteRedisLockExecutor;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis 分布式锁自动配置边界测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleRedisLockAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    LockConfiguration.class,
                    RedisLockRouteConfiguration.class,
                    RedisLockRouteMissingConfiguration.class
            ));

    @Test
    public void testRouteDisabledUsesDefaultExecutor() {
        contextRunner.withUserConfiguration(DefaultRedisConfiguration.class)
                .run(context -> {
                    log.info("验证默认模式自动配置 Bean 列表: {}", (Object) context.getBeanDefinitionNames());
                    assertTrue(context.containsBean("simpleRedisLockRedisTemplate"), "默认模式必须注册 simpleRedisLockRedisTemplate");
                    assertTrue(context.getBean(RedisLockExecutor.class) instanceof DefaultRedisLockExecutor,
                            "默认模式必须注册 DefaultRedisLockExecutor");
                    assertNotNull(context.getBean(SimpleRedisLock.class), "默认模式必须注册 SimpleRedisLock");
                });
    }

    @Test
    public void testRouteEnabledUsesRouteExecutor() {
        contextRunner.withUserConfiguration(RouteTemplateConfiguration.class)
                .withPropertyValues("io.github.surezzzzzz.sdk.lock.redis.route.enable=true")
                .run(context -> {
                    log.info("验证 route 模式自动配置 Bean 列表: {}", (Object) context.getBeanDefinitionNames());
                    assertFalse(context.containsBean("simpleRedisLockRedisTemplate"), "route 模式不应注册 simpleRedisLockRedisTemplate");
                    assertTrue(context.getBean(RedisLockExecutor.class) instanceof RouteRedisLockExecutor,
                            "route 模式必须注册 RouteRedisLockExecutor");
                    assertNotNull(context.getBean(SimpleRedisLock.class), "route 模式必须注册 SimpleRedisLock");
                });
    }

    @Test
    public void testRouteEnabledWithoutRedisRouteTemplateFailsFast() {
        contextRunner.withPropertyValues("io.github.surezzzzzz.sdk.lock.redis.route.enable=true")
                .run(context -> {
                    log.info("验证 route 模式缺少 RedisRouteTemplate 时启动失败，failure={}", context.getStartupFailure());
                    assertNotNull(context.getStartupFailure(), "route.enable=true 但 RedisRouteTemplate 不存在时必须启动失败");
                    assertTrue(String.valueOf(context.getStartupFailure().getMessage()).contains("RedisRouteTemplate"),
                            "失败信息必须明确提示缺少 RedisRouteTemplate");
                });
    }

    @Test
    public void testCustomRedisLockExecutorBacksOffDefaults() {
        contextRunner.withUserConfiguration(DefaultRedisConfiguration.class, CustomExecutorConfiguration.class)
                .run(context -> {
                    log.info("验证业务自定义 RedisLockExecutor 时默认 executor 退让");
                    RedisLockExecutor executor = context.getBean(RedisLockExecutor.class);
                    assertSame(CustomExecutorConfiguration.CUSTOM_EXECUTOR, executor, "业务自定义 RedisLockExecutor 必须生效");
                    assertTrue(context.containsBean("simpleRedisLockRedisTemplate"), "默认模式下仍可注册 simpleRedisLockRedisTemplate");
                    assertNotNull(context.getBean(SimpleRedisLock.class), "自定义 executor 模式必须注册 SimpleRedisLock");
                });
    }

    @Test
    public void testRouteEnabledWithCustomExecutorDoesNotFailWithoutRouteTemplate() {
        contextRunner.withUserConfiguration(CustomExecutorConfiguration.class)
                .withPropertyValues("io.github.surezzzzzz.sdk.lock.redis.route.enable=true")
                .run(context -> {
                    log.info("验证 route.enable=true 且业务自定义 executor 时不强行失败");
                    assertNull(context.getStartupFailure(), "业务自定义 RedisLockExecutor 时 SDK 不应强行失败");
                    assertSame(CustomExecutorConfiguration.CUSTOM_EXECUTOR, context.getBean(RedisLockExecutor.class),
                            "业务自定义 RedisLockExecutor 必须优先生效");
                    assertFalse(context.containsBean("simpleRedisLockRedisTemplate"), "route.enable=true 时不应注册 simpleRedisLockRedisTemplate");
                });
    }

    @Configuration
    static class DefaultRedisConfiguration {
        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return new LettuceConnectionFactory("localhost", 6379);
        }
    }

    @Configuration
    static class RouteTemplateConfiguration {
        @Bean
        RedisRouteTemplate redisRouteTemplate() {
            return new RedisRouteTemplate(null, null);
        }
    }

    @Configuration
    static class CustomExecutorConfiguration {
        static final RedisLockExecutor CUSTOM_EXECUTOR = new RedisLockExecutor() {
            @Override
            public boolean tryLock(String lockKey, String lockValue, long expireTime, TimeUnit timeUnit) {
                return true;
            }

            @Override
            public boolean unlock(String lockKey, String lockValue) {
                return true;
            }
        };

        @Bean
        RedisLockExecutor customRedisLockExecutor() {
            return CUSTOM_EXECUTOR;
        }
    }
}
