package io.github.surezzzzzz.sdk.sensitive.keyword.constant;

import lombok.Getter;

/**
 * Mask Type Enum
 *
 * @author surezzzzzz
 */
@Getter
public enum MaskType {

    /**
     * 星号掩码
     * 示例: ABC公司 -> ***公司
     */
    ASTERISK("asterisk", "星号"),

    /**
     * 占位符
     * 示例: ABC公司 -> [ORG_001]
     */
    PLACEHOLDER("placeholder", "占位符"),

    /**
     * 哈希码
     * 示例: ABC公司 -> 3A2F8B
     */
    HASH("hash", "哈希码");

    /**
     * 类型代码
     */
    private final String code;

    /**
     * 类型描述
     */
    private final String description;

    MaskType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 掩码类型枚举，如果不存在返回null
     */
    public static MaskType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (MaskType type : values()) {
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
     * @return true有效，false无效
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
        MaskType[] types = values();
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
