package io.github.surezzzzzz.sdk.prometheus.route.model;

import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorMessage;
import lombok.Getter;

import java.util.Locale;

/**
 * 不可变 HTTP header。
 *
 * @author surezzzzzz
 */
@Getter
public final class PrometheusRouteHeader {

    private final String name;
    private final String value;

    /**
     * 创建请求或响应 header。
     *
     * @param name  header 名称
     * @param value header 值
     */
    public PrometheusRouteHeader(String name, String value) {
        validate(name);
        validate(value);
        this.name = name.toLowerCase(Locale.ROOT);
        this.value = value;
    }

    private static void validate(String value) {
        if (value == null || value.trim().isEmpty() || containsControlCharacter(value)) {
            throw new IllegalArgumentException(ErrorMessage.REQUEST_ILLEGAL);
        }
    }

    private static boolean containsControlCharacter(String value) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return true;
            }
        }
        return false;
    }

}
