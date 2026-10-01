package io.github.surezzzzzz.sdk.lock.redis.configuration;

import io.github.surezzzzzz.sdk.lock.redis.constant.SimpleRedisLockConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 分布式锁配置。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(SimpleRedisLockConstant.CONFIG_PREFIX)
public class SimpleRedisLockProperties {

    private Route route = new Route();

    @Data
    public static class Route {
        /**
         * 是否启用 redis-route 模式，启用后按 lockKey 路由到对应 datasource。
         * 默认 false，保持与 1.0.x 相同的单 Redis 行为。
         */
        private boolean enable = SimpleRedisLockConstant.DEFAULT_ROUTE_ENABLE;
    }
}
