package io.github.surezzzzzz.sdk.redis.route.test.cases;

import io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteConfiguration;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.redis.route.factory.RedisConnectionFactoryFactory;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import io.github.surezzzzzz.sdk.redis.route.test.factory.MockRedisConnectionFactory;
import io.github.surezzzzzz.sdk.redis.route.test.factory.MockRedisConnectionFactoryFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Redis route 自动配置测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleRedisRouteAutoConfigurationTest {

    private static final String[] ROUTE_PROPERTIES = {
            "io.github.surezzzzzz.sdk.redis.route.enable=true",
            "io.github.surezzzzzz.sdk.redis.route.default-source=default",
            "io.github.surezzzzzz.sdk.redis.route.probe.server-info=false",
            "io.github.surezzzzzz.sdk.redis.route.sources.default.host=localhost",
            "io.github.surezzzzzz.sdk.redis.route.sources.default.port=6379",
            "io.github.surezzzzzz.sdk.redis.route.sources.default.database=0",
            "io.github.surezzzzzz.sdk.redis.route.sources.default.timeout-ms=3000",
            "io.github.surezzzzzz.sdk.redis.route.sources.default.connect-timeout-ms=3000",
            "io.github.surezzzzzz.sdk.redis.route.sources.cache.host=localhost",
            "io.github.surezzzzzz.sdk.redis.route.sources.cache.port=6379",
            "io.github.surezzzzzz.sdk.redis.route.sources.cache.database=1",
            "io.github.surezzzzzz.sdk.redis.route.sources.cache.timeout-ms=3000",
            "io.github.surezzzzzz.sdk.redis.route.sources.cache.connect-timeout-ms=3000",
            "io.github.surezzzzzz.sdk.redis.route.rules[0].pattern=cache:",
            "io.github.surezzzzzz.sdk.redis.route.rules[0].type=prefix",
            "io.github.surezzzzzz.sdk.redis.route.rules[0].datasource=cache",
            "io.github.surezzzzzz.sdk.redis.route.rules[0].priority=1"
    };

    private final ApplicationContextRunner routeContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SimpleRedisRouteConfiguration.class,
                    RedisAutoConfiguration.class))
            .withUserConfiguration(MockRedisFactoryConfiguration.class)
            .withPropertyValues(ROUTE_PROPERTIES);

    @Test
    public void testRouteEnabledPublishesDefaultSourceAsStandardRedisBeans() {
        routeContextRunner.run(context -> {
            assertNull(context.getStartupFailure(), "Route 接管默认 Bean 时应用上下文应正常启动");
            SimpleRedisRouteRegistry registry = context.getBean(SimpleRedisRouteRegistry.class);
            RedisRouteTemplate routeTemplate = context.getBean(RedisRouteTemplate.class);
            RedisConnectionFactory connectionFactory = context.getBean("redisConnectionFactory", RedisConnectionFactory.class);
            StringRedisTemplate stringRedisTemplate = context.getBean("stringRedisTemplate", StringRedisTemplate.class);
            RedisTemplate<?, ?> redisTemplate = context.getBean("redisTemplate", RedisTemplate.class);

            log.info("验证 Route default-source 标准 Bean 接管，datasourceKeys={}", registry.getDatasourceKeys());
            assertSame(registry.getConnectionFactory(), connectionFactory,
                    "标准 RedisConnectionFactory 必须复用 Route default-source factory");
            assertSame(registry.getStringRedisTemplate(), stringRedisTemplate,
                    "标准 StringRedisTemplate 必须复用 Route default-source template");
            assertSame(routeTemplate.connectionFactory(), connectionFactory,
                    "Route 默认 factory 与标准 factory 必须为同一实例");
            assertSame(routeTemplate.stringTemplate(), stringRedisTemplate,
                    "Route 默认 template 与标准 StringRedisTemplate 必须为同一实例");
            assertSame(connectionFactory, redisTemplate.getConnectionFactory(),
                    "标准 RedisTemplate 必须绑定 Route default-source factory");
            assertSame(redisTemplate, context.getBean(RedisTemplate.class),
                    "按原始 RedisTemplate 类型注入必须唯一命中标准 RedisTemplate");
            assertTrue(context.getBeanFactory().getBeanDefinition("redisConnectionFactory").isPrimary(),
                    "Route RedisConnectionFactory 必须为 Primary");
            assertFalse(context.getBeanFactory().getBeanDefinition("stringRedisTemplate").isPrimary(),
                    "StringRedisTemplate 不能与标准 RedisTemplate 同时作为 Primary");
            assertTrue(context.getBeanFactory().getBeanDefinition("redisTemplate").isPrimary(),
                    "Route RedisTemplate 必须为 Primary");
            assertEquals(1, context.getBeansOfType(RedisConnectionFactory.class).size(),
                    "Boot 不得创建第二个 RedisConnectionFactory");
            assertEquals(1, context.getBeansOfType(StringRedisTemplate.class).size(),
                    "Boot 不得创建第二个 StringRedisTemplate");
            assertEquals(2, context.getBeansOfType(RedisTemplate.class).size(),
                    "上下文只能包含标准 RedisTemplate 与 StringRedisTemplate");
            assertSame(registry.getStringRedisTemplate("cache"),
                    routeTemplate.stringTemplateByKey("cache:configuration:001"),
                    "非默认 datasource 仍必须通过 Route 显式访问");
            assertNotSame(stringRedisTemplate, registry.getStringRedisTemplate("cache"),
                    "标准 StringRedisTemplate 不能伪装为按 key 自动路由模板");
        });
    }

    @Test
    public void testStandardConnectionFactoryIsOnlyDestroyedByRegistry() {
        AtomicReference<MockRedisConnectionFactory> factoryReference = new AtomicReference<>();

        routeContextRunner.run(context -> factoryReference.set(context.getBean("redisConnectionFactory",
                MockRedisConnectionFactory.class)));

        assertNotNull(factoryReference.get(), "应获取 Route default-source 测试连接工厂");
        assertEquals(1, factoryReference.get().getDestroyCount(),
                "标准 redisConnectionFactory 只能由 Route 注册表销毁一次");
    }

    @Test
    public void testRouteDisabledDoesNotPublishRouteOrStandardRedisBeans() {
        new ApplicationContextRunner()
                .withUserConfiguration(SimpleRedisRouteConfiguration.class)
                .withPropertyValues("io.github.surezzzzzz.sdk.redis.route.enable=false")
                .run(context -> {
                    log.info("验证 Redis Route 禁用时不创建 Route 或标准 Redis Bean");
                    assertNull(context.getStartupFailure(), "Route 禁用时上下文应正常启动");
                    assertFalse(context.containsBean("simpleRedisRouteRegistry"));
                    assertFalse(context.containsBean("redisRouteTemplate"));
                    assertFalse(context.containsBean("redisConnectionFactory"));
                    assertFalse(context.containsBean("stringRedisTemplate"));
                    assertFalse(context.containsBean("redisTemplate"));
                });
    }

    @Test
    public void testHostConnectionFactoryFailsFastWhenRouteEnabled() {
        assertHostStandardRedisBeanConflict(HostRedisConnectionFactoryConfiguration.class);
    }

    @Test
    public void testHostStringRedisTemplateFailsFastWhenRouteEnabled() {
        assertHostStandardRedisBeanConflict(HostStringRedisTemplateConfiguration.class);
    }

    @Test
    public void testHostRedisTemplateFailsFastWhenRouteEnabled() {
        assertHostStandardRedisBeanConflict(HostRedisTemplateConfiguration.class);
    }

    @Test
    public void testHostBeanWithStandardNameFailsFastWhenRouteEnabled() {
        assertHostStandardRedisBeanConflict(HostStandardNameRedisConnectionFactoryConfiguration.class);
    }

    @Test
    public void testHostBeanWithRouteFactoryMethodNameFailsFastWhenRouteEnabled() {
        assertHostStandardRedisBeanConflict(HostRouteFactoryMethodNameRedisConnectionFactoryConfiguration.class);
    }

    private void assertHostStandardRedisBeanConflict(Class<?> hostConfiguration) {
        routeContextRunner.withUserConfiguration(hostConfiguration).run(context -> {
            Throwable failure = context.getStartupFailure();
            assertNotNull(failure, "宿主声明标准 Redis Bean 时必须阻断 Route 默认接管");
            ConfigurationException exception = findConfigurationException(failure);
            log.info("验证宿主标准 Redis Bean 冲突，errorCode={}, message={}",
                    exception.getErrorCode(), exception.getMessage());
            assertEquals(ErrorCode.REDIS_ROUTE_015, exception.getErrorCode(),
                    "冲突必须返回默认 Bean 接管错误码");
            assertFalse(exception.getMessage().contains("localhost"),
                    "冲突消息不得暴露 Redis 连接信息");
        });
    }

    private ConfigurationException findConfigurationException(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ConfigurationException) {
                return (ConfigurationException) current;
            }
        }
        throw new AssertionError("应包含 ConfigurationException", failure);
    }

    @Configuration
    static class MockRedisFactoryConfiguration {

        @Bean
        RedisConnectionFactoryFactory redisConnectionFactoryFactory() {
            return new MockRedisConnectionFactoryFactory();
        }
    }

    @Configuration
    static class HostRedisConnectionFactoryConfiguration {

        @Bean
        RedisConnectionFactory hostRedisConnectionFactory() {
            return new MockRedisConnectionFactory("host");
        }
    }

    @Configuration
    static class HostStringRedisTemplateConfiguration {

        @Bean
        StringRedisTemplate hostStringRedisTemplate() {
            return new StringRedisTemplate(new MockRedisConnectionFactory("host"));
        }
    }

    @Configuration
    static class HostRedisTemplateConfiguration {

        @Bean
        RedisTemplate<Object, Object> hostRedisTemplate() {
            RedisTemplate<Object, Object> redisTemplate = new RedisTemplate<>();
            redisTemplate.setConnectionFactory(new MockRedisConnectionFactory("host"));
            redisTemplate.afterPropertiesSet();
            return redisTemplate;
        }
    }

    @Configuration
    static class HostStandardNameRedisConnectionFactoryConfiguration {

        @Bean(name = "redisConnectionFactory")
        RedisConnectionFactory hostRedisConnectionFactory() {
            return new MockRedisConnectionFactory("host");
        }
    }

    @Configuration
    static class HostRouteFactoryMethodNameRedisConnectionFactoryConfiguration {

        @Bean
        RedisConnectionFactory redisConnectionFactory() {
            return new MockRedisConnectionFactory("host");
        }
    }
}
