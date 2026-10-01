package io.github.surezzzzzz.sdk.redis.route.exception;

/**
 * Redis route 配置异常
 *
 * @author surezzzzzz
 */
public class ConfigurationException extends SimpleRedisRouteException {

    /**
     * 创建不带原因异常的配置异常。
     *
     * @param errorCode 错误码
     * @param message   安全错误说明
     */
    public ConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 创建带原因异常的配置异常。
     *
     * @param errorCode 错误码
     * @param message   安全错误说明
     * @param cause     原始异常
     */
    public ConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
