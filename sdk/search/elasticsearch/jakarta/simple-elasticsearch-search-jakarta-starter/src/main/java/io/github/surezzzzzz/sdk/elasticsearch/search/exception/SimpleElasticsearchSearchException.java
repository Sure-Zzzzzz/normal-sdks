package io.github.surezzzzzz.sdk.elasticsearch.search.exception;

import lombok.Getter;

/**
 * 可安全向调用方返回的固定错误。
 */
@Getter
public class SimpleElasticsearchSearchException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String errorCode;
    private final int status;

    /**
     * 使用固定错误码、脱敏消息和 HTTP 状态创建异常。
     */
    public SimpleElasticsearchSearchException(String errorCode, String message, int status) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }
}
