package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ConsumerEventType;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler.KafkaConsumerHandler;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler.KafkaConsumerHandlerAdapter;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler.TopicDispatchingKafkaConsumerHandler;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.NoOpKafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.listener.KafkaConsumerEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerEventContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * topic 分派消费处理器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class TopicDispatchingKafkaConsumerHandlerTest {

    @Test
    public void testRegistrationIdFlowsToHandlerAndEventContext() {
        String topic = "mock.topic";
        String registrationId = "mockConsumer#handle";
        AtomicReference<String> handlerRegistrationId = new AtomicReference<>();
        AtomicReference<KafkaConsumerEventContext> eventContext = new AtomicReference<>();
        KafkaConsumerHandler<String, String> handler = record -> handlerRegistrationId.set(record.getRegistrationId());
        Map<String, KafkaConsumerHandler<String, String>> handlers = new LinkedHashMap<>();
        handlers.put(topic, handler);
        Map<String, String> registrationIds = new LinkedHashMap<>();
        registrationIds.put(topic, registrationId);
        TopicDispatchingKafkaConsumerHandler dispatcher =
                new TopicDispatchingKafkaConsumerHandler(handlers, registrationIds);
        KafkaConsumerEventListener eventListener = eventContext::set;
        KafkaConsumerHandlerAdapter adapter = new KafkaConsumerHandlerAdapter(dispatcher,
                new NoOpKafkaConsumerIdempotencyChecker(), (record, cause, attempt) -> null,
                (record, cause, attempt, errorCode) -> false, eventListener, "mock-datasource");

        adapter.onManualCommitMessage(new ConsumerRecord<>(topic, 1, 9L, "mock-key", "mock-value"),
                org.mockito.Mockito.mock(Acknowledgment.class));
        KafkaConsumerEventContext actualEvent = eventContext.get();
        log.info("消费事件上下文：registrationId={}，eventType={}，topic={}，offset={}",
                actualEvent.getRegistrationId(), actualEvent.getEventType(), actualEvent.getTopic(), actualEvent.getOffset());

        assertEquals(registrationId, handlerRegistrationId.get());
        assertEquals(registrationId, actualEvent.getRegistrationId());
        assertEquals(ConsumerEventType.CONSUMED, actualEvent.getEventType());
        assertEquals(topic, actualEvent.getTopic());
        assertEquals(1, actualEvent.getPartition());
        assertEquals(9L, actualEvent.getOffset());
    }
}
