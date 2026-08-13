package io.github.surezzzzzz.sdk.prometheus.route.validator;

import io.github.surezzzzzz.sdk.prometheus.route.configuration.SimplePrometheusRouteProperties;

/**
 * Prometheus Route 配置校验 SPI。
 *
 * @author surezzzzzz
 */
public interface PrometheusRoutePropertiesValidator {

    /**
     * 校验启用时的完整 Route 配置。
     *
     * @param properties Route 配置
     */
    void validate(SimplePrometheusRouteProperties properties);
}
