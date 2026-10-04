package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishEnvelope;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishResult;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.support.KafkaPublisherTestHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka Publisher 模型测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaPublisherModelTest {

    @Test
    public void testMessageToStringDoesNotExposeSensitiveFields() {
        KafkaPublishMessage<String> message = KafkaPublisherTestHelper.message();
        message.setHeaders(Collections.singletonMap(KafkaPublisherTestHelper.CUSTOM_HEADER,
                KafkaPublisherTestHelper.CUSTOM_HEADER_VALUE));
        message.setAttributes(Collections.<String, Object>singletonMap("mock-attribute", "mock-attribute-value"));
        String output = message.toString();

        log.info("KafkaPublishMessage toString: {}", output);
        assertFalse(output.contains(KafkaPublisherTestHelper.KEY), "toString 不应包含 key");
        assertFalse(output.contains(KafkaPublisherTestHelper.PAYLOAD), "toString 不应包含 payload");
        assertFalse(output.contains(KafkaPublisherTestHelper.CUSTOM_HEADER_VALUE), "toString 不应包含 header value");
        assertFalse(output.contains("mock-attribute-value"), "toString 不应包含 attributes");
    }

    @Test
    public void testEnvelopeAndResultConstructorsAvailable() {
        KafkaPublishEnvelope<String> envelope = new KafkaPublishEnvelope<>();
        KafkaPublishResult result = new KafkaPublishResult();
        KafkaPublishMessage<String> message = new KafkaPublishMessage<>();

        log.info("无参模型: envelope={}, result={}, message={}", envelope, result, message);
        assertNotNull(envelope, "Envelope 应保留无参构造");
        assertNotNull(result, "Result 应保留无参构造");
        assertNotNull(message, "Message 应保留无参构造");
        assertNull(message.getEnvelopeEnabled(), "envelopeEnabled 默认应为 null，表示跟随配置");
    }

    @Test
    public void testResultToStringDoesNotExposeKey() {
        KafkaPublishResult result = KafkaPublishResult.builder()
                .messageId(KafkaPublisherTestHelper.MESSAGE_ID)
                .topic(KafkaPublisherTestHelper.TOPIC)
                .key(KafkaPublisherTestHelper.KEY)
                .build();
        String output = result.toString();

        log.info("KafkaPublishResult toString: {}", output);
        assertFalse(output.contains(KafkaPublisherTestHelper.KEY), "结果 toString 不应包含 key");
    }
}
