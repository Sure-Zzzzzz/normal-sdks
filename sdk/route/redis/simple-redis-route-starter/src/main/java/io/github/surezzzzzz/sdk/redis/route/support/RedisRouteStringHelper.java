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

    /**
     * 判断字符串是否包含至少一个非空白字符。
     *
     * @param value 待判断的字符串
     * @return 字符串非 null 且去除首尾空白后非空时返回 true
     */
    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
