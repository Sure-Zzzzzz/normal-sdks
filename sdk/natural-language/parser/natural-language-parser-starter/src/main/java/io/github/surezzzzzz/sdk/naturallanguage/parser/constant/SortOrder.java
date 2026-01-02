package io.github.surezzzzzz.sdk.naturallanguage.parser.constant;

/**
 * 排序顺序
 *
 * @author surezzzzzz
 */
public enum SortOrder {

    /**
     * 升序
     */
    ASC("asc", "升序"),

    /**
     * 降序
     */
    DESC("desc", "降序");

    private final String code;
    private final String description;

    SortOrder(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }
}
