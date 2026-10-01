package io.github.surezzzzzz.sdk.mysql.route.support;

import io.github.surezzzzzz.sdk.mysql.route.constant.ErrorMessage;

/**
 * MySQL Route 字符串帮助类。
 *
 * @author surezzzzzz
 */
public final class MySqlRouteStringHelper {

    private MySqlRouteStringHelper() {
        throw new UnsupportedOperationException(ErrorMessage.HELPER_CLASS_INSTANTIATION_UNSUPPORTED);
    }

    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
