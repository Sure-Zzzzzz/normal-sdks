package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.exception.RouteException;
import io.github.surezzzzzz.sdk.kafka.route.matcher.KafkaRoutePatternMatcher;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteRecord;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.resolver.DefaultKafkaRouteResolver;
import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.MockKafkaConsumerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.MockKafkaProducerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteTestDataHelper;
import io.github.surezzzzzz.sdk.kafka.route.validator.DefaultKafkaRoutePropertiesValidator;
import org.apache.kafka.common.header.internals.RecordHeader;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route record 测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaRouteRecordTest {

    @Test
    public void testToStringDoesNotExposePayloadOrHeaders() {
        KafkaRouteRecord<String, String> record = new KafkaRouteRecord<>();
        record.setTopic("mock.topic");
        record.setRouteKey("tenant-a");
        record.setKey("mock-key");
        record.setValue("mock-value");
        record.setHeaders(Collections.singletonList(new RecordHeader("mock-header", new byte[]{1})));

        String text = record.toString();

        assertFalse(text.contains("mock-key"));
        assertFalse(text.contains("mock-value"));
        assertFalse(text.contains("mock-header"));
        assertTrue(text.contains("tenant-a"));
        assertTrue(text.contains("mock.topic"));
    }

    @Test
    public void testBlankRouteKeyDoesNotFallbackToTopicRoute() {
        KafkaRouteRecord<String, String> record = new KafkaRouteRecord<>();
        record.setTopic("event.order.created");
        record.setRouteKey(" ");
        record.setValue("mock-value");

        assertThrows(RouteException.class, () -> template().send(record));
    }

    private KafkaRouteTemplate template() {
        SimpleKafkaRouteRegistry registry = new SimpleKafkaRouteRegistry(KafkaRouteTestDataHelper.properties(),
                new DefaultKafkaRoutePropertiesValidator(new KafkaRoutePatternMatcher()),
                new MockKafkaProducerFactoryFactory(),
                new MockKafkaConsumerFactoryFactory());
        return new KafkaRouteTemplate(registry,
                new DefaultKafkaRouteResolver(KafkaRouteTestDataHelper.properties(), new KafkaRoutePatternMatcher()));
    }
}
