package io.github.surezzzzzz.sdk.messaging.kafka.consumer.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.configuration.SimpleKafkaConsumerProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.DefaultKafkaConsumerBackoffPolicy;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 默认退避策略测试。
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultKafkaConsumerBackoffPolicyTest {

    @Test
    public void testExponentialBackoffCapsAtConfiguredMaximum() {
        DefaultKafkaConsumerBackoffPolicy policy = policy(100L, 2.0D, 500L, 0.0D);

        long first = policy.computeBackoffMs(1);
        long second = policy.computeBackoffMs(2);
        long third = policy.computeBackoffMs(3);
        long fourth = policy.computeBackoffMs(4);
        long capped = policy.computeBackoffMs(100);
        log.info("指数退避：first={}，second={}，third={}，fourth={}，capped={}",
                first, second, third, fourth, capped);

        assertEquals(100L, first);
        assertEquals(200L, second);
        assertEquals(400L, third);
        assertEquals(500L, fourth);
        assertEquals(500L, capped);
    }

    @Test
    public void testJitterStaysWithinExpectedRange() {
        DefaultKafkaConsumerBackoffPolicy policy = policy(100L, 2.0D, 1000L, 0.2D);
        for (int i = 0; i < 50; i++) {
            long value = policy.computeBackoffMs(3);
            log.info("第 {} 次抖动退避：{}", i, value);
            assertTrue(value >= 320L && value < 480L, "抖动值必须位于 [320, 480) 范围");
        }
    }

    @Test
    public void testLargeAttemptNeverOverflowsToNegative() {
        DefaultKafkaConsumerBackoffPolicy policy = policy(1L, Double.MAX_VALUE, 1000L, 0.0D);
        long value = policy.computeBackoffMs(Integer.MAX_VALUE);
        log.info("最大 attempt 的退避：{}", value);

        assertEquals(1000L, value);
    }

    @Test
    public void testLargeAttemptWithUnitMultiplierReturnsWithoutLinearCalculation() {
        DefaultKafkaConsumerBackoffPolicy policy = policy(100L, 1.0D, 1000L, 0.0D);

        long value = policy.computeBackoffMs(Integer.MAX_VALUE);
        log.info("单位倍数下的大 attempt 退避：{}", value);

        assertEquals(100L, value);
    }

    private DefaultKafkaConsumerBackoffPolicy policy(long initial, double multiplier, long maximum, double jitter) {
        SimpleKafkaConsumerProperties properties = new SimpleKafkaConsumerProperties();
        properties.getError().setInitialIntervalMs(initial);
        properties.getError().setMultiplier(multiplier);
        properties.getError().setMaxIntervalMs(maximum);
        properties.getError().setJitterFactor(jitter);
        return new DefaultKafkaConsumerBackoffPolicy(properties);
    }
}
