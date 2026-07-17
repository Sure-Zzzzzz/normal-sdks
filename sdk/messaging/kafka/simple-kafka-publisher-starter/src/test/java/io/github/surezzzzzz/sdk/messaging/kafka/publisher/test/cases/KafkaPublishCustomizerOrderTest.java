package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.customizer.KafkaPublishEnvelopeCustomizer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.customizer.KafkaPublishHeaderCustomizer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.DefaultKafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishRouteKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishTopicResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer.JacksonKafkaPublishSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.support.KafkaPublisherTestHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.core.Ordered;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Kafka 发布自定义器顺序测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaPublishCustomizerOrderTest {

    @Test
    public void testHeaderAndEnvelopeCustomizersUseSpringOrder() throws Exception {
        KafkaRouteTemplate routeTemplate = mock(KafkaRouteTemplate.class);
        java.util.concurrent.atomic.AtomicReference<ProducerRecord<String, String>> recordRef =
                new java.util.concurrent.atomic.AtomicReference<>();
        when(routeTemplate.send(any(ProducerRecord.class))).thenAnswer(invocation -> {
            ProducerRecord<String, String> record = invocation.getArgument(0);
            recordRef.set(record);
            return KafkaPublisherTestHelper.successFuture(record);
        });
        SimpleKafkaPublisherProperties properties = KafkaPublisherTestHelper.properties();
        properties.getEnvelope().setEnable(true);
        KafkaPublishHeaderCustomizer lateHeader = new OrderedHeaderCustomizer(2, "late");
        KafkaPublishHeaderCustomizer earlyHeader = new OrderedHeaderCustomizer(1, "early");
        KafkaPublishEnvelopeCustomizer lateEnvelope = new OrderedEnvelopeCustomizer(2, "late");
        KafkaPublishEnvelopeCustomizer earlyEnvelope = new OrderedEnvelopeCustomizer(1, "early");
        DefaultKafkaPublisher publisher = new DefaultKafkaPublisher(routeTemplate, properties,
                new JacksonKafkaPublishSerializer(),
                new DefaultKafkaPublishTopicResolver(), new DefaultKafkaPublishKeyResolver(),
                new DefaultKafkaPublishRouteKeyResolver(),
                () -> KafkaPublisherTestHelper.MESSAGE_ID,
                () -> KafkaPublisherTestHelper.TRACE_ID,
                () -> KafkaPublisherTestHelper.RECORD_TIMESTAMP,
                Arrays.asList(lateHeader, earlyHeader), Arrays.asList(lateEnvelope, earlyEnvelope));
        io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage<String> message =
                KafkaPublisherTestHelper.message();
        message.setEnvelopeEnabled(true);

        publisher.publish(message).get(KafkaPublisherTestHelper.FUTURE_TIMEOUT_SECONDS,
                java.util.concurrent.TimeUnit.SECONDS);
        ProducerRecord<String, String> record = recordRef.get();

        JsonNode envelope = new ObjectMapper().readTree(record.value());
        log.info("自定义器顺序后的 record value: {}", record.value());
        assertNotNull(record.headers().lastHeader("customizer-order"),
                "最终 record 应包含 customizer-order header");
        assertArrayEquals("early,late".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                record.headers().lastHeader("customizer-order").value(),
                "header customizer 应按 Ordered 从小到大执行");
        assertNotNull(envelope.get("attributes"), "Envelope 应包含 attributes 对象");
        assertEquals("early,late", envelope.get("attributes").get("envelope-order").asText(),
                "envelope customizer 应按 Ordered 从小到大执行");
    }

    @RequiredArgsConstructor
    private static class OrderedHeaderCustomizer implements KafkaPublishHeaderCustomizer, Ordered {
        private final int order;
        private final String value;

        @Override
        public void customize(io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishHeaderContext context) {
            String current = context.getHeaders().get("customizer-order");
            context.getHeaders().put("customizer-order", current == null ? value : current + "," + value);
        }

        @Override
        public int getOrder() {
            return order;
        }
    }

    @RequiredArgsConstructor
    private static class OrderedEnvelopeCustomizer implements KafkaPublishEnvelopeCustomizer, Ordered {
        private final int order;
        private final String value;

        @Override
        public void customize(io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishEnvelopeContext context) {
            Object current = context.getAttributes().get("envelope-order");
            context.getAttributes().put("envelope-order", current == null ? value : current + "," + value);
        }

        @Override
        public int getOrder() {
            return order;
        }
    }
}
