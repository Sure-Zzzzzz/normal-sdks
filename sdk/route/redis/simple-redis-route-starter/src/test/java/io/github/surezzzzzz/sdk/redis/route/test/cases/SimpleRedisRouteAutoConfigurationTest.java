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
import org.springframework.beans.factory.FactoryBean;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Primary;
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

    private static <K, V> RedisTemplate<K, V> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<K, V> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.afterPropertiesSet();
        return template;
    }

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
    public void testHostBusinessTemplatesBoundToRouteFactoryAreAllowed() {
        routeContextRunner.withUserConfiguration(HostBusinessTemplateConfiguration.class).run(context -> {
            assertNull(context.getStartupFailure(), "绑定 Route factory 的业务模板不得阻断 default-source 接管");
            RedisConnectionFactory routeFactory = context.getBean("redisConnectionFactory", RedisConnectionFactory.class);
            RedisTemplate<?, ?> authorizationTemplate = context.getBean("authorizationRedisTemplate", RedisTemplate.class);
            RedisTemplate<?, ?> consentTemplate = context.getBean("consentRedisTemplate", RedisTemplate.class);
            StringRedisTemplate otpTemplate = context.getBean("otpRedisTemplate", StringRedisTemplate.class);

            assertSame(routeFactory, authorizationTemplate.getConnectionFactory(),
                    "授权模板必须使用 Route default-source factory");
            assertSame(routeFactory, consentTemplate.getConnectionFactory(),
                    "授权同意模板必须使用 Route default-source factory");
            assertSame(routeFactory, otpTemplate.getConnectionFactory(),
                    "额外 StringRedisTemplate 必须使用 Route default-source factory");
            assertTrue(context.getBeanFactory().getBeanDefinition("authorizationRedisTemplate").isPrimary(),
                    "业务模板原有 Primary 语义必须保留");
        });
    }

    @Test
    public void testHostStandardNameTemplatesBoundToRouteFactoryAreAllowed() {
        routeContextRunner.withUserConfiguration(HostStandardNameTemplateConfiguration.class).run(context -> {
            assertNull(context.getStartupFailure(), "宿主标准名模板绑定 Route factory 时应允许保留原有语义");
            RedisConnectionFactory routeFactory = context.getBean("redisConnectionFactory", RedisConnectionFactory.class);
            RedisTemplate<?, ?> redisTemplate = context.getBean("redisTemplate", RedisTemplate.class);
            StringRedisTemplate stringRedisTemplate = context.getBean("stringRedisTemplate", StringRedisTemplate.class);

            assertSame(routeFactory, redisTemplate.getConnectionFactory(),
                    "宿主 redisTemplate 必须绑定 Route default-source factory");
            assertSame(routeFactory, stringRedisTemplate.getConnectionFactory(),
                    "宿主 stringRedisTemplate 必须绑定 Route default-source factory");
            assertTrue(context.getBeanFactory().getBeanDefinition("redisTemplate").isPrimary(),
                    "宿主 redisTemplate 的 Primary 语义必须保留");
        });
    }

    @Test
    public void testHostConnectionFactoryFailsFastWhenRouteEnabled() {
        assertFactoryOwnershipConflict(HostRedisConnectionFactoryConfiguration.class, "hostRedisConnectionFactory");
    }

    @Test
    public void testHostFactoryBeanFailsFastWhenRouteEnabled() {
        assertFactoryOwnershipConflict(HostRedisConnectionFactoryFactoryBeanConfiguration.class,
                "hostRedisConnectionFactory");
    }

    @Test
    public void testHostLazyConnectionFactoryFailsFastWhenRouteEnabled() {
        assertFactoryOwnershipConflict(HostLazyRedisConnectionFactoryConfiguration.class,
                "hostRedisConnectionFactory");
    }

    @Test
    public void testOpaqueLazyFactoryBeanProductFailsAtRuntimeWhenRouteEnabled() {
        routeContextRunner.withUserConfiguration(HostOpaqueLazyFactoryBeanConfiguration.class).run(context -> {
            assertNull(context.getStartupFailure(), "无法预判产品类型的延迟 FactoryBean 不应提前实例化");
            Throwable failure = assertThrows(Throwable.class,
                    () -> context.getBean("hostRedisConnectionFactory"));
            ConfigurationException exception = findConfigurationException(failure);
            assertEquals(ErrorCode.REDIS_ROUTE_015, exception.getErrorCode(),
                    "运行期创建的独立 RedisConnectionFactory 必须返回 factory 所有权错误码");
            assertTrue(exception.getMessage().contains("hostRedisConnectionFactory"),
                    "错误消息必须定位运行期冲突 Bean");
        });
    }

    @Test
    public void testHostTemplateWithIndependentFactoryFailsFastWhenRouteEnabled() {
        assertTemplateFactoryMismatch(HostIndependentTemplateConfiguration.class, "hostRedisTemplate");
    }

    @Test
    public void testHostTemplateWithoutFactoryFailsFastWhenRouteEnabled() {
        assertTemplateFactoryMismatch(HostTemplateWithoutFactoryConfiguration.class, "hostRedisTemplate");
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

    private void assertFactoryOwnershipConflict(Class<?> hostConfiguration, String beanName) {
        routeContextRunner.withUserConfiguration(hostConfiguration).run(context -> {
            ConfigurationException exception = findConfigurationException(context.getStartupFailure());
            log.info("验证宿主 RedisConnectionFactory 冲突，errorCode={}, message={}",
                    exception.getErrorCode(), exception.getMessage());
            assertEquals(ErrorCode.REDIS_ROUTE_015, exception.getErrorCode(),
                    "独立 RedisConnectionFactory 必须返回 factory 所有权错误码");
            assertTrue(exception.getMessage().contains(beanName), "错误消息必须定位冲突 Bean");
            assertFalse(exception.getMessage().contains("localhost"), "错误消息不得暴露 Redis 连接信息");
        });
    }

    private void assertTemplateFactoryMismatch(Class<?> hostConfiguration, String beanName) {
        routeContextRunner.withUserConfiguration(hostConfiguration).run(context -> {
            ConfigurationException exception = findConfigurationException(context.getStartupFailure());
            log.info("验证 RedisTemplate factory 不匹配，errorCode={}, message={}",
                    exception.getErrorCode(), exception.getMessage());
            assertEquals(ErrorCode.REDIS_ROUTE_016, exception.getErrorCode(),
                    "绑定非 Route factory 的模板必须返回模板 factory 错误码");
            assertTrue(exception.getMessage().contains(beanName), "错误消息必须定位冲突 Bean");
            assertFalse(exception.getMessage().contains("localhost"), "错误消息不得暴露 Redis 连接信息");
        });
    }

    private ConfigurationException findConfigurationException(Throwable failure) {
        assertNotNull(failure, "冲突场景必须阻断应用启动");
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
    static class HostBusinessTemplateConfiguration {

        @Bean
        @Primary
        RedisTemplate<String, String> authorizationRedisTemplate(RedisConnectionFactory connectionFactory) {
            return redisTemplate(connectionFactory);
        }

        @Bean
        RedisTemplate<String, String> consentRedisTemplate(RedisConnectionFactory connectionFactory) {
            return redisTemplate(connectionFactory);
        }

        @Bean
        StringRedisTemplate otpRedisTemplate(RedisConnectionFactory connectionFactory) {
            return new StringRedisTemplate(connectionFactory);
        }
    }

    @Configuration
    static class HostStandardNameTemplateConfiguration {

        @Bean(name = "redisTemplate")
        @Primary
        RedisTemplate<Object, Object> hostRedisTemplate(RedisConnectionFactory connectionFactory) {
            return redisTemplate(connectionFactory);
        }

        @Bean(name = "stringRedisTemplate")
        StringRedisTemplate hostStringRedisTemplate(RedisConnectionFactory connectionFactory) {
            return new StringRedisTemplate(connectionFactory);
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
    static class HostRedisConnectionFactoryFactoryBeanConfiguration {

        @Bean
        FactoryBean<RedisConnectionFactory> hostRedisConnectionFactory() {
            return new FactoryBean<RedisConnectionFactory>() {

                @Override
                public RedisConnectionFactory getObject() {
                    return new MockRedisConnectionFactory("host");
                }

                @Override
                public Class<?> getObjectType() {
                    return RedisConnectionFactory.class;
                }
            };
        }
    }

    @Configuration
    static class HostLazyRedisConnectionFactoryConfiguration {

        @Bean
        @Lazy
        RedisConnectionFactory hostRedisConnectionFactory() {
            return new MockRedisConnectionFactory("host");
        }
    }

    @Configuration
    static class HostOpaqueLazyFactoryBeanConfiguration {

        @Bean
        @Lazy
        FactoryBean<Object> hostRedisConnectionFactory() {
            return new FactoryBean<Object>() {

                @Override
                public Object getObject() {
                    return new MockRedisConnectionFactory("host");
                }

                @Override
                public Class<?> getObjectType() {
                    return null;
                }
            };
        }
    }

    @Configuration
    static class HostIndependentTemplateConfiguration {

        @Bean
        RedisTemplate<Object, Object> hostRedisTemplate() {
            return redisTemplate(new MockRedisConnectionFactory("host"));
        }
    }

    @Configuration
    static class HostTemplateWithoutFactoryConfiguration {

        @Bean
        RedisTemplate<Object, Object> hostRedisTemplate() {
            return new RedisTemplate<>();
        }
    }
}
