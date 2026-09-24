package io.github.surezzzzzz.sdk.auth.aksk.core.constant;

import lombok.Getter;

/**
 * AKSK 令牌授权事实的来源模式 Enum
 *
 * <p>该枚举是跨 AKSK Server、资源服务的协议常量；OWNER_INHERITED 的三权真值始终来自 IAM。
 * code 与常量名同形，令牌 Claim、introspection 与日志中的值保持稳定。</p>
 *
 * @author surezzzzzz
 */
@Getter
public enum AkskAuthorizationMode {

    /**
     * 兼容历史平台客户端和未绑定用户客户端的本地静态授权
     */
    STATIC_LEGACY("STATIC_LEGACY", "本地静态授权"),

    /**
     * 由 IAM 人员授权投影实时决定的用户 AKU
     */
    OWNER_INHERITED("OWNER_INHERITED", "IAM 所属人授权投影");

    private final String code;
    private final String description;

    AkskAuthorizationMode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static AkskAuthorizationMode fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AkskAuthorizationMode type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 校验代码是否有效
     *
     * @param code 类型代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的类型代码
     *
     * @return 类型代码数组
     */
    public static String[] getAllCodes() {
        AkskAuthorizationMode[] types = values();
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
