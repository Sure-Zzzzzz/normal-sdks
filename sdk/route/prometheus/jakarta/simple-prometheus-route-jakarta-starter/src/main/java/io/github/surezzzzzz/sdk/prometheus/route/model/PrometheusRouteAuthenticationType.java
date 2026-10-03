package io.github.surezzzzzz.sdk.prometheus.route.model;

import lombok.Getter;

/**
 * target 认证类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum PrometheusRouteAuthenticationType {

    /**
     * 不使用认证。
     */
    NONE("NONE", "不使用认证"),

    /**
     * Basic 认证。
     */
    BASIC("BASIC", "Basic 认证"),

    /**
     * Bearer Token 认证。
     */
    BEARER("BEARER", "Bearer Token 认证");

    private final String code;
    private final String description;

    PrometheusRouteAuthenticationType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取认证类型。
     *
     * @param code 认证类型代码
     * @return 认证类型，不存在时返回 null
     */
    public static PrometheusRouteAuthenticationType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PrometheusRouteAuthenticationType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断认证类型代码是否有效。
     *
     * @param code 认证类型代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部认证类型代码。
     *
     * @return 认证类型代码数组
     */
    public static String[] getAllCodes() {
        PrometheusRouteAuthenticationType[] types = values();
        String[] codes = new String[types.length];
        for (int index = 0; index < types.length; index++) {
            codes[index] = types[index].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
