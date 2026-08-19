package io.github.surezzzzzz.sdk.http.xff.core.exception;

import lombok.Getter;

/**
 * Simple XFF Capture Core 基础异常。
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleXffCaptureCoreException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码。
     */
    private final String errorCode;

    /**
     * 创建 Core 异常。
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public SimpleXffCaptureCoreException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建包含原因的 Core 异常。
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param cause     原因
     */
    public SimpleXffCaptureCoreException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
