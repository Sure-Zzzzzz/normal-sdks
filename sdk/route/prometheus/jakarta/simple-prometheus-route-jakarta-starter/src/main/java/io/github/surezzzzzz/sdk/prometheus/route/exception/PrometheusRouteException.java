package io.github.surezzzzzz.sdk.prometheus.route.exception;

import lombok.Getter;

/**
 * Prometheus Route 受控异常。
 *
 * @author surezzzzzz
 */
@Getter
public class PrometheusRouteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 稳定错误码。
     */
    private final String errorCode;

    /**
     * 创建受控异常。
     *
     * @param errorCode 稳定错误码
     * @param message   安全错误消息
     */
    public PrometheusRouteException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

}
