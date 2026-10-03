package io.github.surezzzzzz.sdk.kafka.route.exception;

/**
 * Kafka route 路由异常
 *
 * @author surezzzzzz
 */
public class RouteException extends SimpleKafkaRouteException {

    public RouteException(String errorCode, String message) {
        super(errorCode, message);
    }

    public RouteException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
