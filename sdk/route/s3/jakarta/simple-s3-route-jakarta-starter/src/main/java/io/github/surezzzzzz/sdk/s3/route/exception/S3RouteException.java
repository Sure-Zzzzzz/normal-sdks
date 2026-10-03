package io.github.surezzzzzz.sdk.s3.route.exception;

import lombok.Getter;

/**
 * S3 Route 受控异常。
 *
 * @author surezzzzzz
 */
@Getter
public class S3RouteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 稳定错误码。
     */
    private final String errorCode;

    /**
     * 创建受控异常。
     *
     * @param errorCode 稳定错误码
     * @param message   安全错误消息
     */
    public S3RouteException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建带原始原因的受控异常，消息仍为受控文案。
     *
     * @param errorCode 稳定错误码
     * @param message   安全错误消息
     * @param cause     原始原因
     */
    public S3RouteException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

}
