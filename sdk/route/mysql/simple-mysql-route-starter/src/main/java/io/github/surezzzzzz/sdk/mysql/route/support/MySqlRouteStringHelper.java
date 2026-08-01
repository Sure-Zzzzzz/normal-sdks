package io.github.surezzzzzz.sdk.mysql.route.support;

/**
 * MySQL Route 字符串帮助类。
 *
 * @author surezzzzzz
 */
public final class MySqlRouteStringHelper {

    private MySqlRouteStringHelper() {
        throw new UnsupportedOperationException("帮助类不能实例化");
    }

    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
