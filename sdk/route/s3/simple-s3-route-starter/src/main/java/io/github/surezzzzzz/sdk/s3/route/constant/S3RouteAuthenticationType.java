package io.github.surezzzzzz.sdk.s3.route.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

/**
 * S3 target 认证类型。
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public enum S3RouteAuthenticationType {

    /**
     * 匿名访问。
     */
    NONE("none", "匿名访问"),

    /**
     * AccessKey 静态凭据。
     */
    ACCESS_KEY("access_key", "AccessKey 静态凭据");

    private final String code;
    private final String description;

    /**
     * 根据代码获取枚举。
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static S3RouteAuthenticationType fromCode(String code) {
        if (code == null) {
            return null;
        }
        String lowerCode = code.toLowerCase(Locale.ROOT).trim();
        for (S3RouteAuthenticationType type : values()) {
            if (type.code.equals(lowerCode)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断类型代码是否有效。
     *
     * @param code 类型代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的类型代码。
     *
     * @return 类型代码数组
     */
    public static String[] getAllCodes() {
        S3RouteAuthenticationType[] types = values();
        String[] codes = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            codes[i] = types[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
