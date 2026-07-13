package io.github.surezzzzzz.sdk.lock.redis.exception;

/**
 * Simple Redis Lock 配置异常
 *
 * @author surezzzzzz
 */
public class ConfigurationException extends SimpleRedisLockException {

    private static final long serialVersionUID = 1L;

    public ConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
