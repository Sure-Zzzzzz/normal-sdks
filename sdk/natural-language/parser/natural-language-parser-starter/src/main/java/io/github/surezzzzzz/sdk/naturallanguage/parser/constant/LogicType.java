package io.github.surezzzzzz.sdk.naturallanguage.parser.constant;

/**
 * 逻辑运算符类型
 *
 * @author surezzzzzz
 */
public enum LogicType {

    /**
     * 与
     */
    AND("and", "与"),

    /**
     * 或
     */
    OR("or", "或");

    private final String code;
    private final String description;

    LogicType(String code, String description) {
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
