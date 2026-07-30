package io.github.surezzzzzz.sdk.messaging.kafka.consumer.container;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerContainerContext;
import org.springframework.kafka.listener.MessageListenerContainer;

/**
 * 消费容器工厂 SPI，可完全接管容器创建以自定义 ContainerProperties 等
 *
 * @author surezzzzzz
 */
public interface KafkaConsumerContainerFactory {

    /**
     * 创建消费容器
     *
     * @param context 容器创建上下文
     * @return 消费容器
     */
    MessageListenerContainer createContainer(KafkaConsumerContainerContext context);
}
