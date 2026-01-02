package io.github.surezzzzzz.sdk.naturallanguage.parser.exception;

/**
 * 自然语言解析异常
 * 提供详细的错误上下文、位置信息和修复建议
 * <p>
 * 设计目标：
 * 1. 提供清晰的错误信息，帮助用户快速定位问题
 * 2. 根据错误类型给出具体的修复建议
 * 3. 显示错误发生的上下文（查询文本和位置）
 *
 * @author surezzzzzz
 */
public class NLParseException extends RuntimeException {

    /**
     * 错误类型
     */
    private final ErrorType errorType;

    /**
     * 原始查询文本
     */
    private final String query;

    /**
     * 错误位置（token索引，-1表示无法确定位置）
     */
    private final int position;

    /**
     * 当前解析状态（可选）
     */
    private final String parseState;

    /**
     * 修复建议（可选）
     */
    private final String suggestion;

    /**
     * 相关的token文本（可选）
     */
    private final String relatedToken;

    /**
     * 错误类型枚举
     */
    public enum ErrorType {
        /**
         * 缺少值：操作符后没有值
         */
        MISSING_VALUE("缺少值"),

        /**
         * 缺少操作符：字段和值之间缺少操作符
         */
        MISSING_OPERATOR("缺少操作符"),

        /**
         * 无法识别的操作符：拼写错误或不支持的操作符
         */
        UNRECOGNIZED_OPERATOR("无法识别的操作符"),

        /**
         * 空查询：输入为空或只包含停用词
         */
        EMPTY_QUERY("空查询"),

        /**
         * 类型不匹配：值与字段类型不匹配
         */
        TYPE_MISMATCH("类型不匹配"),

        /**
         * 语法错误：其他语法错误
         */
        SYNTAX_ERROR("语法错误");

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
    private NLParseException(ErrorType errorType,
                             String query,
                             int position,
                             String parseState,
                             String suggestion,
                             String relatedToken,
                             String message,
                             Throwable cause) {
        super(message, cause);
        this.errorType = errorType;
        this.query = query;
        this.position = position;
        this.parseState = parseState;
        this.suggestion = suggestion;
        this.relatedToken = relatedToken;
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
        if (query != null && position >= 0) {
            sb.append("\n位置: ").append(position);
            sb.append("\n上下文: \"").append(query).append("\"");

            // 添加位置指示器
            sb.append("\n        ");
            for (int i = 0; i < Math.min(position, query.length()); i++) {
                sb.append(" ");
            }
            sb.append("^");
        }

        // 添加相关token
        if (relatedToken != null) {
            sb.append("\n相关token: \"").append(relatedToken).append("\"");
        }

        // 添加解析状态
        if (parseState != null) {
            sb.append("\n解析状态: ").append(parseState);
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

    public String getQuery() {
        return query;
    }

    public int getPosition() {
        return position;
    }

    public String getParseState() {
        return parseState;
    }

    public String getSuggestion() {
        return suggestion;
    }

    public String getRelatedToken() {
        return relatedToken;
    }

    // ========== Builder模式 ==========

    public static Builder builder(ErrorType errorType) {
        return new Builder(errorType);
    }

    public static class Builder {
        private final ErrorType errorType;
        private String query;
        private int position = -1;
        private String parseState;
        private String suggestion;
        private String relatedToken;
        private String message;
        private Throwable cause;

        private Builder(ErrorType errorType) {
            this.errorType = errorType;
        }

        public Builder query(String query) {
            this.query = query;
            return this;
        }

        public Builder position(int position) {
            this.position = position;
            return this;
        }

        public Builder parseState(String parseState) {
            this.parseState = parseState;
            return this;
        }

        public Builder suggestion(String suggestion) {
            this.suggestion = suggestion;
            return this;
        }

        public Builder relatedToken(String relatedToken) {
            this.relatedToken = relatedToken;
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

        public NLParseException build() {
            // 如果没有提供message，根据错误类型生成默认消息
            if (message == null) {
                message = generateDefaultMessage();
            }
            return new NLParseException(errorType, query, position, parseState,
                    suggestion, relatedToken, message, cause);
        }

        private String generateDefaultMessage() {
            switch (errorType) {
                case MISSING_VALUE:
                    return "操作符后缺少值";
                case MISSING_OPERATOR:
                    return "字段和值之间缺少操作符";
                case UNRECOGNIZED_OPERATOR:
                    return "无法识别的操作符";
                case EMPTY_QUERY:
                    return "查询为空或只包含停用词";
                case TYPE_MISMATCH:
                    return "值与字段类型不匹配";
                case SYNTAX_ERROR:
                default:
                    return "语法错误";
            }
        }
    }

    // ========== 便捷静态工厂方法 ==========

    /**
     * 创建"缺少值"异常
     */
    public static NLParseException missingValue(String query, int position, String operator) {
        return builder(ErrorType.MISSING_VALUE)
                .query(query)
                .position(position)
                .relatedToken(operator)
                .suggestion("请在操作符\"" + operator + "\"后添加一个值")
                .build();
    }

    /**
     * 创建"缺少操作符"异常
     */
    public static NLParseException missingOperator(String query, int position, String field, String value) {
        return builder(ErrorType.MISSING_OPERATOR)
                .query(query)
                .position(position)
                .relatedToken(field)
                .suggestion("请在字段\"" + field + "\"和值\"" + value + "\"之间添加操作符，例如：等于、大于、包含等")
                .build();
    }

    /**
     * 创建"无法识别的操作符"异常
     */
    public static NLParseException unrecognizedOperator(String query, int position, String operator, String suggestion) {
        Builder builder = builder(ErrorType.UNRECOGNIZED_OPERATOR)
                .query(query)
                .position(position)
                .relatedToken(operator);

        if (suggestion != null) {
            builder.suggestion("您是否想输入：" + suggestion);
        } else {
            builder.suggestion("请使用支持的操作符：等于、不等于、大于、小于、包含、不包含、在...之间等");
        }

        return builder.build();
    }

    /**
     * 创建"空查询"异常
     */
    public static NLParseException emptyQuery(String query) {
        return builder(ErrorType.EMPTY_QUERY)
                .query(query)
                .suggestion("请输入有效的查询条件，例如：查询年龄大于18的用户")
                .build();
    }

    /**
     * 创建"类型不匹配"异常
     */
    public static NLParseException typeMismatch(String query, int position, String field, String value, String expectedType) {
        return builder(ErrorType.TYPE_MISMATCH)
                .query(query)
                .position(position)
                .relatedToken(value)
                .suggestion("字段\"" + field + "\"期望" + expectedType + "类型的值，但得到了\"" + value + "\"")
                .build();
    }

    /**
     * 创建通用语法错误异常
     */
    public static NLParseException syntaxError(String query, int position, String message) {
        return builder(ErrorType.SYNTAX_ERROR)
                .query(query)
                .position(position)
                .message(message)
                .build();
    }
}
