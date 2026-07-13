package io.github.surezzzzzz.sdk.lock.redis.constant;

/**
 * 错误消息常量
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置错误 ====================

    public static final String CONFIG_MISSING_REDIS_ROUTE_TEMPLATE =
            "已开启 lock route（io.github.surezzzzzz.sdk.lock.redis.route.enable=true），但未找到 RedisRouteTemplate Bean。请确认已引入并启用 simple-redis-route-starter。";
}
