package io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.exception;

import lombok.Getter;

/**
 * 所属人授权协作基础异常。
 *
 * @author surezzzzzz
 */
@Getter
public class OwnerAuthorizationCollaborationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码。
     */
    private final String errorCode;

    /**
     * 创建所属人授权协作异常。
     *
     * @param errorCode 错误码
     * @param message   错误信息
     */
    public OwnerAuthorizationCollaborationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * 创建携带根因的所属人授权协作异常。
     *
     * @param errorCode 错误码
     * @param message   错误信息
     * @param cause     根因
     */
    public OwnerAuthorizationCollaborationException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
