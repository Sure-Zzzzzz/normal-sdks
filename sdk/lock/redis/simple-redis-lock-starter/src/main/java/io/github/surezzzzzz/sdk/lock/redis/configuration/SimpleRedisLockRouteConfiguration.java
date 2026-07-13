package io.github.surezzzzzz.sdk.lock.redis.configuration;

import io.github.surezzzzzz.sdk.lock.redis.constant.SimpleRedisLockConstant;
import io.github.surezzzzzz.sdk.lock.redis.executor.RedisLockExecutor;
import io.github.surezzzzzz.sdk.lock.redis.executor.RouteRedisLockExecutor;
import io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Simple Redis Lock route 模式自动配置
 *
 * @author surezzzzzz
 */
@Configuration
@AutoConfigureAfter(name = SimpleRedisLockConstant.SIMPLE_REDIS_ROUTE_CONFIGURATION_CLASS_NAME)
@ConditionalOnClass(name = SimpleRedisLockConstant.REDIS_ROUTE_TEMPLATE_CLASS_NAME)
@ConditionalOnBean(type = SimpleRedisLockConstant.REDIS_ROUTE_TEMPLATE_CLASS_NAME)
@ConditionalOnProperty(
        prefix = SimpleRedisLockConstant.ROUTE_CONFIG_PREFIX,
        name = SimpleRedisLockConstant.PROPERTY_ENABLE,
        havingValue = SimpleRedisLockConstant.PROPERTY_VALUE_TRUE
)
public class SimpleRedisLockRouteConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedisLockExecutor.class)
    public RedisLockExecutor routeRedisLockExecutor(RedisRouteTemplate redisRouteTemplate) {
        return new RouteRedisLockExecutor(redisRouteTemplate);
    }
}
