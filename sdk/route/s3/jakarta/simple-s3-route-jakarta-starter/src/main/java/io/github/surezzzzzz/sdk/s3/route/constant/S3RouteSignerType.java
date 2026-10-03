package io.github.surezzzzzz.sdk.s3.route.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Locale;

/**
 * S3 Route target 签名版本。
 *
 * @author surezzzzzz
 */
@Getter
@RequiredArgsConstructor
public enum S3RouteSignerType {

    /**
     * AWS Signature V4（SDK 默认，S3 生态主流）。
     */
    AWS_V4("aws_v4", "AWS Signature V4 签名"),

    /**
     * S3 V2 签名（HmacSHA1），用于部署为 V2 签名的 S3 兼容存储。
     */
    S3_V2("s3_v2", "S3 V2 签名（HmacSHA1）");

    private final String code;
    private final String description;

    /**
     * 根据代码获取枚举。
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static S3RouteSignerType fromCode(String code) {
        if (code == null) {
            return null;
        }
        String lowerCode = code.toLowerCase(Locale.ROOT).trim();
        for (S3RouteSignerType type : values()) {
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
        S3RouteSignerType[] types = values();
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
