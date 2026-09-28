package io.github.surezzzzzz.sdk.auth.aksk.server.entity;

import lombok.Getter;

/**
 * AKSK 客户端的授权来源模式 Enum
 *
 * <p>STATIC_LEGACY 沿用本地静态三权；OWNER_INHERITED 的最终授权真值只能来自身份源 reader。
 * code 与常量名同形，binding 列持久化与管理端展示的值保持稳定。</p>
 *
 * @author surezzzzzz
 */
@Getter
public enum AkskOwnerAuthorizationMode {

    /**
     * 本地静态授权（历史平台客户端与未绑定用户的存量客户端）
     */
    STATIC_LEGACY("STATIC_LEGACY", "本地静态授权"),

    /**
     * 身份源所属人授权投影
     */
    OWNER_INHERITED("OWNER_INHERITED", "身份源所属人授权投影");

    /**
     * 用于 binding 列持久化与传输的稳定编码。
     */
    private final String code;

    /**
     * 面向展示的中文说明，不应用于程序分支。
     */
    private final String description;

    AkskOwnerAuthorizationMode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举。
     *
     * @param code 模式代码
     * @return 枚举，如果不存在返回 null
     */
    public static AkskOwnerAuthorizationMode fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AkskOwnerAuthorizationMode type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 校验 code 是否有效。
     *
     * @param code 模式代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的模式代码。
     *
     * @return 模式代码数组
     */
    public static String[] getAllCodes() {
        AkskOwnerAuthorizationMode[] types = values();
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
