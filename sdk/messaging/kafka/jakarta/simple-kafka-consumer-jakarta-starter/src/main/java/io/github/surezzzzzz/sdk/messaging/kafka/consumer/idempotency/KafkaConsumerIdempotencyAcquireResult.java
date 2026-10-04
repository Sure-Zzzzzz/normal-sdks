package io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.KafkaConsumerException;
import lombok.Getter;

/**
 * 幂等处理租约领取结果。
 *
 * @author surezzzzzz
 */
@Getter
public class KafkaConsumerIdempotencyAcquireResult {

    private final KafkaConsumerIdempotencyAcquireStatus status;
    private final KafkaConsumerIdempotencyLease lease;

    private KafkaConsumerIdempotencyAcquireResult(KafkaConsumerIdempotencyAcquireStatus status,
                                                  KafkaConsumerIdempotencyLease lease) {
        this.status = status;
        this.lease = lease;
    }

    /**
     * 创建当前投递已领取处理租约的结果。
     *
     * @param lease 当前 owner 的处理租约
     * @return 已领取结果
     */
    public static KafkaConsumerIdempotencyAcquireResult acquired(KafkaConsumerIdempotencyLease lease) {
        if (lease == null) {
            throw new KafkaConsumerException(ErrorCode.IDEMPOTENCY_CHECK_FAILED,
                    ErrorMessage.IDEMPOTENCY_LEASE_REQUIRED);
        }
        return new KafkaConsumerIdempotencyAcquireResult(KafkaConsumerIdempotencyAcquireStatus.ACQUIRED, lease);
    }

    /**
     * 创建存在未过期处理租约的结果。
     *
     * @return 处理中结果
     */
    public static KafkaConsumerIdempotencyAcquireResult inProgress() {
        return new KafkaConsumerIdempotencyAcquireResult(KafkaConsumerIdempotencyAcquireStatus.IN_PROGRESS, null);
    }

    /**
     * 创建消息已完成的结果。
     *
     * @return 已完成结果
     */
    public static KafkaConsumerIdempotencyAcquireResult completed() {
        return new KafkaConsumerIdempotencyAcquireResult(KafkaConsumerIdempotencyAcquireStatus.COMPLETED, null);
    }
}
