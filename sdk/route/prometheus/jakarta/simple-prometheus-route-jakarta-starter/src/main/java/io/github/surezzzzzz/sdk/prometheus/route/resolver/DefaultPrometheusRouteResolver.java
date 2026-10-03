package io.github.surezzzzzz.sdk.prometheus.route.resolver;

import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.prometheus.route.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.prometheus.route.exception.PrometheusRouteException;
import io.github.surezzzzzz.sdk.prometheus.route.registry.SimplePrometheusRouteRegistry;

/**
 * 默认精确 target 解析器。
 *
 * @author surezzzzzz
 */
public class DefaultPrometheusRouteResolver implements PrometheusRouteResolver {

    private final SimplePrometheusRouteRegistry registry;

    public DefaultPrometheusRouteResolver(SimplePrometheusRouteRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String resolveTargetKey(String targetKey) {
        registry.assertOpen();
        if (targetKey == null || targetKey.trim().isEmpty()) {
            throw new PrometheusRouteException(ErrorCode.TARGET_KEY_ILLEGAL, ErrorMessage.TARGET_KEY_ILLEGAL);
        }
        if (!registry.contains(targetKey)) {
            throw new PrometheusRouteException(ErrorCode.TARGET_NOT_REGISTERED,
                    ErrorMessage.TARGET_NOT_REGISTERED);
        }
        return targetKey;
    }
}
