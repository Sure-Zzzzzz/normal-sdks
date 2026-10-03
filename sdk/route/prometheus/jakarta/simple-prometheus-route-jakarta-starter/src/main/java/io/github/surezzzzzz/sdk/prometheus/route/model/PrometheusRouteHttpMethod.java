package io.github.surezzzzzz.sdk.prometheus.route.model;

import lombok.Getter;

/**
 * Route 支持的 HTTP 方法。
 *
 * @author surezzzzzz
 */
@Getter
public enum PrometheusRouteHttpMethod {

    /**
     * GET 请求。
     */
    GET("GET", "GET 请求"),

    /**
     * POST 请求。
     */
    POST("POST", "POST 请求");

    private final String code;
    private final String description;

    PrometheusRouteHttpMethod(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取 HTTP 方法。
     *
     * @param code HTTP 方法代码
     * @return HTTP 方法，不存在时返回 null
     */
    public static PrometheusRouteHttpMethod fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PrometheusRouteHttpMethod method : values()) {
            if (method.code.equalsIgnoreCase(code)) {
                return method;
            }
        }
        return null;
    }

    /**
     * 判断 HTTP 方法代码是否有效。
     *
     * @param code HTTP 方法代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部 HTTP 方法代码。
     *
     * @return HTTP 方法代码数组
     */
    public static String[] getAllCodes() {
        PrometheusRouteHttpMethod[] methods = values();
        String[] codes = new String[methods.length];
        for (int index = 0; index < methods.length; index++) {
            codes[index] = methods[index].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
