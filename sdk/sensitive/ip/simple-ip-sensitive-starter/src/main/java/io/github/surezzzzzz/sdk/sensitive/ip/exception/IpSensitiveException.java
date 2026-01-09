package io.github.surezzzzzz.sdk.sensitive.ip.exception;

/**
 * IP 脱敏基础异常
 *
 * @author surezzzzzz
 */
public class IpSensitiveException extends RuntimeException {

    public IpSensitiveException(String message) {
        super(message);
    }

    public IpSensitiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
