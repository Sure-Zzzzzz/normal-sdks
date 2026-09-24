package io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization;

import lombok.Getter;

/**
 * IAM 向 AKSK 暴露的授权控制面最终状态类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum IamAkskAuthorizationChangeType {

    /**
     * 所属人启停或权限版本变化。
     */
    OWNER_STATE("OWNER_STATE", "所属人状态变化"),

    /**
     * 目标可信应用或继承开关状态变化。
     */
    TARGET_APPLICATION_STATE("TARGET_APPLICATION_STATE", "目标应用状态变化"),

    /**
     * 所属人与目标应用的完整授权投影变化。
     */
    OWNER_APPLICATION_PROJECTION("OWNER_APPLICATION_PROJECTION", "人员应用投影变化");

    /**
     * 用于持久化与变更流传输的稳定编码。
     */
    private final String code;

    /**
     * 面向展示的中文说明，不应用于程序分支。
     */
    private final String description;

    IamAkskAuthorizationChangeType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据 code 获取枚举。
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static IamAkskAuthorizationChangeType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (IamAkskAuthorizationChangeType type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 校验 code 是否有效。
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
        IamAkskAuthorizationChangeType[] types = values();
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
