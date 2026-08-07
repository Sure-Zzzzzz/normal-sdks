package io.github.surezzzzzz.sdk.auth.resource.core.exception;

import lombok.Getter;

/**
 * 资源认证异常。
 *
 * @author surezzzzzz
 */
@Getter
public class ResourceAuthenticationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码。
     */
    private final String errorCode;

    /**
     * 创建资源认证异常。
     *
     * @param errorCode 错误码
     * @param message   安全错误信息
     */
    public ResourceAuthenticationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建资源认证异常。
     *
     * @param errorCode 错误码
     * @param message   安全错误信息
     * @param cause     根因
     */
    public ResourceAuthenticationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
