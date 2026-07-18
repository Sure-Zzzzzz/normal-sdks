package io.github.surezzzzzz.sdk.auth.data.permission.core.exception;

import lombok.Getter;

/**
 * 数据权限基础异常。
 *
 * @author surezzzzzz
 */
@Getter
public class DataPermissionException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码。
     */
    private final String errorCode;

    /**
     * 创建数据权限异常。
     *
     * @param errorCode 错误码
     * @param message   错误信息
     */
    public DataPermissionException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建携带根因的数据权限异常。
     *
     * @param errorCode 错误码
     * @param message   错误信息
     * @param cause     根因
     */
    public DataPermissionException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
