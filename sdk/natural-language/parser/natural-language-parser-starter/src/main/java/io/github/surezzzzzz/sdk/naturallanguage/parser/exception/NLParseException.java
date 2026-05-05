package io.github.surezzzzzz.sdk.naturallanguage.parser.exception;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.ErrorCode;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.ErrorMessage;

/**
 * 自然语言解析异常
 * 提供详细的错误上下文、位置信息和修复建议
 *
 * @author surezzzzzz
 */
public class NLParseException extends NLParserException {

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
     * 完整构造函数
     */
    private NLParseException(String errorCode,
                             String query,
                             int position,
                             String parseState,
                             String suggestion,
                             String relatedToken,
                             String message,
                             Throwable cause) {
        super(errorCode, message, cause);
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

    public static Builder builder(String errorCode) {
        return new Builder(errorCode);
    }

    public static class Builder {
        private final String errorCode;
        private String query;
        private int position = -1;
        private String parseState;
        private String suggestion;
        private String relatedToken;
        private String message;
        private Throwable cause;

        private Builder(String errorCode) {
            this.errorCode = errorCode;
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
            return new NLParseException(errorCode, query, position, parseState,
                    suggestion, relatedToken, message, cause);
        }
    }

    // ========== 便捷静态工厂方法 ==========

    /**
     * 创建"空查询"异常
     *
     * @param query 原始查询文本
     * @return 解析异常
     */
    public static NLParseException emptyQuery(String query) {
        return builder(ErrorCode.PARSE_EMPTY_QUERY)
                .query(query)
                .message(ErrorMessage.PARSE_EMPTY_QUERY)
                .suggestion("请输入有效的查询条件，例如：查询年龄大于18的用户")
                .build();
    }

    /**
     * 创建"语法错误"异常
     *
     * @param query    原始查询文本
     * @param position 错误位置
     * @param detail   错误详情
     * @return 解析异常
     */
    public static NLParseException syntaxError(String query, int position, String detail) {
        return builder(ErrorCode.PARSE_SYNTAX_ERROR)
                .query(query)
                .position(position)
                .message(String.format(ErrorMessage.PARSE_SYNTAX_ERROR, detail))
                .build();
    }

    /**
     * 创建"不支持的意图类型"异常
     *
     * @param intentType 意图类型
     * @return 解析异常
     */
    public static NLParseException unsupportedIntent(String intentType) {
        return builder(ErrorCode.PARSE_UNSUPPORTED_INTENT)
                .message(String.format(ErrorMessage.PARSE_UNSUPPORTED_INTENT, intentType))
                .build();
    }

    /**
     * 创建"缺少值"异常
     *
     * @param query    原始查询文本
     * @param position 错误位置
     * @param operator 相关操作符
     * @return 解析异常
     */
    public static NLParseException missingValue(String query, int position, String operator) {
        return builder(ErrorCode.PARSE_SYNTAX_ERROR)
                .query(query)
                .position(position)
                .relatedToken(operator)
                .suggestion("请在操作符\"" + operator + "\"后添加一个值")
                .message(String.format(ErrorMessage.PARSE_SYNTAX_ERROR, "操作符后缺少值"))
                .build();
    }

    /**
     * 创建"缺少操作符"异常
     *
     * @param query    原始查询文本
     * @param position 错误位置
     * @param field    字段名
     * @param value    值
     * @return 解析异常
     */
    public static NLParseException missingOperator(String query, int position, String field, String value) {
        return builder(ErrorCode.PARSE_SYNTAX_ERROR)
                .query(query)
                .position(position)
                .relatedToken(field)
                .suggestion("请在字段\"" + field + "\"和值\"" + value + "\"之间添加操作符，例如：等于、大于、包含等")
                .message(String.format(ErrorMessage.PARSE_SYNTAX_ERROR, "字段和值之间缺少操作符"))
                .build();
    }

    /**
     * 创建"无法识别的操作符"异常
     *
     * @param query      原始查询文本
     * @param position   错误位置
     * @param operator   操作符
     * @param suggestion 建议
     * @return 解析异常
     */
    public static NLParseException unrecognizedOperator(String query, int position, String operator, String suggestion) {
        Builder builder = builder(ErrorCode.PARSE_SYNTAX_ERROR)
                .query(query)
                .position(position)
                .relatedToken(operator)
                .message(String.format(ErrorMessage.PARSE_SYNTAX_ERROR, "无法识别的操作符: " + operator));

        if (suggestion != null) {
            builder.suggestion("您是否想输入：" + suggestion);
        } else {
            builder.suggestion("请使用支持的操作符：等于、不等于、大于、小于、包含、不包含、在...之间等");
        }

        return builder.build();
    }
}
