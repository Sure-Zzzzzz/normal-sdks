package io.github.surezzzzzz.sdk.auth.authorization.application.core.exception;

import lombok.Getter;

/**
 * 应用授权基础异常。
 *
 * @author surezzzzzz
 */
@Getter
public class ApplicationAuthorizationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码。
     */
    private final String errorCode;

    /**
     * 创建应用授权异常。
     *
     * @param errorCode 错误码
     * @param message   错误信息
     */
    public ApplicationAuthorizationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建携带根因的应用授权异常。
     *
     * @param errorCode 错误码
     * @param message   错误信息
     * @param cause     根因
     */
    public ApplicationAuthorizationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
