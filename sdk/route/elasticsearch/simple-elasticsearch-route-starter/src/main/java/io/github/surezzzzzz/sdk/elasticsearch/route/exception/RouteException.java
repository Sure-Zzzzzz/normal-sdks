package io.github.surezzzzzz.sdk.elasticsearch.route.exception;

/**
 * 路由异常
 *
 * @author surezzzzzz
 */
public class RouteException extends SimpleElasticsearchRouteException {

    public RouteException(String errorCode, String message) {
        super(errorCode, message);
    }

    public RouteException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
