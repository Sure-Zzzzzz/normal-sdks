package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteProperties;
import io.github.surezzzzzz.sdk.kafka.route.constant.RouteMatchType;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.mock.env.MockPropertySource;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route 配置绑定测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleKafkaRoutePropertiesBindingTest {

    @Test
    public void testYamlStylePropertiesBindToNestedFields() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MockPropertySource()
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.enable", "true")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.default-source", "default")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.sources.default.bootstrap-servers[0]", "127.0.0.1:9092")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.sources.default.client-id", "default-client")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.sources.default.producer.client-id", "default-producer")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.sources.default.producer.acks", "all")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.sources.default.producer.transaction-id-prefix", "default-tx-")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.sources.default.producer.properties[partitioner.class]", "mock.Partitioner")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.sources.default.consumer.group-id", "default-group")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.sources.default.consumer.enable-auto-commit", "false")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.sources.default.security.security-protocol", "sasl_ssl")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.sources.default.security.sasl-mechanism", "PLAIN")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.diagnostics.enable", "false")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.diagnostics.fail-fast", "true")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.diagnostics.timeout-ms", "8000")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.rules[0].pattern", "event.")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.rules[0].type", "prefix")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.rules[0].datasource", "event")
                .withProperty("io.github.surezzzzzz.sdk.kafka.route.rules[0].priority", "1"));

        SimpleKafkaRouteProperties properties = Binder.get(environment)
                .bind("io.github.surezzzzzz.sdk.kafka.route", SimpleKafkaRouteProperties.class)
                .orElseGet(SimpleKafkaRouteProperties::new);
        log.info("配置绑定结果 defaultSource={}, sources={}, rules={}, diagnostics={}",
                properties.getDefaultSource(), properties.getSources().keySet(),
                properties.getRules(), properties.getDiagnostics());

        assertTrue(properties.isEnable());
        assertEquals("default", properties.getDefaultSource());
        SimpleKafkaRouteProperties.DataSourceConfig defaultSource = properties.getSources().get("default");
        assertNotNull(defaultSource);
        assertEquals(Collections.singletonList("127.0.0.1:9092"), defaultSource.getBootstrapServers());
        assertEquals("default-client", defaultSource.getClientId());
        assertEquals("default-producer", defaultSource.getProducer().getClientId());
        assertEquals("all", defaultSource.getProducer().getAcks());
        assertEquals("default-tx-", defaultSource.getProducer().getTransactionIdPrefix());
        assertEquals("mock.Partitioner", defaultSource.getProducer().getProperties().get("partitioner.class"));
        assertEquals("default-group", defaultSource.getConsumer().getGroupId());
        assertFalse(defaultSource.getConsumer().getEnableAutoCommit());
        assertEquals("sasl_ssl", defaultSource.getSecurity().getSecurityProtocol());
        assertEquals("PLAIN", defaultSource.getSecurity().getSaslMechanism());
        assertFalse(properties.getDiagnostics().isEnable());
        assertTrue(properties.getDiagnostics().isFailFast());
        assertEquals(8000L, properties.getDiagnostics().getTimeoutMs());
        assertEquals(1, properties.getRules().size());
        SimpleKafkaRouteProperties.RouteRule rule = properties.getRules().get(0);
        assertEquals("event.", rule.getPattern());
        assertEquals(RouteMatchType.PREFIX.getCode(), rule.getType());
        assertEquals("event", rule.getDatasource());
        assertEquals(1, rule.getPriority());
    }
}
