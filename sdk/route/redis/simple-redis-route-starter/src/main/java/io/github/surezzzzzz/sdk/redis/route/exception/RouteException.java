package io.github.surezzzzzz.sdk.redis.route.exception;

/**
 * Redis route 路由异常
 *
 * @author surezzzzzz
 */
public class RouteException extends SimpleRedisRouteException {

    /**
     * 创建不带原因异常的路由异常。
     *
     * @param errorCode 错误码
     * @param message   安全错误说明
     */
    public RouteException(String errorCode, String message) {
        super(errorCode, message);
    }

    /**
     * 创建带原因异常的路由异常。
     *
     * @param errorCode 错误码
     * @param message   安全错误说明
     * @param cause     原始异常
     */
    public RouteException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
