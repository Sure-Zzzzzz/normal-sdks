package io.github.surezzzzzz.sdk.audit.http.xff.exception;

import lombok.Getter;

/**
 * XFF Capture 审计基础异常。
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleXffCaptureAuditException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码。
     */
    private final String errorCode;

    /**
     * 创建审计异常。
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public SimpleXffCaptureAuditException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建包含原因的审计异常。
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param cause     原因
     */
    public SimpleXffCaptureAuditException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
