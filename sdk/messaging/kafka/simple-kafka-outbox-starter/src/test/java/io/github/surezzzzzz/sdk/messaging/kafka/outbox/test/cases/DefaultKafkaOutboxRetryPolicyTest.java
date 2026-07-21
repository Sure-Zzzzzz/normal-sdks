package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration.SimpleKafkaOutboxProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxRetryContext;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.DefaultKafkaOutboxRetryPolicy;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.retry.KafkaOutboxJitterGenerator;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.exception.KafkaPublishException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 默认 Kafka Outbox 重试策略测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultKafkaOutboxRetryPolicyTest {

    @Test
    public void testRetryClassification() {
        DefaultKafkaOutboxRetryPolicy policy = policy(defaultRetry(), 0.5D);
        List<String> deterministicCodes = Arrays.asList(
                io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_002,
                io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_003,
                io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_004,
                io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_005,
                io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_006,
                io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_009,
                io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_010);
        for (String errorCode : deterministicCodes) {
            KafkaPublishException cause = new KafkaPublishException(errorCode, "mock deterministic failure");
            boolean retryable = policy.isRetryable(null, cause);
            log.info("确定性 Publisher 错误分类输入: {}, 是否可重试: {}", errorCode, retryable);
            assertFalse(retryable, "确定性 Publisher 错误 " + errorCode + " 不应重试");
        }

        KafkaPublishException transientPublish = new KafkaPublishException(
                io.github.surezzzzzz.sdk.messaging.kafka.publisher.constant.ErrorCode.KAFKA_PUBLISHER_007,
                "mock transient failure");
        KafkaPublishException unknownPublish = new KafkaPublishException("UNKNOWN", "mock unknown failure");
        log.info("可重试分类输入: TimeoutException、发送错误、未知 Publisher 错误、普通异常、null");
        assertTrue(policy.isRetryable(null, new TimeoutException("mock timeout")), "超时异常应重试");
        assertTrue(policy.isRetryable(null, transientPublish), "Publisher 发送错误应重试");
        assertTrue(policy.isRetryable(null, unknownPublish), "未知 Publisher 错误应保守重试");
        assertTrue(policy.isRetryable(null, new IllegalStateException("mock")), "普通运行时异常应重试");
        assertTrue(policy.isRetryable(null, null), "未知 null cause 应保守重试");
    }

    @Test
    public void testExponentialBackoffAndMaximumCap() {
        SimpleKafkaOutboxProperties.RetryConfig retry = defaultRetry();
        retry.setInitialIntervalMs(100L);
        retry.setMultiplier(2.0D);
        retry.setMaxIntervalMs(500L);
        retry.setJitterFactor(0.0D);
        DefaultKafkaOutboxRetryPolicy policy = policy(retry, 0.5D);

        long nullContextDelay = policy.nextDelayMs(null);
        long nullAttemptDelay = policy.nextDelayMs(context(null));
        long zeroAttemptDelay = policy.nextDelayMs(context(0));
        long firstDelay = policy.nextDelayMs(context(1));
        long secondDelay = policy.nextDelayMs(context(2));
        long thirdDelay = policy.nextDelayMs(context(3));
        long fourthDelay = policy.nextDelayMs(context(4));
        long hugeAttemptDelay = policy.nextDelayMs(context(Integer.MAX_VALUE));

        log.info("指数退避输入 attempt: nullContext/null/0/1/2/3/4/MAX，输出: {}/{}/{}/{}/{}/{}/{}/{}",
                nullContextDelay, nullAttemptDelay, zeroAttemptDelay, firstDelay, secondDelay,
                thirdDelay, fourthDelay, hugeAttemptDelay);
        assertEquals(100L, nullContextDelay, "null context 应按第一次尝试计算");
        assertEquals(100L, nullAttemptDelay, "null attempt 应按第一次尝试计算");
        assertEquals(100L, zeroAttemptDelay, "非正 attempt 不应产生负指数");
        assertEquals(100L, firstDelay, "第一次尝试后的基础延迟应为初始间隔");
        assertEquals(200L, secondDelay, "第二次尝试应进行一次指数退避");
        assertEquals(400L, thirdDelay, "第三次尝试应进行两次指数退避");
        assertEquals(500L, fourthDelay, "指数退避应封顶于最大间隔");
        assertEquals(500L, hugeAttemptDelay, "极大 attempt 应快速封顶且不能溢出");
    }

    @Test
    public void testJitterAndRandomNormalizationBoundaries() {
        SimpleKafkaOutboxProperties.RetryConfig retry = defaultRetry();
        retry.setInitialIntervalMs(1000L);
        retry.setMultiplier(2.0D);
        retry.setMaxIntervalMs(5000L);
        retry.setJitterFactor(0.2D);

        long belowRange = policy(retry, -1.0D).nextDelayMs(context(1));
        long lower = policy(retry, 0.0D).nextDelayMs(context(1));
        long middle = policy(retry, 0.5D).nextDelayMs(context(1));
        long upper = policy(retry, 1.0D).nextDelayMs(context(1));
        long aboveRange = policy(retry, 2.0D).nextDelayMs(context(1));
        long capped = policy(retry, 1.0D).nextDelayMs(context(4));

        log.info("抖动随机值输入: -1/0/0.5/1/2，输出: {}/{}/{}/{}/{}, 封顶输出: {}",
                belowRange, lower, middle, upper, aboveRange, capped);
        assertEquals(800L, belowRange, "低于零的随机值应归一化到零");
        assertEquals(800L, lower, "随机值下界应产生最小抖动延迟");
        assertEquals(1000L, middle, "随机值中点应保留基础延迟");
        assertEquals(1200L, upper, "随机值上界应产生最大抖动延迟");
        assertEquals(1200L, aboveRange, "高于一的随机值应归一化到一");
        assertEquals(5000L, capped, "抖动后延迟仍不得超过最大间隔");
    }

    private DefaultKafkaOutboxRetryPolicy policy(SimpleKafkaOutboxProperties.RetryConfig retry,
                                                 double random) {
        KafkaOutboxJitterGenerator jitterGenerator = () -> random;
        return new DefaultKafkaOutboxRetryPolicy(retry, jitterGenerator);
    }

    private SimpleKafkaOutboxProperties.RetryConfig defaultRetry() {
        return new SimpleKafkaOutboxProperties().getRetry();
    }

    private OutboxRetryContext context(Integer attempt) {
        return OutboxRetryContext.builder().attempt(attempt).build();
    }
}
