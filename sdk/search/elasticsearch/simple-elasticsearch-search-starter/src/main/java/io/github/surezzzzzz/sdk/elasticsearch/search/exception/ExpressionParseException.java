package io.github.surezzzzzz.sdk.elasticsearch.search.exception;

import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;

/**
 * 高级表达式解析异常
 * 包装 ConditionExpressionParseException，对外暴露 SDK 自己的异常体系
 *
 * @author surezzzzzz
 */
public class ExpressionParseException extends SimpleElasticsearchSearchException {

    public ExpressionParseException(String message) {
        super(ErrorCode.EXPRESSION_PARSE_FAILED, message);
    }

    public ExpressionParseException(String message, Throwable cause) {
        super(ErrorCode.EXPRESSION_PARSE_FAILED, message, cause);
    }
}
