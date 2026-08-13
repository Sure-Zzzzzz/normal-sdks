package io.github.surezzzzzz.sdk.prometheus.route.annotation;

import java.lang.annotation.*;

/**
 * Prometheus Route 内部组件扫描标记。
 *
 * @author surezzzzzz
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface SimplePrometheusRouteComponent {
}
