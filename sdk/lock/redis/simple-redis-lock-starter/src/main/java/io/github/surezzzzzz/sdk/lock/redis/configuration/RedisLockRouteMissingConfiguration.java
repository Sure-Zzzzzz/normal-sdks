package io.github.surezzzzzz.sdk.lock.redis.configuration;

import io.github.surezzzzzz.sdk.lock.redis.executor.RedisLockExecutor;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * route 模式开启但缺少 RedisRouteTemplate 时注册失败型 executor，保证错误信息明确。
 * 如果业务自定义了 RedisLockExecutor，此配置不生效。
 *
 * @author surezzzzzz
 */
@Configuration
@AutoConfigureAfter(name = "io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteConfiguration")
@ConditionalOnMissingBean(type = "io.github.surezzzzzz.sdk.redis.route.template.RedisRouteTemplate")
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.lock.redis.route",
        name = "enable",
        havingValue = "true"
)
public class RedisLockRouteMissingConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedisLockExecutor.class)
    public RedisLockExecutor missingRouteRedisLockExecutor() {
        // route 模式开启但 RedisRouteTemplate 不存在，启动时明确失败
        throw new IllegalStateException(
                "已开启 lock route（io.github.surezzzzzz.sdk.lock.redis.route.enable=true），" +
                        "但未找到 RedisRouteTemplate Bean。" +
                        "请确认已引入并启用 simple-redis-route-starter。"
        );
    }
}
