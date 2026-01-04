package io.github.surezzzzzz.sdk.expression.condition.parser.exception;

/**
 * 条件表达式解析异常
 * 提供详细的错误上下文、位置信息和修复建议
 * <p>
 * 设计目标：
 * <ol>
 *   <li>提供清晰的错误信息，帮助用户快速定位问题</li>
 *   <li>根据错误类型给出具体的修复建议</li>
 *   <li>显示错误发生的上下文（表达式文本和位置）</li>
 * </ol>
 *
 * @author surezzzzzz
 */
public class ConditionExpressionParseException extends RuntimeException {

    /**
     * 错误类型
     */
    private final ErrorType errorType;

    /**
     * 原始表达式文本
     */
    private final String expression;

    /**
     * 错误位置（行号，从1开始）
     */
    private final int line;

    /**
     * 错误位置（列号，从0开始）
     */
    private final int column;

    /**
     * 导致错误的 token 文本（可选）
     */
    private final String offendingToken;

    /**
     * 修复建议（可选）
     */
    private final String suggestion;

    /**
     * 错误类型枚举
     */
    public enum ErrorType {
        /**
         * 语法错误：无法识别的语法结构
         */
        SYNTAX_ERROR("语法错误"),

        /**
         * 无法识别的运算符：拼写错误或不支持的运算符
         */
        UNRECOGNIZED_OPERATOR("无法识别的运算符"),

        /**
         * 缺少值：运算符后没有值
         */
        MISSING_VALUE("缺少值"),

        /**
         * 缺少字段：条件表达式缺少字段名
         */
        MISSING_FIELD("缺少字段"),

        /**
         * 无效的值：值格式不正确
         */
        INVALID_VALUE("无效的值"),

        /**
         * 括号不匹配：左右括号数量不一致
         */
        MISMATCHED_PARENTHESIS("括号不匹配"),

        /**
         * IN 运算符值列表为空
         */
        EMPTY_IN_LIST("IN运算符值列表为空"),

        /**
         * 不支持的运算符：字段不支持该运算符
         */
        UNSUPPORTED_OPERATOR("不支持的运算符"),

        /**
         * 无效的时间范围表达式
         */
        INVALID_TIME_RANGE("无效的时间范围表达式"),

        /**
         * 无效的布尔值
         */
        INVALID_BOOLEAN_VALUE("无效的布尔值"),

        /**
         * 空表达式：输入为空或只包含空白字符
         */
        EMPTY_EXPRESSION("空表达式"),

        /**
         * 未关闭的字符串：字符串缺少结束引号
         */
        UNCLOSED_STRING("未关闭的字符串");

        private final String description;

        ErrorType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * 完整构造函数
     */
    private ConditionExpressionParseException(
            ErrorType errorType,
            String expression,
            int line,
            int column,
            String offendingToken,
            String suggestion,
            String message,
            Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.expression = expression;
        this.line = line;
        this.column = column;
        this.offendingToken = offendingToken;
        this.suggestion = suggestion;
    }

    /**
     * 获取格式化的错误消息（包含上下文和建议）
     */
    @Override
    public String getMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append(super.getMessage());

        // 添加错误类型
        if (errorType != null) {
            sb.append("\n错误类型: ").append(errorType.getDescription());
        }

        // 添加位置和上下文
        if (expression != null && line >= 0) {
            sb.append("\n位置: 第").append(line).append("行，第").append(column).append("列");
            sb.append("\n表达式: \"").append(expression).append("\"");

            // 添加位置指示器
            if (column >= 0) {
                sb.append("\n          ");
                for (int i = 0; i < column; i++) {
                    sb.append(" ");
                }
                sb.append("^");
            }
        }

        // 添加导致错误的 token
        if (offendingToken != null) {
            sb.append("\n问题token: \"").append(offendingToken).append("\"");
        }

        // 添加建议
        if (suggestion != null) {
            sb.append("\n建议: ").append(suggestion);
        }

        return sb.toString();
    }

    // ========== Getter方法 ==========

    public ErrorType getErrorType() {
        return errorType;
    }

