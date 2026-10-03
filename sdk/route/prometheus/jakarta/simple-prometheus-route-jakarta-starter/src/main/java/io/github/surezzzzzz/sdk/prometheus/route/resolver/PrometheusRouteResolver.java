package io.github.surezzzzzz.sdk.prometheus.route.resolver;

/**
 * Prometheus target 精确解析 SPI。
 *
 * @author surezzzzzz
 */
public interface PrometheusRouteResolver {

    /**
     * 解析并校验调用方指定的 target key。
     *
     * @param targetKey 调用方指定的 target key
     * @return 已登记的精确 target key
     */
    String resolveTargetKey(String targetKey);
}
