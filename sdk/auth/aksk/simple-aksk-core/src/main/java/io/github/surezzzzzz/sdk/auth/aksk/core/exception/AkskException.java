package io.github.surezzzzzz.sdk.auth.aksk.core.exception;

import lombok.Getter;

/**
 * AKSK 相关异常基类
 *
 * @author Sure
 * @since 1.0.0
 */
@Getter
public class AkskException extends RuntimeException {

    private final String errorCode;

    public AkskException(String message) {
        super(message);
        this.errorCode = null;
    }

    public AkskException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = null;
    }

    public AkskException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public AkskException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    @Override
    public String toString() {
        if (errorCode != null) {
            return "AkskException{" +
                    "errorCode='" + errorCode + '\'' +
                    ", message='" + getMessage() + '\'' +
                    '}';
        }
        return super.toString();
    }
}
