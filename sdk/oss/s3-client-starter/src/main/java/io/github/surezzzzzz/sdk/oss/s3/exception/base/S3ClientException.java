package io.github.surezzzzzz.sdk.oss.s3.exception.base;

import lombok.Getter;

/**
 * S3客户端异常基类
 */
@Getter
public class S3ClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final String errorCode;

    /**
     * 构造客户端异常
     *
     * @param errorCode 错误码
     * @param message   错误信息
     */
    public S3ClientException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 构造客户端异常
     *
     * @param errorCode 错误码
     * @param message   错误信息
     * @param cause     原始异常
     */
    public S3ClientException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}