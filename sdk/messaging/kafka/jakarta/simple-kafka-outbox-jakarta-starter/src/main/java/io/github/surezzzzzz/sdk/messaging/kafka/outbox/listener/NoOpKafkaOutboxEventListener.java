package io.github.surezzzzzz.sdk.messaging.kafka.outbox.listener;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.annotation.SimpleKafkaOutboxComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxCleanupContext;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxEventContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;

/**
 * 默认空操作 Kafka Outbox Listener
 *
 * @author surezzzzzz
 */
@SimpleKafkaOutboxComponent
@ConditionalOnMissingBean(KafkaOutboxEventListener.class)
public class NoOpKafkaOutboxEventListener implements KafkaOutboxEventListener {
    /**
     * {@inheritDoc}
     */
    @Override
    public void onSaved(OutboxEventContext context) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClaimed(OutboxEventContext context) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSent(OutboxEventContext context) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRetry(OutboxEventContext context) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onPoison(OutboxEventContext context) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onLeaseLost(OutboxEventContext context) {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCleanup(OutboxCleanupContext context) {
    }
}
