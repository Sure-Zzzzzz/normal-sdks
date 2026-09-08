package io.github.surezzzzzz.sdk.auth.iam.core.exception;

import lombok.Getter;

/**
 * IAM协议异常。
 *
 * @author surezzzzzz
 */
@Getter
public class IamProtocolException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码。
     */
    private final String errorCode;

    /**
     * 创建IAM协议异常。
     *
     * @param errorCode 错误码
     * @param message   安全错误信息
     */
    public IamProtocolException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
