package io.github.surezzzzzz.sdk.auth.iam.resource.core.exception;

import lombok.Getter;

/**
 * IAM资源认证异常。
 *
 * @author surezzzzzz
 */
@Getter
public class IamResourceAuthenticationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码。
     */
    private final String errorCode;

    /**
     * 创建IAM资源认证异常。
     *
     * @param errorCode 错误码
     * @param message   安全错误信息
     */
    public IamResourceAuthenticationException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
