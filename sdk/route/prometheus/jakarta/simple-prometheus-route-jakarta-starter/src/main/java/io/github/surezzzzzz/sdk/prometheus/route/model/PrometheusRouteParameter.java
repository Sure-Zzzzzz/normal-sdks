package io.github.surezzzzzz.sdk.prometheus.route.model;

import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorMessage;
import lombok.Getter;

/**
 * 不可变 query 参数。
 *
 * @author surezzzzzz
 */
@Getter
public final class PrometheusRouteParameter {

    private final String name;
    private final String value;

    /**
     * 创建有序 query 参数。
     *
     * @param name  参数名称
     * @param value 参数值
     */
    public PrometheusRouteParameter(String name, String value) {
        validate(name);
        validate(value);
        this.name = name;
        this.value = value;
    }

    private static void validate(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(ErrorMessage.REQUEST_ILLEGAL);
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(ErrorMessage.REQUEST_ILLEGAL);
            }
        }
    }

}
