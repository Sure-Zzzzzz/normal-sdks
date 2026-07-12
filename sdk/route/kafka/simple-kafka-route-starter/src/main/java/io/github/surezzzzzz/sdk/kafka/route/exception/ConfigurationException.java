package io.github.surezzzzzz.sdk.kafka.route.exception;

/**
 * Kafka route 配置异常
 *
 * @author surezzzzzz
 */
public class ConfigurationException extends SimpleKafkaRouteException {

    public ConfigurationException(String errorCode, String message) {
        super(errorCode, message);
    }

    public ConfigurationException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
