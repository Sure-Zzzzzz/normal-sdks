package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.configuration.SimpleKafkaRouteConfiguration;
import io.github.surezzzzzz.sdk.kafka.route.diagnostic.KafkaRouteDiagnostics;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route 无 broker 启动测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaRouteNoBrokerStartupTest {

    @Test
    public void testContextStartsWithoutReachableBroker() {
        new ApplicationContextRunner()
                .withUserConfiguration(SimpleKafkaRouteConfiguration.class)
                .withPropertyValues(
                        "io.github.surezzzzzz.sdk.kafka.route.enable=true",
                        "io.github.surezzzzzz.sdk.kafka.route.default-source=default",
                        "io.github.surezzzzzz.sdk.kafka.route.sources.default.bootstrap-servers[0]=127.0.0.1:65535",
                        "io.github.surezzzzzz.sdk.kafka.route.sources.default.producer.client-id=no-broker-producer",
                        "io.github.surezzzzzz.sdk.kafka.route.sources.default.consumer.client-id=no-broker-consumer",
                        "io.github.surezzzzzz.sdk.kafka.route.diagnostics.enable=false"
                )
                .run(context -> {
                    log.info("无 broker 启动结果 startupFailure={}", context.getStartupFailure());
                    assertNull(context.getStartupFailure());
                    assertNotNull(context.getBean(SimpleKafkaRouteRegistry.class));
                    assertNotNull(context.getBean(KafkaRouteTemplate.class));
                    assertNotNull(context.getBean(KafkaRouteDiagnostics.class));
                    assertEquals(0, context.getBeansOfType(ProducerFactory.class).size());
                    assertEquals(0, context.getBeansOfType(ConsumerFactory.class).size());
                    assertEquals(0, context.getBeansOfType(KafkaTemplate.class).size());
                    assertTrue(context.getBean(SimpleKafkaRouteRegistry.class).containsDatasource("default"));
                });
    }
}