    public String getExpression() {
        return expression;
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getOffendingToken() {
        return offendingToken;
    }

    public String getSuggestion() {
        return suggestion;
    }

    // ========== Builder模式 ==========

    public static Builder builder(ErrorType errorType) {
        return new Builder(errorType);
    }

    public static class Builder {
        private final ErrorType errorType;
        private String expression;
        private int line = -1;
        private int column = -1;
        private String offendingToken;
        private String suggestion;
        private String message;
        private Throwable cause;

        private Builder(ErrorType errorType) {
            this.errorType = errorType;
        }

        public Builder expression(String expression) {
            this.expression = expression;
            return this;
        }

        public Builder line(int line) {
            this.line = line;
            return this;
        }

        public Builder column(int column) {
            this.column = column;
            return this;
        }

        public Builder offendingToken(String offendingToken) {
            this.offendingToken = offendingToken;
            return this;
        }

        public Builder suggestion(String suggestion) {
            this.suggestion = suggestion;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder cause(Throwable cause) {
            this.cause = cause;
            return this;
        }

        public ConditionExpressionParseException build() {
            // 如果没有提供message，根据错误类型生成默认消息
            if (message == null) {
                message = generateDefaultMessage();
            }
            return new ConditionExpressionParseException(
                    errorType, expression, line, column,
                    offendingToken, suggestion, message, cause);
        }

        private String generateDefaultMessage() {
            switch (errorType) {
                case SYNTAX_ERROR:
                    return "条件表达式包含语法错误";
                case UNRECOGNIZED_OPERATOR:
                    return "无法识别的运算符";
                case MISSING_VALUE:
                    return "运算符后缺少值";
                case MISSING_FIELD:
                    return "条件表达式缺少字段名";
                case INVALID_VALUE:
                    return "值格式不正确";
                case MISMATCHED_PARENTHESIS:
                    return "括号不匹配";
                case EMPTY_IN_LIST:
                    return "IN 运算符的值列表不能为空";
                case UNSUPPORTED_OPERATOR:
                    return "字段不支持该运算符";
                case INVALID_TIME_RANGE:
                    return "无效的时间范围表达式";
                case INVALID_BOOLEAN_VALUE:
                    return "无效的布尔值";
                case EMPTY_EXPRESSION:
                    return "表达式为空或只包含空白字符";
                case UNCLOSED_STRING:
                    return "字符串缺少结束引号";
                default:
                    return "解析错误";
            }
        }
    }

    // ========== 便捷静态工厂方法 ==========

    /**
     * 创建"空表达式"异常
     */
    public static ConditionExpressionParseException emptyExpression(String expression) {
        return builder(ErrorType.EMPTY_EXPRESSION)
                .expression(expression)
                .suggestion("请输入有效的条件表达式，例如：类型='活跃' AND 分类='高'")
                .build();
    }

    /**
     * 创建"语法错误"异常
     */
    public static ConditionExpressionParseException syntaxError(
            String expression, int line, int column, String offendingToken, String message) {
        return builder(ErrorType.SYNTAX_ERROR)
                .expression(expression)
                .line(line)
                .column(column)
                .offendingToken(offendingToken)
                .message(message)
                .build();
    }

    /**
     * 创建"无法识别的运算符"异常
     */
    public static ConditionExpressionParseException unrecognizedOperator(
            String expression, int line, int column, String operator) {
        return builder(ErrorType.UNRECOGNIZED_OPERATOR)
                .expression(expression)
                .line(line)
                .column(column)
                .offendingToken(operator)
                .suggestion("请使用支持的运算符：=, !=, >, <, >=, <=, IN, NOT IN, LIKE, PREFIX LIKE, SUFFIX LIKE, IS NULL, IS NOT NULL")
                .build();
    }

    /**
     * 创建"缺少值"异常
     */
    public static ConditionExpressionParseException missingValue(
            String expression, int line, int column, String operator) {
        return builder(ErrorType.MISSING_VALUE)
                .expression(expression)
                .line(line)
                .column(column)
                .offendingToken(operator)
                .suggestion("请在运算符\"" + operator + "\"后添加一个值")
                .build();
    }

    /**
     * 创建"缺少字段"异常
     */
    public static ConditionExpressionParseException missingField(
            String expression, int line, int column) {
        return builder(ErrorType.MISSING_FIELD)
                .expression(expression)
                .line(line)
                .column(column)
                .suggestion("条件表达式必须包含字段名，例如：类型='活跃'")
                .build();
    }

    /**
     * 创建"括号不匹配"异常
     */
    public static ConditionExpressionParseException mismatchedParenthesis(
            String expression, int line, int column) {
        return builder(ErrorType.MISMATCHED_PARENTHESIS)
                .expression(expression)
                .line(line)
                .column(column)
                .suggestion("请检查括号是否正确配对")
                .build();
    }

    /**
     * 创建"IN运算符值列表为空"异常
     */
    public static ConditionExpressionParseException emptyInList(
            String expression, int line, int column) {
        return builder(ErrorType.EMPTY_IN_LIST)
                .expression(expression)
                .line(line)
                .column(column)
                .suggestion("IN 运算符至少需要一个值，例如：类型 IN ('高','中','低')")
                .build();
    }

    /**
     * 创建"无效的时间范围"异常
     */
    public static ConditionExpressionParseException invalidTimeRange(
            String expression, int line, int column, String value) {
        return builder(ErrorType.INVALID_TIME_RANGE)
                .expression(expression)
                .line(line)
                .column(column)
                .offendingToken(value)
                .suggestion("请使用支持的时间范围：近1个月, 近三个月, 近半年, 近1年 等")
                .build();
    }

    /**
     * 创建"无效的布尔值"异常
     */
    public static ConditionExpressionParseException invalidBooleanValue(
            String expression, int line, int column, String value) {
        return builder(ErrorType.INVALID_BOOLEAN_VALUE)
                .expression(expression)
                .line(line)
                .column(column)
                .offendingToken(value)
                .suggestion("请使用支持的布尔值：true, false, 是, 否")
                .build();
    }
}
