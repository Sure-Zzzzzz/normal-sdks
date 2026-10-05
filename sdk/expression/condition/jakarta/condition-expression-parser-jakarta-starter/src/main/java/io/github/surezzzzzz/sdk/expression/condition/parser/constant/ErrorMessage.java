package io.github.surezzzzzz.sdk.expression.condition.parser.constant;

/** 默认失败消息不携带输入、字段、值或第三方原因。 */
public final class ErrorMessage {
    public static final String PARSE_ERROR = "条件表达式解析失败：%s";
    public static final String INVALID_CONFIGURATION = "条件表达式解析配置无效";
    public static final String INVALID_TREE = "条件表达式树结构无效";
    public static final String METRIC_ERROR = "表达式验证失败：%s为 %d，允许最大值 %d";
    public static final String CHECK_SYNTAX = "请检查表达式语法和解析容量";
    public static final String DUPLICATE_STRATEGY = "值解析策略重复注册";
    public static final String DUPLICATE_KEYWORD = "时间关键字重复注册";
    private ErrorMessage() { }
}
