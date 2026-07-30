package io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 按 topic 分派的消费处理器
 *
 * @author surezzzzzz
 */
public class TopicDispatchingKafkaConsumerHandler implements KafkaConsumerHandler<String, String> {

    private final Map<String, KafkaConsumerHandler<String, String>> handlers;
    private final Map<String, String> registrationIds;

    /**
     * 构造 topic 分派处理器。
     *
     * @param handlers        topic 到处理器的映射
     * @param registrationIds topic 到注册项标识的映射
     */
    public TopicDispatchingKafkaConsumerHandler(Map<String, KafkaConsumerHandler<String, String>> handlers,
                                                Map<String, String> registrationIds) {
        this.handlers = Collections.unmodifiableMap(new LinkedHashMap<>(handlers));
        this.registrationIds = Collections.unmodifiableMap(new LinkedHashMap<>(registrationIds));
    }

    @Override
    public void handle(KafkaConsumerRecord<String, String> record) throws Exception {
        KafkaConsumerHandler<String, String> handler = handlers.get(record.getTopic());
        if (handler == null) {
            throw new IllegalStateException(String.format(ErrorMessage.HANDLER_NOT_FOUND, record.getTopic()));
        }
        handler.handle(record);
    }

    @Override
    public String resolveRegistrationId(String topic) {
        return registrationIds.get(topic);
    }
}
