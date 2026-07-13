package io.github.surezzzzzz.sdk.lock.redis.configuration;

import io.github.surezzzzzz.sdk.lock.redis.constant.ErrorCode;
import io.github.surezzzzzz.sdk.lock.redis.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.lock.redis.constant.SimpleRedisLockConstant;
import io.github.surezzzzzz.sdk.lock.redis.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.lock.redis.executor.RedisLockExecutor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Simple Redis Lock route 缺失自动配置
 *
 * @author surezzzzzz
 */
@Configuration
@AutoConfigureAfter(name = SimpleRedisLockConstant.SIMPLE_REDIS_ROUTE_CONFIGURATION_CLASS_NAME)
@ConditionalOnMissingBean(type = SimpleRedisLockConstant.REDIS_ROUTE_TEMPLATE_CLASS_NAME)
@ConditionalOnProperty(
        prefix = SimpleRedisLockConstant.ROUTE_CONFIG_PREFIX,
        name = SimpleRedisLockConstant.PROPERTY_ENABLE,
        havingValue = SimpleRedisLockConstant.PROPERTY_VALUE_TRUE
)
public class SimpleRedisLockRouteMissingConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedisLockExecutor.class)
    public RedisLockExecutor missingRouteRedisLockExecutor() {
        throw new ConfigurationException(
                ErrorCode.CONFIG_MISSING_REDIS_ROUTE_TEMPLATE,
                ErrorMessage.CONFIG_MISSING_REDIS_ROUTE_TEMPLATE
        );
    }
}
