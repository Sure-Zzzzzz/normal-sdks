package io.github.surezzzzzz.sdk.messaging.kafka.consumer.listener;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerEventContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.AnnotationAwareOrderComparator;

import java.util.ArrayList;
import java.util.List;

/**
 * 按顺序分发消费事件的监听器。
 *
 * @author surezzzzzz
 */
@Slf4j
public class CompositeKafkaConsumerEventListener implements KafkaConsumerEventListener {

    private final List<KafkaConsumerEventListener> delegates;

    public CompositeKafkaConsumerEventListener(List<KafkaConsumerEventListener> delegates) {
        this.delegates = new ArrayList<>(delegates);
        AnnotationAwareOrderComparator.sort(this.delegates);
    }

    @Override
    public void onEvent(KafkaConsumerEventContext context) {
        for (KafkaConsumerEventListener delegate : delegates) {
            try {
                delegate.onEvent(context);
            } catch (RuntimeException e) {
                log.warn("消费事件监听器回调异常，忽略：listener=[{}]", delegate.getClass().getName(), e);
            }
        }
    }
}
