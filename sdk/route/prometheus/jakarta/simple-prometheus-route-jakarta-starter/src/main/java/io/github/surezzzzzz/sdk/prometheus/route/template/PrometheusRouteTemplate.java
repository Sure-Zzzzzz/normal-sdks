package io.github.surezzzzzz.sdk.prometheus.route.template;

import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.prometheus.route.exception.PrometheusRouteException;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteRequest;
import io.github.surezzzzzz.sdk.prometheus.route.model.PrometheusRouteResponse;
import io.github.surezzzzzz.sdk.prometheus.route.registry.SimplePrometheusRouteRegistry;
import io.github.surezzzzzz.sdk.prometheus.route.resolver.PrometheusRouteResolver;

/**
 * Prometheus Route 唯一同步 HTTP 门面。
 *
 * @author surezzzzzz
 */
public class PrometheusRouteTemplate {

    private final SimplePrometheusRouteRegistry registry;
    private final PrometheusRouteResolver resolver;

    /**
     * 创建 Route 同步门面。
     *
     * @param registry target 资源注册表
     * @param resolver target 精确解析器
     */
    public PrometheusRouteTemplate(SimplePrometheusRouteRegistry registry, PrometheusRouteResolver resolver) {
        this.registry = registry;
        this.resolver = resolver;
    }

    /**
     * 向已登记 target 发送结构化同步请求。
     *
     * @param targetKey 已登记 target key
     * @param request   Route 请求
     * @return 不携带底层连接资源的响应快照
     * @throws PrometheusRouteException target、请求或 Route 状态不符合约束时抛出
     */
    public PrometheusRouteResponse exchange(String targetKey, PrometheusRouteRequest request) {
        if (request == null) {
            throw new PrometheusRouteException(ErrorCode.REQUEST_ILLEGAL, ErrorMessage.REQUEST_ILLEGAL);
        }
        return registry.exchange(resolver.resolveTargetKey(targetKey), request);
    }
}
