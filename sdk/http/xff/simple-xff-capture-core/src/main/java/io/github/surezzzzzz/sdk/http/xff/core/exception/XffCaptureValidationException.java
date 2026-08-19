package io.github.surezzzzzz.sdk.http.xff.core.exception;

/**
 * XFF Capture 契约校验异常。
 *
 * @author surezzzzzz
 */
public class XffCaptureValidationException extends SimpleXffCaptureCoreException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建契约校验异常。
     *
     * @param errorCode 错误码
     * @param message   错误消息
     */
    public XffCaptureValidationException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 创建包含原因的契约校验异常。
     *
     * @param errorCode 错误码
     * @param message   错误消息
     * @param cause     原因
     */
    public XffCaptureValidationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
