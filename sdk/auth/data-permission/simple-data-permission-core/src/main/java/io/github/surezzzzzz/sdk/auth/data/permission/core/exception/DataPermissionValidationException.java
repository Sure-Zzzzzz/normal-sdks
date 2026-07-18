package io.github.surezzzzzz.sdk.auth.data.permission.core.exception;

/**
 * 数据权限校验异常。
 *
 * @author surezzzzzz
 */
public class DataPermissionValidationException extends DataPermissionException {

    private static final long serialVersionUID = 1L;

    /**
     * 创建数据权限校验异常。
     *
     * @param errorCode 错误码
     * @param message   错误信息
     */
    public DataPermissionValidationException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 创建携带根因的数据权限校验异常。
     *
     * @param errorCode 错误码
     * @param message   错误信息
     * @param cause     根因
     */
    public DataPermissionValidationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
