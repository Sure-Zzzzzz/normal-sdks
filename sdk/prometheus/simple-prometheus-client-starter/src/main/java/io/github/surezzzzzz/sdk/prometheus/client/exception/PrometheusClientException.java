package io.github.surezzzzzz.sdk.prometheus.client.exception;

import lombok.Getter;

/**
 * Prometheus Client 业务异常（请求参数非法、写入/查询失败、重定向或响应解析失败）。
 *
 * <p>与 Route 层 {@code PrometheusRouteException}（连接/传输/配置层面）边界不同，本异常表达
 * Client 能识别的请求或 Prometheus HTTP/API 业务失败，不吞并或包装 Route 层异常。</p>
 *
 * @author surezzzzzz
 */
@Getter
public class PrometheusClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 稳定错误码。
     */
    private final String errorCode;

    /**
     * 创建 Client 业务异常。
     *
     * @param errorCode 稳定错误码
     * @param message   安全错误消息（不包含原始响应体或 Header）
     */
    public PrometheusClientException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }
}
