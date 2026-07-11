package io.github.surezzzzzz.sdk.redis.route.support;

/**
 * Redis route 字符串 Helper
 *
 * @author surezzzzzz
 */
public final class RedisRouteStringHelper {

    private RedisRouteStringHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
