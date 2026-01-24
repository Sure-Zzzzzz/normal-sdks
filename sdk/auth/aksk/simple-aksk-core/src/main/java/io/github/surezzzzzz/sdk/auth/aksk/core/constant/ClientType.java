package io.github.surezzzzzz.sdk.auth.aksk.core.constant;

import lombok.Getter;

/**
 * Client Type Enum
 *
 * @author surezzzzzz
 */
@Getter
public enum ClientType {

    /**
     * 平台级
     */
    PLATFORM(1, "platform", "平台级"),

    /**
     * 用户级
     */
    USER(2, "user", "用户级");

    private final int code;
    private final String value;
    private final String description;

    ClientType(int code, String value, String description) {
        this.code = code;
        this.value = value;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static ClientType fromCode(int code) {
        for (ClientType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断类型代码是否有效
     *
     * @param code 类型代码
     * @return true 有效，false 无效
     */
    public static boolean isValid(int code) {
        return fromCode(code) != null;
    }

    /**
     * 获取所有有效的类型代码
     *
     * @return 类型代码数组
     */
    public static int[] getAllCodes() {
        ClientType[] types = values();
        int[] codes = new int[types.length];
        for (int i = 0; i < types.length; i++) {
            codes[i] = types[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return String.valueOf(code);
    }
}
