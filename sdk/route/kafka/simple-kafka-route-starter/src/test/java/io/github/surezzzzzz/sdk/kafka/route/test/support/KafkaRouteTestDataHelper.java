package io.github.surezzzzzz.sdk.kafka.route.test.support;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.RouteMatchType;

import java.util.Arrays;

/**
 * Kafka route 测试数据 Helper
 *
 * @author surezzzzzz
 */
public final class KafkaRouteTestDataHelper {

    private KafkaRouteTestDataHelper() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static SimpleKafkaRouteProperties properties() {
        SimpleKafkaRouteProperties properties = new SimpleKafkaRouteProperties();
        properties.setEnable(true);
        properties.setDefaultSource("default");
        properties.getSources().put("default", source("default-client"));
        properties.getSources().put("event", source("event-client"));
        properties.getRules().add(rule("event.", RouteMatchType.PREFIX.getCode(), "event", 1));
        properties.getRules().add(rule("tenant-*", RouteMatchType.WILDCARD.getCode(), "event", 2));
        return properties;
    }

    public static SimpleKafkaRouteProperties.DataSourceConfig source(String clientId) {
        SimpleKafkaRouteProperties.DataSourceConfig config = new SimpleKafkaRouteProperties.DataSourceConfig();
        config.setBootstrapServers(Arrays.asList("127.0.0.1:9092"));
        config.setClientId(clientId);
        config.getProducer().setClientId(clientId + "-producer");
        config.getConsumer().setClientId(clientId + "-consumer");
        return config;
    }

    public static SimpleKafkaRouteProperties.RouteRule rule(String pattern, String type,
                                                            String datasource, int priority) {
        SimpleKafkaRouteProperties.RouteRule rule = new SimpleKafkaRouteProperties.RouteRule();
        rule.setPattern(pattern);
        rule.setType(type);
        rule.setDatasource(datasource);
        rule.setPriority(priority);
        return rule;
    }
}
