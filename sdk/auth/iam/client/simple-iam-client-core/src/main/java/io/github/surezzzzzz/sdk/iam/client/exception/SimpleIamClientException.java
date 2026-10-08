package io.github.surezzzzzz.sdk.iam.client.exception;

import lombok.Getter;

/**
 * IAM Client 异常基类（非 2xx 透传 Spring/Feign 标准异常，不进本族）。
 *
 * @author surezzzzzz
 */
@Getter
public class SimpleIamClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String errorCode;

    public SimpleIamClientException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public SimpleIamClientException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }
}
