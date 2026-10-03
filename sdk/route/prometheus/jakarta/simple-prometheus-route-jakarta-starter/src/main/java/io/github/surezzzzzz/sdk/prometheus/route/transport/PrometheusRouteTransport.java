package io.github.surezzzzzz.sdk.prometheus.route.transport;

import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteRequest;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteResponse;

import java.io.IOException;

/**
 * Route 内部 target transport 契约。
 *
 * @author surezzzzzz
 */
public interface PrometheusRouteTransport extends AutoCloseable {

    /**
     * 执行请求并返回响应快照。
     *
     * @param request Route 请求
     * @return 响应快照
     */
    PrometheusRouteResponse exchange(PrometheusRouteRequest request);

    @Override
    void close() throws IOException;
}
