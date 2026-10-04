package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyAcquireStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.support.KafkaConsumerE2eRecorder;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Consumer 端到端记录器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaConsumerE2eRecorderTest {

    @Test
    public void testIdempotencyStatusHistoryRetainsEarlierBranch() {
        String messageId = "mock-status-history";
        KafkaConsumerE2eRecorder.clear(messageId);
        try {
            KafkaConsumerE2eRecorder.recordIdempotencyStatus(messageId,
                    KafkaConsumerIdempotencyAcquireStatus.IN_PROGRESS);
            KafkaConsumerE2eRecorder.recordIdempotencyStatus(messageId,
                    KafkaConsumerIdempotencyAcquireStatus.ACQUIRED);

            boolean observedInProgress = KafkaConsumerE2eRecorder.awaitIdempotencyStatus(messageId,
                    KafkaConsumerIdempotencyAcquireStatus.IN_PROGRESS, 0L);
            log.info("幂等历史观测：messageId={}，observedInProgress={}", messageId, observedInProgress);

            assertTrue(observedInProgress, "后续领取状态不得覆盖已发生的 IN_PROGRESS 分支");
        } finally {
            KafkaConsumerE2eRecorder.clear(messageId);
        }

        assertFalse(KafkaConsumerE2eRecorder.awaitIdempotencyStatus(messageId,
                KafkaConsumerIdempotencyAcquireStatus.IN_PROGRESS, 0L), "清理后不得保留历史状态");
    }
}
