package io.github.surezzzzzz.sdk.redis.route.exception;

/**
 * Redis route 配置异常
 *
 * @author surezzzzzz
 */
public class ConfigurationException extends SimpleRedisRouteException {

    public ConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
