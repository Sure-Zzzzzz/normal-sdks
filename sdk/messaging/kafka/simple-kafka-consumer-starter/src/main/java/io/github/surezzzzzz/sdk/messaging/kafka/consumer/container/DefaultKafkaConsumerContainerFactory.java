package io.github.surezzzzzz.sdk.messaging.kafka.consumer.container;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerContainerContext;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.*;

/**
 * 默认消费容器工厂
 *
 * @author surezzzzzz
 */
public class DefaultKafkaConsumerContainerFactory implements KafkaConsumerContainerFactory {

    @Override
    public MessageListenerContainer createContainer(KafkaConsumerContainerContext context) {
        ContainerProperties containerProperties = new ContainerProperties(
                context.getTopics().toArray(new String[0]));
        containerProperties.setGroupId(context.getGroupId());
        containerProperties.setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);
        containerProperties.setMessageListener((AcknowledgingMessageListener<String, String>)
                context.getListener()::onManualCommitMessage);
        containerProperties.setShutdownTimeout(context.getShutdownAwaitMs());

        ConsumerFactory<Object, Object> consumerFactory = context.getConsumerFactory();
        ConcurrentMessageListenerContainer<Object, Object> container =
                new ConcurrentMessageListenerContainer<>(consumerFactory, containerProperties);
        container.setConcurrency(context.getConcurrency());
        container.setErrorHandler(new NoAckContainerStoppingErrorHandler());
        container.setAutoStartup(false);
        return container;
    }

    private static final class NoAckContainerStoppingErrorHandler extends ContainerStoppingErrorHandler {

        @Override
        public boolean isAckAfterHandle() {
            return false;
        }
    }
}
