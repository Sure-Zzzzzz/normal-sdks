package io.github.surezzzzzz.sdk.prometheus.route.transport;

import io.github.surezzzzzz.sdk.prometheus.route.configuration.SimplePrometheusRouteProperties;

/**
 * target 私有 transport 创建 SPI。
 *
 * @author surezzzzzz
 */
public interface PrometheusRouteTransportFactory {

    /**
     * 创建指定 target 的私有 transport。
     *
     * @param targetKey target key
     * @param config    target 配置
     * @return 私有 transport
     */
    PrometheusRouteTransport create(String targetKey, SimplePrometheusRouteProperties.TargetConfig config);
}
