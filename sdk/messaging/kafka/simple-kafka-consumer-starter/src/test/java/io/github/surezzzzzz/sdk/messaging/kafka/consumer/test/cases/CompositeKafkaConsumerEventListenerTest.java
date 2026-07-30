package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.listener.CompositeKafkaConsumerEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.listener.KafkaConsumerEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerEventContext;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.Order;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 组合消费事件监听器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class CompositeKafkaConsumerEventListenerTest {

    @Test
    public void testOrderedDelegatesContinueAfterFailure() {
        List<String> calls = new ArrayList<>();
        KafkaConsumerEventListener listener = new CompositeKafkaConsumerEventListener(Arrays.asList(
                new LateListener(calls), new FailingListener(calls), new EarlyListener(calls)));
        KafkaConsumerEventContext context = KafkaConsumerEventContext.builder().messageId("mock-message").build();

        listener.onEvent(context);
        log.info("组合监听器调用顺序：{}", calls);

        assertEquals(Arrays.asList("early", "failing", "late"), calls);
    }

    @Test
    public void testEmptyDelegatesDoNothing() {
        new CompositeKafkaConsumerEventListener(Collections.emptyList())
                .onEvent(KafkaConsumerEventContext.builder().messageId("mock-message").build());
    }

    @Order(1)
    private static class EarlyListener implements KafkaConsumerEventListener {

        private final List<String> calls;

        private EarlyListener(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void onEvent(KafkaConsumerEventContext context) {
            calls.add("early");
        }
    }

    @Order(2)
    private static class FailingListener implements KafkaConsumerEventListener {

        private final List<String> calls;

        private FailingListener(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void onEvent(KafkaConsumerEventContext context) {
            calls.add("failing");
            throw new IllegalStateException("mock listener failure");
        }
    }

    @Order(3)
    private static class LateListener implements KafkaConsumerEventListener {

        private final List<String> calls;

        private LateListener(List<String> calls) {
            this.calls = calls;
        }

        @Override
        public void onEvent(KafkaConsumerEventContext context) {
            calls.add("late");
        }
    }
}
