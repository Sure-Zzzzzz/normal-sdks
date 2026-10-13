package io.github.surezzzzzz.sdk.feedback.server.constant;

import lombok.Getter;

/**
 * 反馈类型枚举（bug 报告 / 功能建议 / 体验问题 / 其他）
 *
 * @author surezzzzzz
 */
@Getter
public enum FeedbackType {

    /**
     * 缺陷报告
     */
    BUG("bug", "缺陷报告"),

    /**
     * 功能建议
     */
    FEATURE("feature", "功能建议"),

    /**
     * 体验问题
     */
    EXPERIENCE("experience", "体验问题"),

    /**
     * 其他
     */
    OTHER("other", "其他");

    private final String code;
    private final String description;

    FeedbackType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static FeedbackType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (FeedbackType type : values()) {
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
        FeedbackType[] types = values();
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
