package io.github.surezzzzzz.sdk.auth.iam.server.test.cases;

import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.iam.server.validator.SimpleIamServerConfigurationValidator;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * IAM 启动配置校验测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class SimpleIamServerConfigurationValidatorTest {

    @Test
    void shouldRejectInvalidTokenLifetimeBeforeBootstrap() {
        SimpleIamServerProperties properties = validProperties();
        properties.getToken().setAccessExpiresIn(0);

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> validator(properties, emptyBeanFactory()).afterSingletonsInstantiated());

        assertEquals(ServerErrorMessage.ACCESS_TOKEN_EXPIRES_IN_INVALID, exception.getMessage());
    }

    @Test
    void shouldRejectRequiredRedisRouteTemplateThatIsAbsent() {
        SimpleIamServerProperties properties = redisRequiredProperties();

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> validator(properties, routeBeanFactory(false, true, null)).afterSingletonsInstantiated());

        assertEquals(ServerErrorMessage.REDIS_REQUIRED_BUT_DISABLED, exception.getMessage());
    }

    @Test
    void shouldRejectRequiredRedisRouteRegistryThatIsAbsent() {
        SimpleIamServerProperties properties = redisRequiredProperties();

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> validator(properties, routeBeanFactory(true, false, null)).afterSingletonsInstantiated());

        assertEquals(ServerErrorMessage.REDIS_REQUIRED_BUT_DISABLED, exception.getMessage());
    }

    @Test
    void shouldRejectRequiredLimiterCoordinatorThatIsAbsent() {
        SimpleIamServerProperties properties = redisRequiredProperties();
        properties.getSecurity().setRequireLimiter(true);
        SimpleRedisRouteRegistry registry = routeRegistry("default", Collections.singleton("default"));

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> validator(properties, routeBeanFactory(true, true, registry)).afterSingletonsInstantiated());

        assertEquals(ServerErrorMessage.LIMITER_REQUIRED_BUT_DISABLED, exception.getMessage());
    }

    @Test
    void shouldRejectNonDefaultRouteSource() {
        SimpleIamServerProperties properties = redisRequiredProperties();
        SimpleRedisRouteRegistry registry = routeRegistry("primary", Collections.singleton("primary"));

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> validator(properties, routeBeanFactory(true, true, registry)).afterSingletonsInstantiated());

        assertEquals(ServerErrorMessage.REDIS_ROUTE_DEFAULT_SOURCE_INVALID, exception.getMessage());
    }

    @Test
    void shouldRejectMultipleRouteSources() {
        SimpleIamServerProperties properties = redisRequiredProperties();
        SimpleRedisRouteRegistry registry = routeRegistry("default",
                new LinkedHashSet<>(Arrays.asList("default", "secondary")));

        ConfigurationException exception = assertThrows(ConfigurationException.class,
                () -> validator(properties, routeBeanFactory(true, true, registry)).afterSingletonsInstantiated());

        assertEquals(ServerErrorMessage.REDIS_ROUTE_SINGLE_SOURCE_REQUIRED, exception.getMessage());
    }

    @Test
    void shouldAcceptSingleDefaultRoute() {
        SimpleIamServerProperties properties = redisRequiredProperties();
        SimpleRedisRouteRegistry registry = routeRegistry("default", Collections.singleton("default"));

        assertDoesNotThrow(() -> validator(properties, routeBeanFactory(true, true, registry)).afterSingletonsInstantiated());
    }

    private SimpleIamServerConfigurationValidator validator(SimpleIamServerProperties properties,
                                                            ConfigurableListableBeanFactory beanFactory) {
        return new SimpleIamServerConfigurationValidator(properties, beanFactory);
    }

    private ConfigurableListableBeanFactory emptyBeanFactory() {
        return routeBeanFactory(false, false, null);
    }

    private ConfigurableListableBeanFactory routeBeanFactory(boolean hasTemplate, boolean hasRegistry,
                                                             SimpleRedisRouteRegistry registry) {
        ConfigurableListableBeanFactory beanFactory = mock(ConfigurableListableBeanFactory.class);
        when(beanFactory.getBeanNamesForType(any(Class.class), anyBoolean(), anyBoolean()))
                .thenAnswer(invocation -> {
                    Class<?> type = invocation.getArgument(0);
                    if (type == RedisRouteTemplate.class && hasTemplate) {
                        return new String[]{"redisRouteTemplate"};
                    }
                    if (type == SimpleRedisRouteRegistry.class && hasRegistry) {
                        return new String[]{"simpleRedisRouteRegistry"};
                    }
                    return new String[0];
                });
        if (hasRegistry) {
            when(beanFactory.getBean(SimpleRedisRouteRegistry.class)).thenReturn(registry);
        }
        return beanFactory;
    }

    private SimpleRedisRouteRegistry routeRegistry(String defaultSource, Set<String> datasourceKeys) {
        SimpleRedisRouteRegistry registry = mock(SimpleRedisRouteRegistry.class);
        when(registry.getDefaultDatasourceKey()).thenReturn(defaultSource);
        when(registry.getDatasourceKeys()).thenReturn(datasourceKeys);
        return registry;
    }

    private SimpleIamServerProperties redisRequiredProperties() {
        SimpleIamServerProperties properties = validProperties();
        properties.getBootstrap().setEnabled(false);
        properties.getSecurity().setRequireRedis(true);
        return properties;
    }

    private SimpleIamServerProperties validProperties() {
        SimpleIamServerProperties properties = new SimpleIamServerProperties();
        properties.setIssuer("http://localhost:8180");
        properties.setMe("iam-validator-test");
        properties.getSecurity().setRequireRedis(false);
        properties.getSecurity().setRequireCache(false);
        properties.getSecurity().setRequireLimiter(false);
        properties.getSecurity().setRequireLock(false);
        properties.getBootstrap().setEnabled(true);
        properties.getBootstrap().setUsername("admin");
        return properties;
    }
}
