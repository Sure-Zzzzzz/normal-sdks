package io.github.surezzzzzz.sdk.expression.condition.parser.exception;

import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ConditionExpressionParserConstant;
import io.github.surezzzzzz.sdk.expression.condition.parser.constant.ErrorMessage;
import lombok.Getter;

/**
 * 安全的默认解析异常。原文 getter 仅供主动诊断，不可直接序列化成错误响应。
 * 显式 builder 消息和原因由调用方负责脱敏；SDK 失败不携带第三方原因。
 */
@Getter
public class ConditionExpressionParseException extends RuntimeException {
    private final ErrorType errorType;
    private final String expression;
    /** 行号从 1 开始，无位置为 -1。 */
    private final int line;
    /** 列号从 0 开始，无位置为 -1。 */
    private final int column;
    private final String offendingToken;
    private final String suggestion;

    @Getter
    public enum ErrorType {
        SYNTAX_ERROR("语法错误"),
        UNRECOGNIZED_OPERATOR("无法识别的运算符"),
        MISSING_VALUE("缺少值"),
        MISSING_FIELD("缺少字段"),
        INVALID_VALUE("无效的值"),
        MISMATCHED_PARENTHESIS("括号不匹配"),
        EMPTY_IN_LIST("IN运算符值列表为空"),
        UNSUPPORTED_OPERATOR("不支持的运算符"),
        INVALID_TIME_RANGE("无效的时间范围表达式"),
        INVALID_BOOLEAN_VALUE("无效的布尔值"),
        EMPTY_EXPRESSION("空表达式"),
        UNCLOSED_STRING("未关闭的字符串"),
        LEXICAL_ERROR("词法错误"),
        LENGTH_LIMIT("长度超限"),
        TOKEN_LIMIT("词法单元超限"),
        PARSE_DEPTH_LIMIT("解析递归层数超限"),
        AST_DEPTH_LIMIT("树深度超限"),
        AST_NODE_LIMIT("树节点数超限"),
        CONDITION_LIMIT("条件数超限"),
        IN_VALUE_LIMIT("IN值数超限");
        private final String description;
        ErrorType(String description) { this.description = description; }
        /** 稳定英文错误码。 */
        public String getCode() { return name(); }
        /** 未知码返回 null。 */
        public static ErrorType fromCode(String code) {
            for (ErrorType type : values()) if (type.name().equals(code)) return type;
            return null;
        }
        /** 判断已知错误码。 */
        public static boolean isValid(String code) { return fromCode(code) != null; }
        /** 返回独立错误码数组。 */
        public static String[] getAllCodes() {
            return java.util.Arrays.stream(values()).map(ErrorType::getCode).toArray(String[]::new);
        }
    }

    @lombok.Builder(builderClassName = "Builder")
    private ConditionExpressionParseException(ErrorType errorType, String expression, int line,
            int column, String offendingToken, String suggestion, String message, Throwable cause) {
        super(message != null ? message : String.format(ErrorMessage.PARSE_ERROR,
                errorType == null ? ErrorType.SYNTAX_ERROR.getDescription() : errorType.getDescription()), cause);
        this.errorType = errorType == null ? ErrorType.SYNTAX_ERROR : errorType;
        this.expression = expression;
        this.line = line;
        this.column = column;
        this.offendingToken = offendingToken;
        this.suggestion = suggestion == null ? ErrorMessage.CHECK_SYNTAX : suggestion;
    }

    /** 保留旧 builder(errorType) 接入方式。 */
    public static Builder builder(ErrorType errorType) { return new Builder().errorType(errorType); }
    /** 保留旧 int 方法签名和未知位置默认值，链式方法仍由 Lombok 生成。 */
    public static class Builder {
        private int line = ConditionExpressionParserConstant.UNKNOWN_POSITION;
        private int column = ConditionExpressionParserConstant.UNKNOWN_POSITION;
    }
    /** 空输入。 */
    public static ConditionExpressionParseException emptyExpression(String expression) {
        return builder(ErrorType.EMPTY_EXPRESSION).expression(expression).build();
    }
    /** 保留显式消息参数；调用方必须自行脱敏。 */
    public static ConditionExpressionParseException syntaxError(String expression, int line,
            int column, String offendingToken, String message) {
        return builder(ErrorType.SYNTAX_ERROR).expression(expression).line(line).column(column)
                .offendingToken(offendingToken).message(message).build();
    }
    /** 无法识别的运算符；默认消息不包含运算符文本。 */
    public static ConditionExpressionParseException unrecognizedOperator(String expression,
            int line, int column, String operator) {
        return at(ErrorType.UNRECOGNIZED_OPERATOR, expression, line, column, operator);
    }
    /** 缺少值。 */
    public static ConditionExpressionParseException missingValue(String expression,
            int line, int column, String operator) {
        return at(ErrorType.MISSING_VALUE, expression, line, column, operator);
    }
    /** 缺少字段。 */
    public static ConditionExpressionParseException missingField(String expression, int line, int column) {
        return at(ErrorType.MISSING_FIELD, expression, line, column, null);
    }
    /** 括号不匹配。 */
    public static ConditionExpressionParseException mismatchedParenthesis(String expression, int line, int column) {
        return at(ErrorType.MISMATCHED_PARENTHESIS, expression, line, column, null);
    }
    /** IN 列表为空。 */
    public static ConditionExpressionParseException emptyInList(String expression, int line, int column) {
        return at(ErrorType.EMPTY_IN_LIST, expression, line, column, null);
    }
    /** 时间范围无效。 */
    public static ConditionExpressionParseException invalidTimeRange(String expression,
            int line, int column, String value) {
        return at(ErrorType.INVALID_TIME_RANGE, expression, line, column, value);
    }
    /** 布尔值无效。 */
    public static ConditionExpressionParseException invalidBooleanValue(String expression,
            int line, int column, String value) {
        return at(ErrorType.INVALID_BOOLEAN_VALUE, expression, line, column, value);
    }
    private static ConditionExpressionParseException at(ErrorType type, String expression,
            int line, int column, String token) {
        return builder(type).expression(expression).line(line).column(column).offendingToken(token).build();
    }
}
