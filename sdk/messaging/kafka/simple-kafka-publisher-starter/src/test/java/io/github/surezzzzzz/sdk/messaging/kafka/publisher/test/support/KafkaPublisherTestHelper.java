package io.github.surezzzzzz.sdk.messaging.kafka.publisher.test.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.kafka.route.template.KafkaRouteTemplate;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.configuration.SimpleKafkaPublisherProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.engine.DefaultKafkaPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishRouteKeyResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.resolver.DefaultKafkaPublishTopicResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.serializer.JacksonKafkaPublishSerializer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.support.SendResult;
import org.springframework.util.concurrent.SettableListenableFuture;

import java.util.Collections;

/**
 * Kafka Publisher 测试 Helper
 *
 * @author surezzzzzz
 */
public final class KafkaPublisherTestHelper {

    public static final String TOPIC = "mock.publisher.topic";
    public static final String API_TOPIC = "mock.publisher.api.topic";
    public static final String MESSAGE_TOPIC = "mock.publisher.message.topic";
    public static final String KEY = "mock-key";
    public static final String API_KEY = "mock-api-key";
    public static final String ROUTE_KEY = "mock-route-key";
    public static final String DATASOURCE_KEY = "mock-source";
    public static final String MESSAGE_ID = "mock-message-id";
    public static final String MESSAGE_TYPE = "mock.message.created";
    public static final String TRACE_ID = "mock-trace-id";
    public static final String PAYLOAD = "mock-payload";
    public static final String CUSTOM_HEADER = "mock-header";
    public static final String CUSTOM_HEADER_VALUE = "mock-header-value";
    public static final int PARTITION = 1;
    public static final long OFFSET = 3L;
    public static final long RECORD_TIMESTAMP = 1000L;
    public static final int SERIALIZED_SIZE = 10;
    public static final long FUTURE_TIMEOUT_SECONDS = 3L;
    public static final String ASSERT_RECORD_MESSAGE = "应构造 Kafka ProducerRecord";
    public static final String ASSERT_RESULT_MESSAGE = "应返回 Kafka 发布结果";
    public static final String UTILITY_CLASS_MESSAGE = "Utility class";

    private KafkaPublisherTestHelper() {
        throw new UnsupportedOperationException(UTILITY_CLASS_MESSAGE);
    }

    /**
     * 创建默认属性
     *
     * @return 默认属性
     */
    public static SimpleKafkaPublisherProperties properties() {
        SimpleKafkaPublisherProperties properties = new SimpleKafkaPublisherProperties();
        properties.setAppName("mock-app");
        properties.getEnvelope().setEnable(false);
        return properties;
    }

    /**
     * 创建默认 Publisher
     *
     * @param routeTemplate route 模板
     * @return 默认 Publisher
     */
    public static DefaultKafkaPublisher publisher(KafkaRouteTemplate routeTemplate) {
        SimpleKafkaPublisherProperties properties = properties();
        return new DefaultKafkaPublisher(routeTemplate, properties,
                new JacksonKafkaPublishSerializer(new ObjectMapper()),
                new DefaultKafkaPublishTopicResolver(),
                new DefaultKafkaPublishKeyResolver(),
                new DefaultKafkaPublishRouteKeyResolver(),
                () -> MESSAGE_ID,
                () -> TRACE_ID,
                () -> RECORD_TIMESTAMP,
                Collections.emptyList(),
                Collections.emptyList());
    }

    /**
     * 创建发布消息
     *
     * @return 发布消息
     */
    public static KafkaPublishMessage<String> message() {
        return KafkaPublishMessage.<String>builder()
                .topic(TOPIC)
                .key(KEY)
                .messageId(MESSAGE_ID)
                .messageType(MESSAGE_TYPE)
                .payload(PAYLOAD)
                .envelopeEnabled(false)
                .build();
    }

    /**
     * 创建成功 Future
     *
     * @param record ProducerRecord
     * @return 成功 Future
     */
    public static SettableListenableFuture<SendResult<String, String>> successFuture(
            ProducerRecord<String, String> record) {
        // SB 2.2.x ~ 2.7.9 对应的 kafka-client 2.3.1 / 2.5.1 / 2.6.0 / 3.1.2
        // 均提供 7 参数构造器；第二个 long 是 relativeOffset，不是 timestamp。
        RecordMetadata metadata = new RecordMetadata(new TopicPartition(record.topic(), PARTITION),
                OFFSET, 0L, RECORD_TIMESTAMP, null, SERIALIZED_SIZE, SERIALIZED_SIZE);
        SettableListenableFuture<SendResult<String, String>> future = new SettableListenableFuture<>();
        future.set(new SendResult<>(record, metadata));
        return future;
    }
}
