package io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency;

/**
 * 空操作幂等检查器，幂等未启用时注入，总是放行。
 *
 * @author surezzzzzz
 */
public class NoOpKafkaConsumerIdempotencyChecker implements KafkaConsumerIdempotencyChecker {

    private static final KafkaConsumerIdempotencyLease LEASE = new KafkaConsumerIdempotencyLease() {
        @Override
        public boolean complete() {
            return true;
        }

        @Override
        public boolean release() {
            return true;
        }
    };

    @Override
    public KafkaConsumerIdempotencyAcquireResult acquire(String messageId, String datasourceKey, String groupId) {
        return KafkaConsumerIdempotencyAcquireResult.acquired(LEASE);
    }
}
