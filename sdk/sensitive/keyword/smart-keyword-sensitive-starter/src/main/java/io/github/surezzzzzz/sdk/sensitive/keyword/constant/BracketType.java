package io.github.surezzzzzz.sdk.sensitive.keyword.constant;

import lombok.Getter;

/**
 * Bracket Type Enum
 * 括号类型枚举
 *
 * @author surezzzzzz
 */
@Getter
public enum BracketType {

    /**
     * 中文括号
     */
    CHINESE("（", "）"),

    /**
     * 英文括号
     */
    ENGLISH("(", ")");

    /**
     * 左括号
     */
    private final String leftBracket;

    /**
     * 右括号
     */
    private final String rightBracket;

    BracketType(String leftBracket, String rightBracket) {
        this.leftBracket = leftBracket;
        this.rightBracket = rightBracket;
    }

    /**
     * 根据左括号获取对应的右括号
     *
     * @param leftBracket 左括号
     * @return 右括号，如果没有找到则返回null
     */
    public static String getRightBracket(char leftBracket) {
        for (BracketType type : values()) {
            if (type.leftBracket.charAt(0) == leftBracket) {
                return type.rightBracket;
            }
        }
        return null;
    }

    /**
     * 判断字符是否是左括号
     *
     * @param c 字符
     * @return true表示是左括号
     */
    public static boolean isLeftBracket(char c) {
        for (BracketType type : values()) {
            if (type.leftBracket.charAt(0) == c) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断字符是否是右括号
     *
     * @param c 字符
     * @return true表示是右括号
     */
    public static boolean isRightBracket(char c) {
        for (BracketType type : values()) {
            if (type.rightBracket.charAt(0) == c) {
                return true;
            }
        }
        return false;
    }

    /**
     * 判断字符是否是括号（左或右）
     *
     * @param c 字符
     * @return true表示是括号
     */
    public static boolean isBracket(char c) {
        return isLeftBracket(c) || isRightBracket(c);
    }
}
