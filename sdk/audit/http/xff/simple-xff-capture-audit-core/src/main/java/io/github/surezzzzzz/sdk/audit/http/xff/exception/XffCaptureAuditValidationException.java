package io.github.surezzzzzz.sdk.audit.http.xff.exception;

/**
 * XFF Capture 审计校验异常。
 *
 * @author surezzzzzz
 */
public class XffCaptureAuditValidationException extends SimpleXffCaptureAuditException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建校验异常。
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public XffCaptureAuditValidationException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 创建包含原因的校验异常。
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param cause     原因
     */
    public XffCaptureAuditValidationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
