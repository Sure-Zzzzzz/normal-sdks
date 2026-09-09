package io.github.surezzzzzz.sdk.auth.iam.server.constant;

import lombok.Getter;

/**
 * 可信应用客户端类型枚举
 *
 * @author surezzzzzz
 */
@Getter
public enum TrustedApplicationClientType {

    /**
     * 无法安全保存客户端密钥的客户端
     */
    PUBLIC("PUBLIC", "公共客户端"),

    /**
     * 可安全保存客户端密钥的服务端客户端
     */
    CONFIDENTIAL("CONFIDENTIAL", "机密客户端");

    private final String code;
    private final String description;

    TrustedApplicationClientType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 客户端类型，不存在时返回 null
     */
    public static TrustedApplicationClientType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TrustedApplicationClientType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断类型代码是否有效
     *
     * @param code 类型代码
     * @return true 表示有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部类型代码
     *
     * @return 类型代码数组
     */
    public static String[] getAllCodes() {
        TrustedApplicationClientType[] types = values();
        String[] codes = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            codes[i] = types[i].code;
        }
        return codes;
    }

    /**
     * 返回存储码值（库表 / 协议侧统一使用 code，展示文案由前端负责）
     */
    @Override
    public String toString() {
        return code;
    }
}
