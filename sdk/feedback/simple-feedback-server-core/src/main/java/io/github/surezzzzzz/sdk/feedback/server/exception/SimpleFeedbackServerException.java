package io.github.surezzzzzz.sdk.feedback.server.exception;

import lombok.Getter;

/**
 * Simple Feedback Server 异常基类
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleFeedbackServerException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码（ErrorCode 常量）
     */
    private final String errorCode;

    public SimpleFeedbackServerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleFeedbackServerException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
