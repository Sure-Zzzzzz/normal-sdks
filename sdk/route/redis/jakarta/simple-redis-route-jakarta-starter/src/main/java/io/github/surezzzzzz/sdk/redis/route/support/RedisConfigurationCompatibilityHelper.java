package io.github.surezzzzzz.sdk.redis.route.support;

import io.github.surezzzzzz.sdk.redis.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.redis.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.redis.route.exception.ConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;

/**
 * Spring Data Redis 连接配置 Helper
 *
 * @author surezzzzzz
 */
@Slf4j
public final class RedisConfigurationCompatibilityHelper {

    private static final String USERNAME = "username";
    private static final String PASSWORD = "password";
    private static final String CLIENT_NAME = "clientName";

    private RedisConfigurationCompatibilityHelper() {
    }

    /**
     * 设置 Redis username；显式提供但无法应用时拒绝启动。
     *
     * @param redisConfiguration Redis 配置对象
     * @param username           用户名
     */
    public static void applyUsername(Object redisConfiguration, String username) {
        if (username == null) {
            return;
        }
        if (!RedisRouteStringHelper.hasText(username)
                || !(redisConfiguration instanceof RedisConfiguration.WithAuthentication)) {
            throw configurationFailure(USERNAME);
        }
        try {
            ((RedisConfiguration.WithAuthentication) redisConfiguration).setUsername(username);
            log.debug("Redis 连接配置已应用，setting=[{}]", USERNAME);
        } catch (RuntimeException e) {
            throw configurationFailure(USERNAME, e);
        }
    }

    /**
     * 设置 Redis password；显式提供但无法应用时拒绝启动。
     *
     * @param redisConfiguration Redis 配置对象
     * @param password           密码
     */
    public static void applyPassword(Object redisConfiguration, String password) {
        if (password == null) {
            return;
        }
        if (!RedisRouteStringHelper.hasText(password)
                || !(redisConfiguration instanceof RedisConfiguration.WithAuthentication)) {
            throw configurationFailure(PASSWORD);
        }
        try {
            ((RedisConfiguration.WithAuthentication) redisConfiguration).setPassword(RedisPassword.of(password));
            log.debug("Redis 连接配置已应用，setting=[{}]", PASSWORD);
        } catch (RuntimeException e) {
            throw configurationFailure(PASSWORD, e);
        }
    }

    /**
     * 设置 Lettuce clientName；显式提供但无法应用时拒绝启动。
     *
     * @param clientConfigurationBuilder 客户端配置 builder
     * @param clientName                 客户端名称
     */
    public static void applyClientName(Object clientConfigurationBuilder, String clientName) {
        if (clientName == null) {
            return;
        }
        if (!RedisRouteStringHelper.hasText(clientName)
                || !(clientConfigurationBuilder instanceof LettuceClientConfiguration.LettuceClientConfigurationBuilder)) {
            throw configurationFailure(CLIENT_NAME);
        }
        try {
            ((LettuceClientConfiguration.LettuceClientConfigurationBuilder) clientConfigurationBuilder)
                    .clientName(clientName);
            log.debug("Redis 连接配置已应用，setting=[{}]", CLIENT_NAME);
        } catch (RuntimeException e) {
            throw configurationFailure(CLIENT_NAME, e);
        }
    }

    private static ConfigurationException configurationFailure(String setting) {
        log.warn("Redis 连接配置被拒绝，setting=[{}]", setting);
        return new ConfigurationException(ErrorCode.REDIS_ROUTE_006,
                ErrorMessage.REDIS_CONNECTION_CONFIGURATION_FAILED);
    }

    private static ConfigurationException configurationFailure(String setting, RuntimeException cause) {
        log.warn("Redis 连接配置应用失败，setting=[{}]，异常类型=[{}]",
                setting, cause.getClass().getSimpleName());
        return new ConfigurationException(ErrorCode.REDIS_ROUTE_006,
                ErrorMessage.REDIS_CONNECTION_CONFIGURATION_FAILED);
    }
}
