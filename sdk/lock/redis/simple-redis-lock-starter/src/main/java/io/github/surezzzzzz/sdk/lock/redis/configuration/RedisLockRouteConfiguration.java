package io.github.surezzzzzz.sdk.lock.redis.configuration;

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
 * Redis 分布式锁 route 模式自动配置。
 * 需要同时满足：引入了 simple-redis-route-starter、RedisRouteTemplate Bean 存在、route.enable=true。
 *
 * @author surezzzzzz
 */
@Configuration
@AutoConfigureAfter(name = "io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteConfiguration")
@ConditionalOnClass(name = "io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate")
@ConditionalOnBean(type = "io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate")
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.lock.redis.route",
        name = "enable",
        havingValue = "true"
)
public class RedisLockRouteConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedisLockExecutor.class)
    public RedisLockExecutor routeRedisLockExecutor(RedisRouteTemplate redisRouteTemplate) {
        return new RouteRedisLockExecutor(redisRouteTemplate);
    }
}
