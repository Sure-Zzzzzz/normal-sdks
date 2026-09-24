package io.github.surezzzzzz.sdk.auth.aksk.server.constant;

import lombok.Getter;

/**
 * OWNER_INHERITED AKU 的 IAM 授权同步模式 Enum
 *
 * <p>code 与常量名同形，配置值与日志中的形态保持稳定。</p>
 *
 * @author surezzzzzz
 */
@Getter
public enum AkskOwnerAuthorizationSynchronizationMode {

    /**
     * 默认模式：读取 AKSK 本地投影，超过同步租约后失败关闭
     */
    EVENTUAL_WITH_LEASE("EVENTUAL_WITH_LEASE", "本地投影加同步租约"),

    /**
     * 兼容诊断模式：每次判定同步 resolve IAM，不作为默认生产模式
     */
    STRICT_ONLINE("STRICT_ONLINE", "每次判定在线 resolve");

    private final String code;
    private final String description;

    AkskOwnerAuthorizationSynchronizationMode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static AkskOwnerAuthorizationSynchronizationMode fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AkskOwnerAuthorizationSynchronizationMode type : values()) {
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
        AkskOwnerAuthorizationSynchronizationMode[] types = values();
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
