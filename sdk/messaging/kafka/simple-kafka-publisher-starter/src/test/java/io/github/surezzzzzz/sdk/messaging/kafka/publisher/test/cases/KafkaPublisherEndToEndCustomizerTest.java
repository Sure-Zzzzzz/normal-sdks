package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.customizer.KafkaPublishEnvelopeCustomizer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.customizer.KafkaPublishHeaderCustomizer;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.KafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.SimpleKafkaPublisherTestApplication;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.support.KafkaPublisherEndToEndHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Kafka Publisher customizer 端到端测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = {SimpleKafkaPublisherTestApplication.class,
        KafkaPublisherEndToEndCustomizerTest.CustomizerE2eConfiguration.class})
public class KafkaPublisherEndToEndCustomizerTest {

    @Autowired
    private KafkaPublisher publisher;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testHeaderAndEnvelopeCustomizersTakeEffectEndToEnd() throws Exception {
        String suffix = KafkaPublisherEndToEndHelper.suffix();
        String topic = KafkaPublisherEndToEndHelper.topic(
                KafkaPublisherEndToEndHelper.TOPIC_V37_ROUTE_PREFIX, suffix);
        String key = KafkaPublisherEndToEndHelper.key(suffix + "-customizer");
        KafkaPublisherEndToEndHelper.createTopic(KafkaPublisherEndToEndHelper.V37_BOOTSTRAP_SERVERS,
                topic, KafkaPublisherEndToEndHelper.SINGLE_PARTITION_COUNT,
                KafkaPublisherEndToEndHelper.SINGLE_REPLICATION_FACTOR);
        KafkaPublishMessage<String> message = message(topic, key, "message-customizer-" + suffix);

        publisher.publish(message).get(KafkaPublisherEndToEndHelper.SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        ConsumerRecord<String, String> record = KafkaPublisherEndToEndHelper.consumeRecord(
                KafkaPublisherEndToEndHelper.V37_BOOTSTRAP_SERVERS, topic, key,
                KafkaPublisherEndToEndHelper.CONSUME_TIMEOUT_MS);
        assertNotNull(record, "应消费到 customizer 测试消息");
        JsonNode envelope = objectMapper.readTree(record.value());

        org.apache.kafka.common.header.Header customHeader =
                record.headers().lastHeader("x-e2e-custom-header");
        assertNotNull(customHeader, "消费端应收到 header customizer 添加的 header");
        log.info("customizer E2E header 值: {}", new String(customHeader.value(), StandardCharsets.UTF_8));
        log.info("customizer E2E envelope: {}", record.value());
        assertArrayEquals("custom-header-value".getBytes(StandardCharsets.UTF_8), customHeader.value(),
                "header customizer 添加的 header 应到达消费端");
        assertNotNull(envelope.get("attributes"),
                "消费端 envelope 应包含 attributes 对象");
        assertNotNull(envelope.get("attributes").get("e2eAttribute"),
                "消费端 envelope attributes 应包含 e2eAttribute");
        assertEquals("custom-attribute-value", envelope.get("attributes").get("e2eAttribute").asText(),
                "envelope customizer 补充的 attribute 应进入消费端 envelope attributes JSON");
    }

    private KafkaPublishMessage<String> message(String topic, String key, String messageId) {
        return KafkaPublishMessage.<String>builder()
                .topic(topic)
                .key(key)
                .messageId(messageId)
                .messageType(KafkaPublisherEndToEndHelper.MESSAGE_TYPE)
                .payload(KafkaPublisherEndToEndHelper.PAYLOAD)
                .build();
    }

    @TestConfiguration
    static class CustomizerE2eConfiguration {

        @Bean
        public KafkaPublishHeaderCustomizer e2eHeaderCustomizer() {
            return new E2eHeaderCustomizer();
        }

        @Bean
        public KafkaPublishEnvelopeCustomizer e2eEnvelopeCustomizer() {
            return new E2eEnvelopeCustomizer();
        }
    }

    private static class E2eHeaderCustomizer implements KafkaPublishHeaderCustomizer, Ordered {

        @Override
        public void customize(io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishHeaderContext context) {
            context.getHeaders().put("x-e2e-custom-header", "custom-header-value");
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }

    private static class E2eEnvelopeCustomizer implements KafkaPublishEnvelopeCustomizer, Ordered {

        @Override
        public void customize(io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishEnvelopeContext context) {
            context.getAttributes().put("e2eAttribute", "custom-attribute-value");
        }

        @Override
        public int getOrder() {
            return Ordered.HIGHEST_PRECEDENCE;
        }
    }
}
