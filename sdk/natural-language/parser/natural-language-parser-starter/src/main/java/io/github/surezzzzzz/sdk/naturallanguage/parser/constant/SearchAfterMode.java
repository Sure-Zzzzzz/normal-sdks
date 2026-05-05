package io.github.surezzzzzz.sdk.naturallanguage.parser.constant;

import lombok.Getter;

/**
 * search_after 模式枚举
 *
 * @author surezzzzzz
 */
@Getter
public enum SearchAfterMode {

    /**
     * 追加 _id 排序
     */
    TIEBREAKER("tiebreaker", "追加 _id 排序"),

    /**
     * Point In Time
     */
    PIT("pit", "Point In Time"),

    /**
     * 不追加
     */
    NONE("none", "不追加");

    private final String code;
    private final String description;

    SearchAfterMode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取枚举
     *
     * @param code 类型代码
     * @return 枚举，如果不存在返回 null
     */
    public static SearchAfterMode fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SearchAfterMode type : values()) {
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
        SearchAfterMode[] types = values();
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
