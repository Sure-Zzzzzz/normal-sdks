package io.github.surezzzzzz.sdk.expression.condition.parser.constant;

/** 解析配置、资源限值和值策略优先级。 */
public final class ConditionExpressionParserConstant {
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.expression.condition.parser";
    public static final boolean DEFAULT_ENABLED = true;
    public static final char SINGLE_QUOTE = '\'';
    public static final char DOUBLE_QUOTE = '"';
    public static final int DEFAULT_MAX_LENGTH = 16384;
    public static final int DEFAULT_MAX_TOKENS = 4096;
    public static final int DEFAULT_MAX_PARSE_DEPTH = 32;
    public static final int MAX_PARSE_DEPTH = 64;
    public static final int DEFAULT_MAX_AST_DEPTH = 64;
    public static final int DEFAULT_MAX_AST_NODES = 1000;
    public static final int DEFAULT_MAX_CONDITIONS = 256;
    public static final int DEFAULT_MAX_IN_VALUES = 1000;
    public static final int PRIORITY_BOOLEAN = 1;
    public static final int PRIORITY_TIME = 2;
    public static final int PRIORITY_NUMBER = 3;
    public static final int PRIORITY_STRING = 99;
    public static final int UNKNOWN_POSITION = -1;
    public static final String NUMBER_PATTERN = "-?[0-9]+(\\.[0-9]+)?";
    public static final String TRUE = "true";
    public static final String FALSE = "false";
    public static final String TRUE_ALIAS = "真";
    public static final String FALSE_ALIAS = "假";
    public static final String FALSE_ALIAS_NO = "否";
    public static final String DECIMAL_SEPARATOR = ".";
    public static final int FIRST_CONTENT_INDEX = 1;
    private ConditionExpressionParserConstant() { }
}
