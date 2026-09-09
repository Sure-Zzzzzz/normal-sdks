package io.github.surezzzzzz.sdk.auth.iam.server.validator;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import io.github.surezzzzzz.sdk.limiter.redis.smart.execution.SmartRedisLimiterExecutionCoordinator;
import io.github.surezzzzzz.sdk.lock.redis.SimpleRedisLock;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;

/**
 * IAM 启动配置校验器。
 *
 * @author surezzzzzz
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class SimpleIamServerConfigurationValidator implements SmartInitializingSingleton {

    private final SimpleIamServerProperties properties;
    private final ConfigurableListableBeanFactory beanFactory;

    /**
     * 单例就绪后校验配置合法性与配套组件就位（Redis / 缓存 / 限流 / 锁守门）
     */
    @Override
    public void afterSingletonsInstantiated() {
        validateBasicProperties();
        validateSecurityDependencies();
    }

    private void validateBasicProperties() {
        validateIssuer();
        requireText(properties.getMe(), ServerErrorMessage.APPLICATION_NAME_REQUIRED);

        SimpleIamServerProperties.TokenConfig token = properties.getToken();
        requirePositive(token.getAccessExpiresIn(), ServerErrorMessage.ACCESS_TOKEN_EXPIRES_IN_INVALID);
        requirePositive(token.getRefreshExpiresIn(), ServerErrorMessage.REFRESH_TOKEN_EXPIRES_IN_INVALID);

        SimpleIamServerProperties.SessionConfig session = properties.getSession();
        requirePositive(session.getExpiresIn(), ServerErrorMessage.SESSION_EXPIRES_IN_INVALID);

        SimpleIamServerProperties.LoginConfig login = properties.getLogin();
        requirePositive(login.getMaxAttempts(), ServerErrorMessage.LOGIN_MAX_ATTEMPTS_INVALID);
        requirePositive(login.getLockMinutes(), ServerErrorMessage.LOGIN_LOCK_MINUTES_INVALID);

        SimpleIamServerProperties.PasswordConfig password = properties.getPassword();
        if (password.getMinLength() == null || password.getMinLength() <= 0
                || password.getMaxLength() == null || password.getMinLength() > password.getMaxLength()) {
            throw new ConfigurationException(ServerErrorMessage.PASSWORD_LENGTH_RANGE_INVALID);
        }
        int requiredCharacterTypes = countRequiredCharacterTypes(password);
        if (password.getMaxLength() < requiredCharacterTypes) {
            throw new ConfigurationException(ServerErrorMessage.PASSWORD_REQUIRED_CHARACTER_TYPES_EXCEED_MAX_LENGTH);
        }

        SimpleIamServerProperties.BootstrapConfig bootstrap = properties.getBootstrap();
        if (Boolean.TRUE.equals(bootstrap.getEnabled())) {
            requireText(bootstrap.getUsername(), ServerErrorMessage.BOOTSTRAP_USERNAME_REQUIRED);
        }
    }

    private int countRequiredCharacterTypes(SimpleIamServerProperties.PasswordConfig password) {
        int count = 0;
        if (Boolean.TRUE.equals(password.getRequireUppercase())) {
            count++;
        }
        if (Boolean.TRUE.equals(password.getRequireLowercase())) {
            count++;
        }
        if (Boolean.TRUE.equals(password.getRequireDigit())) {
            count++;
        }
        if (Boolean.TRUE.equals(password.getRequireSpecial())) {
            count++;
        }
        return count;
    }

    private void validateIssuer() {
        String issuer = properties.getIssuer();
        requireText(issuer, ServerErrorMessage.ISSUER_REQUIRED);
        try {
            URI uri = new URI(issuer);
            if (!uri.isAbsolute() || uri.getHost() == null) {
                throw new ConfigurationException(ServerErrorMessage.ISSUER_MUST_BE_ABSOLUTE_URI);
            }
        } catch (URISyntaxException ex) {
            throw new ConfigurationException(ServerErrorMessage.ISSUER_MUST_BE_ABSOLUTE_URI, ex);
        }
    }

    private void validateSecurityDependencies() {
        SimpleIamServerProperties.SecurityConfig security = properties.getSecurity();
        boolean redisRequired = Boolean.TRUE.equals(security.getRequireRedis());
        requireBean(redisRequired, RedisRouteTemplate.class,
                ServerErrorMessage.REDIS_REQUIRED_BUT_DISABLED);
        requireBean(redisRequired, SimpleRedisRouteRegistry.class,
                ServerErrorMessage.REDIS_REQUIRED_BUT_DISABLED);
        if (redisRequired) {
            validateSingleDefaultRoute();
        }
        requireBean(Boolean.TRUE.equals(security.getRequireCache()), SmartCacheManager.class,
                ServerErrorMessage.CACHE_REQUIRED_BUT_DISABLED);
        // /csrf 端点的 IP 级限流依赖 limiter 装配；协调器为 Aspect/Interceptor 共用核心，enable 即存在
        requireBean(Boolean.TRUE.equals(security.getRequireLimiter()), SmartRedisLimiterExecutionCoordinator.class,
                ServerErrorMessage.LIMITER_REQUIRED_BUT_DISABLED);
        requireBean(Boolean.TRUE.equals(security.getRequireLock()), SimpleRedisLock.class,
                ServerErrorMessage.LOCK_REQUIRED_BUT_DISABLED);
        if (Boolean.TRUE.equals(properties.getBootstrap().getEnabled())) {
            requireBean(true, SimpleRedisLock.class, ServerErrorMessage.LOCK_REQUIRED_BUT_DISABLED);
        }
    }

    private void validateSingleDefaultRoute() {
        SimpleRedisRouteRegistry registry = beanFactory.getBean(SimpleRedisRouteRegistry.class);
        if (!"default".equals(registry.getDefaultDatasourceKey())) {
            throw new ConfigurationException(ServerErrorMessage.REDIS_ROUTE_DEFAULT_SOURCE_INVALID);
        }
        if (!registry.getDatasourceKeys().equals(Collections.singleton("default"))) {
            throw new ConfigurationException(ServerErrorMessage.REDIS_ROUTE_SINGLE_SOURCE_REQUIRED);
        }
    }

    private void requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ConfigurationException(message);
        }
    }

    private void requirePositive(Integer value, String message) {
        if (value == null || value <= 0) {
            throw new ConfigurationException(message);
        }
    }

    private void requireBean(boolean required, Class<?> beanType, String message) {
        if (required && beanFactory.getBeanNamesForType(beanType, true, false).length == 0) {
            throw new ConfigurationException(message);
        }
    }
}
