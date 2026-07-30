package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.container.DefaultKafkaConsumerContainerFactory;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler.KafkaConsumerHandlerAdapter;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerContainerContext;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.AcknowledgingMessageListener;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.support.Acknowledgment;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 默认消费容器工厂测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultKafkaConsumerContainerFactoryTest {

    @Test
    public void testManualCommitContainerBindsAcknowledgingListener() {
        KafkaConsumerHandlerAdapter adapter = mock(KafkaConsumerHandlerAdapter.class);
        ConsumerFactory<Object, Object> consumerFactory = mock(ConsumerFactory.class);
        KafkaConsumerContainerContext context = context(adapter, consumerFactory, false);

        ConcurrentMessageListenerContainer<Object, Object> container = (ConcurrentMessageListenerContainer<Object, Object>)
                new DefaultKafkaConsumerContainerFactory().createContainer(context);
        ContainerProperties properties = container.getContainerProperties();
        ConsumerRecord<String, String> record = new ConsumerRecord<>("mock-topic-a", 1, 9L, "mock-key", "mock-value");
        Acknowledgment acknowledgment = mock(Acknowledgment.class);
        ((AcknowledgingMessageListener<String, String>) properties.getMessageListener()).onMessage(record, acknowledgment);
        log.info("手动提交容器：groupId={}，topics={}，ackMode={}，concurrency={}，shutdownTimeout={}",
                properties.getGroupId(), Arrays.toString(properties.getTopics()), properties.getAckMode(),
                container.getConcurrency(), properties.getShutdownTimeout());

        assertEquals("mock-group", properties.getGroupId());
        assertEquals(Arrays.asList("mock-topic-a", "mock-topic-b"), Arrays.asList(properties.getTopics()));
        assertEquals(ContainerProperties.AckMode.MANUAL_IMMEDIATE, properties.getAckMode());
        assertFalse(container.getGenericErrorHandler().isAckAfterHandle(),
                "消费异常停止容器时不得由 Spring Kafka 自动确认 source record");
        assertEquals(2, container.getConcurrency());
        assertEquals(1234L, properties.getShutdownTimeout());
        assertFalse(container.isAutoStartup());
        verify(adapter).onManualCommitMessage(record, acknowledgment);
    }

    private KafkaConsumerContainerContext context(KafkaConsumerHandlerAdapter adapter,
                                                  ConsumerFactory<Object, Object> consumerFactory,
                                                  boolean enableAutoCommit) {
        return KafkaConsumerContainerContext.builder()
                .datasourceKey("mock-datasource")
                .groupId("mock-group")
                .topics(Arrays.asList("mock-topic-a", "mock-topic-b"))
                .autoOffsetReset("earliest")
                .enableAutoCommit(enableAutoCommit)
                .maxPollRecords(100)
                .concurrency(2)
                .shutdownAwaitMs(1234L)
                .listener(adapter)
                .consumerFactory(consumerFactory)
                .build();
    }
}
