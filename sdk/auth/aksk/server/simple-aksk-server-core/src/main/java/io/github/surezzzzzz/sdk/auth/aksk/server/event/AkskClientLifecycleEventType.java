package io.github.surezzzzzz.sdk.auth.aksk.server.event;

import lombok.Getter;

/**
 * OWNER_INHERITED AKU 的稳定生命周期动作 Enum
 *
 * <p>code 与常量名同形，审计记录与日志中的值保持稳定。</p>
 *
 * @author surezzzzzz
 */
@Getter
public enum AkskClientLifecycleEventType {

    /**
     * 创建绑定
     */
    CREATED("CREATED", "创建绑定"),

    /**
     * 重命名
     */
    RENAMED("RENAMED", "重命名"),

    /**
     * 轮换密钥
     */
    SECRET_ROTATED("SECRET_ROTATED", "轮换密钥"),

    /**
     * 终止绑定
     */
    TERMINATED("TERMINATED", "终止绑定");

    private final String code;
    private final String description;

    AkskClientLifecycleEventType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static AkskClientLifecycleEventType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AkskClientLifecycleEventType type : values()) {
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
        AkskClientLifecycleEventType[] types = values();
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
