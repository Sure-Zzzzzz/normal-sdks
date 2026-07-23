package io.github.surezzzzzz.sdk.kafka.route.test.cases;

import io.github.surezzzzzz.sdk.kafka.route.constant.ErrorCode;
import io.github.surezzzzzz.sdk.kafka.route.exception.SimpleKafkaRouteException;
import io.github.surezzzzzz.sdk.kafka.route.matcher.KafkaRoutePatternMatcher;
import io.github.surezzzzzz.sdk.kafka.route.model.KafkaRouteRecord;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.resolver.DefaultKafkaRouteResolver;
import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.MockKafkaConsumerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.MockKafkaProducerFactoryFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.factory.RecordingProducerFactory;
import io.github.surezzzzzz.sdk.kafka.route.test.support.KafkaRouteTestDataHelper;
import io.github.surezzzzzz.sdk.kafka.route.validator.DefaultKafkaRoutePropertiesValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka route 显式门面测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaRouteTemplateTest {

    @Test
    public void testSendByTopicRoute() {
        TemplateFixture fixture = fixture();

        fixture.template.send("event.order.created", "mock-value");
        fixture.template.send("audit.order.created", "mock-value");

        assertEquals(1, fixture.producerFactory("event").getRecords().size());
        assertEquals("event.order.created", fixture.producerFactory("event").getRecords().get(0).topic());
        assertEquals(1, fixture.producerFactory("default").getRecords().size());
        assertEquals("audit.order.created", fixture.producerFactory("default").getRecords().get(0).topic());
    }

    @Test
    public void testSendByRouteKeyDoesNotRewriteTopicOrKafkaKey() {
        TemplateFixture fixture = fixture();

        fixture.template.sendByRouteKey("tenant-a", "mock.topic", "record-key", "record-value");

        assertEquals(1, fixture.producerFactory("event").getRecords().size());
        ProducerRecord<Object, Object> record = fixture.producerFactory("event").getRecords().get(0);
        assertEquals("mock.topic", record.topic());
        assertEquals("record-key", record.key());
        assertEquals("record-value", record.value());
        assertEquals(0, fixture.producerFactory("default").getRecords().size());
    }

    @Test
    public void testSendOnBypassesResolver() {
        TemplateFixture fixture = fixture();

        fixture.template.sendOn("default", "event.order.created", "mock-value");

        assertEquals(1, fixture.producerFactory("default").getRecords().size());
        assertEquals(0, fixture.producerFactory("event").getRecords().size());
    }

    @Test
    public void testKafkaRouteRecordPreservesHeadersPartitionAndTimestamp() {
        TemplateFixture fixture = fixture();
        KafkaRouteRecord<String, String> routeRecord = new KafkaRouteRecord<>();
        routeRecord.setRouteKey("tenant-a");
        routeRecord.setTopic("mock.topic");
        routeRecord.setPartition(0);
        routeRecord.setTimestamp(1000L);
        routeRecord.setKey("mock-key");
        routeRecord.setValue("mock-value");
        routeRecord.setHeaders(Collections.singletonList(new RecordHeader("mock-header", new byte[]{1})));

        fixture.template.send(routeRecord);

        ProducerRecord<Object, Object> record = fixture.producerFactory("event").getRecords().get(0);
        assertEquals("mock.topic", record.topic());
        assertEquals(0, record.partition());
        assertEquals(1000L, record.timestamp());
        assertEquals("mock-key", record.key());
        assertEquals("mock-value", record.value());
        assertNotNull(record.headers().lastHeader("mock-header"));
        assertArrayEquals(new byte[]{1}, record.headers().lastHeader("mock-header").value());
    }

    @Test
    public void testExecuteUsesSelectedKafkaTemplate() {
        TemplateFixture fixture = fixture();

        KafkaTemplate<Object, Object> selected = fixture.template.execute("event.order.created", kafkaTemplate -> kafkaTemplate);

        assertSame(fixture.registry.getKafkaTemplate("event"), selected);
    }

    @Test
    public void testInvalidArguments() {
        TemplateFixture fixture = fixture();

        assertErrorCode(ErrorCode.KAFKA_ROUTE_008, () -> fixture.template.send(" ", "value"));
        assertErrorCode(ErrorCode.KAFKA_ROUTE_008, () -> fixture.template.sendByRouteKey(" ", "mock.topic", "value"));
        assertErrorCode(ErrorCode.KAFKA_ROUTE_008, () -> fixture.template.send((ProducerRecord<Object, Object>) null));
        assertErrorCode(ErrorCode.KAFKA_ROUTE_010, () -> fixture.template.execute("mock.topic", null));
        assertErrorCode(ErrorCode.KAFKA_ROUTE_008, () -> fixture.template.send("mock.topic", -1, "key", "value"));
    }

    @Test
    public void testExplicitDatasourceErrorPriority() {
        TemplateFixture fixture = fixture();

        assertErrorCode(ErrorCode.KAFKA_ROUTE_003, () -> fixture.template.sendOn(" ", " ", "value"));
        assertErrorCode(ErrorCode.KAFKA_ROUTE_003, () -> fixture.template.sendOn("missing", " ", "value"));
        assertErrorCode(ErrorCode.KAFKA_ROUTE_003, () -> fixture.template.sendOn(" ", (ProducerRecord<Object, Object>) null));
        assertErrorCode(ErrorCode.KAFKA_ROUTE_003, () -> fixture.template.executeOn(" ", null));
        assertErrorCode(ErrorCode.KAFKA_ROUTE_010, () -> fixture.template.executeOn("default", null));
    }

    private void assertErrorCode(String errorCode, Runnable runnable) {
        SimpleKafkaRouteException exception = assertThrows(SimpleKafkaRouteException.class, runnable::run);
        assertEquals(errorCode, exception.getErrorCode());
    }

    private TemplateFixture fixture() {
        MockKafkaProducerFactoryFactory producerFactoryFactory = new MockKafkaProducerFactoryFactory();
        MockKafkaConsumerFactoryFactory consumerFactoryFactory = new MockKafkaConsumerFactoryFactory();
        SimpleKafkaRouteRegistry registry = new SimpleKafkaRouteRegistry(KafkaRouteTestDataHelper.properties(),
                new DefaultKafkaRoutePropertiesValidator(new KafkaRoutePatternMatcher()),
                producerFactoryFactory,
                consumerFactoryFactory);
        KafkaRouteTemplate template = new KafkaRouteTemplate(registry,
                new DefaultKafkaRouteResolver(KafkaRouteTestDataHelper.properties(), new KafkaRoutePatternMatcher()));
        return new TemplateFixture(template, registry, producerFactoryFactory);
    }

    /**
     * 测试夹具
     */
    private static class TemplateFixture {

        private final KafkaRouteTemplate template;
        private final SimpleKafkaRouteRegistry registry;
        private final MockKafkaProducerFactoryFactory producerFactoryFactory;

        TemplateFixture(KafkaRouteTemplate template,
                        SimpleKafkaRouteRegistry registry,
                        MockKafkaProducerFactoryFactory producerFactoryFactory) {
            this.template = template;
            this.registry = registry;
            this.producerFactoryFactory = producerFactoryFactory;
        }

        RecordingProducerFactory producerFactory(String datasourceKey) {
            return producerFactoryFactory.getFactories().get(datasourceKey);
        }
    }
}
