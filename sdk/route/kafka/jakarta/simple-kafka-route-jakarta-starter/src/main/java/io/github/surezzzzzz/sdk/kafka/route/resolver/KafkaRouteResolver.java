package io.github.surezzzzzz.sdk.kafka.route.resolver;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteContext;

/**
 * Kafka 路由解析器
 *
 * @author surezzzzzz
 */
public interface KafkaRouteResolver {

    String resolveDataSource(KafkaRouteContext context);

    SimpleKafkaRouteProperties.RouteRule resolveRule(KafkaRouteContext context);
}
