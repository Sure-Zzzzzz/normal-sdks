package io.github.surezzzzzz.sdk.redis.route.exception;

/**
 * Redis route 路由异常
 *
 * @author surezzzzzz
 */
public class RouteException extends SimpleRedisRouteException {

    public RouteException(String errorCode, String message) {
        super(errorCode, message);
    }

    public RouteException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
