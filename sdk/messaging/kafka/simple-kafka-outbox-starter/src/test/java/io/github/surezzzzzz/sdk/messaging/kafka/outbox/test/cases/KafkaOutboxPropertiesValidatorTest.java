package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration.KafkaOutboxPropertiesValidator;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration.SimpleKafkaOutboxProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.exception.KafkaOutboxConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Kafka Outbox 配置校验器边界测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaOutboxPropertiesValidatorTest {

    private final KafkaOutboxPropertiesValidator validator = new KafkaOutboxPropertiesValidator();

    @Test
    public void testDefaultAndInclusiveBoundariesAreValid() {
        SimpleKafkaOutboxProperties defaults = new SimpleKafkaOutboxProperties();
        SimpleKafkaOutboxProperties inclusive = new SimpleKafkaOutboxProperties();
        inclusive.setTableName(repeat('a', SimpleKafkaOutboxConstant.MAX_TABLE_NAME_LENGTH));
        inclusive.getWorker().setConcurrency(1);
        inclusive.getWorker().setBatchSize(1);
        inclusive.getWorker().setScanIntervalMs(1L);
        inclusive.getWorker().setIdleIntervalMs(1L);
        inclusive.getWorker().setLeaseMs(2L);
        inclusive.getWorker().setShutdownAwaitMs(2L);
        inclusive.getSend().setTimeoutMs(1L);
        inclusive.getRetry().setMaxAttempts(1);
        inclusive.getRetry().setInitialIntervalMs(1L);
        inclusive.getRetry().setMultiplier(1.0D);
        inclusive.getRetry().setMaxIntervalMs(1L);
        inclusive.getRetry().setJitterFactor(0.0D);
        inclusive.getCleanup().setRetentionDays(Integer.MAX_VALUE);
        inclusive.getCleanup().setBatchSize(1);
        inclusive.getCleanup().setIntervalMs(1L);

        log.info("默认合法配置: {}", defaults);
        assertDoesNotThrow(() -> validator.validate(defaults), "默认配置应通过全部校验");
        log.info("包含下界、等值边界和 int 上界的合法配置: {}", inclusive);
        assertDoesNotThrow(() -> validator.validate(inclusive), "所有包含式合法边界应通过校验");

        inclusive.getRetry().setJitterFactor(1.0D);
        log.info("jitter-factor 合法上界配置: {}", inclusive.getRetry());
        assertDoesNotThrow(() -> validator.validate(inclusive), "jitter-factor 等于 1 时应通过校验");
    }

    @Test
    public void testNullPropertiesAndTableNameBoundaries() {
        assertInvalid(null, SimpleKafkaOutboxConstant.REASON_PROPERTIES_EMPTY, "空 Properties");
        assertInvalid(properties -> properties.setTableName(null),
                SimpleKafkaOutboxConstant.REASON_TABLE_NAME_INVALID, "null 表名");
        assertInvalid(properties -> properties.setTableName(" "),
                SimpleKafkaOutboxConstant.REASON_TABLE_NAME_INVALID, "空白表名");
        assertInvalid(properties -> properties.setTableName("outbox-name"),
                SimpleKafkaOutboxConstant.REASON_TABLE_NAME_INVALID, "包含非法字符的表名");
        assertInvalid(properties -> properties.setTableName(
                        repeat('a', SimpleKafkaOutboxConstant.MAX_TABLE_NAME_LENGTH + 1)),
                SimpleKafkaOutboxConstant.REASON_TABLE_NAME_INVALID, "超过长度上限的表名");
    }

    @Test
    public void testWorkerBoundaries() {
        assertInvalid(properties -> properties.setWorker(null),
                SimpleKafkaOutboxConstant.REASON_WORKER_CONFIG_INVALID, "null Worker 配置");
        assertInvalid(properties -> properties.getWorker().setConcurrency(0),
                SimpleKafkaOutboxConstant.REASON_WORKER_CONFIG_INVALID, "并发数零值");
        assertInvalid(properties -> properties.getWorker().setBatchSize(0),
                SimpleKafkaOutboxConstant.REASON_WORKER_CONFIG_INVALID, "Worker 批量大小零值");
        assertInvalid(properties -> properties.getWorker().setScanIntervalMs(0L),
                SimpleKafkaOutboxConstant.REASON_WORKER_CONFIG_INVALID, "扫描间隔零值");
        assertInvalid(properties -> properties.getWorker().setIdleIntervalMs(0L),
                SimpleKafkaOutboxConstant.REASON_WORKER_CONFIG_INVALID, "空闲间隔零值");
        assertInvalid(properties -> properties.getWorker().setLeaseMs(0L),
                SimpleKafkaOutboxConstant.REASON_WORKER_CONFIG_INVALID, "租约零值");
        assertInvalid(properties -> properties.getWorker().setLeaseMs(
                        Long.MAX_VALUE / SimpleKafkaOutboxConstant.MILLIS_TO_MICROS + 1L),
                SimpleKafkaOutboxConstant.REASON_LEASE_OVERFLOW, "租约微秒转换溢出边界");
    }

    @Test
    public void testSendAndShutdownBoundaries() {
        assertInvalid(properties -> properties.setSend(null),
                SimpleKafkaOutboxConstant.REASON_SEND_TIMEOUT_INVALID, "null Send 配置");
        assertInvalid(properties -> properties.getSend().setTimeoutMs(0L),
                SimpleKafkaOutboxConstant.REASON_SEND_TIMEOUT_INVALID, "发送超时零值");
        assertInvalid(properties -> properties.getSend().setTimeoutMs(properties.getWorker().getLeaseMs()),
                SimpleKafkaOutboxConstant.REASON_SEND_TIMEOUT_INVALID, "发送超时等于租约");
        assertInvalid(properties -> properties.getWorker().setShutdownAwaitMs(0L),
                SimpleKafkaOutboxConstant.REASON_SHUTDOWN_TIMEOUT_INVALID, "停机等待零值");
        assertInvalid(properties -> properties.getWorker().setShutdownAwaitMs(
                        properties.getWorker().getLeaseMs() + 1L),
                SimpleKafkaOutboxConstant.REASON_SHUTDOWN_TIMEOUT_INVALID, "停机等待超过租约");
    }

    @Test
    public void testRetryBoundaries() {
        assertInvalid(properties -> properties.setRetry(null),
                SimpleKafkaOutboxConstant.REASON_RETRY_CONFIG_INVALID, "null Retry 配置");
        assertInvalid(properties -> properties.getRetry().setMaxAttempts(0),
                SimpleKafkaOutboxConstant.REASON_RETRY_CONFIG_INVALID, "最大尝试次数零值");
        assertInvalid(properties -> properties.getRetry().setInitialIntervalMs(0L),
                SimpleKafkaOutboxConstant.REASON_RETRY_CONFIG_INVALID, "初始重试间隔零值");
        assertInvalid(properties -> properties.getRetry().setMultiplier(Math.nextDown(1.0D)),
                SimpleKafkaOutboxConstant.REASON_RETRY_CONFIG_INVALID, "退避倍数低于下界");
        assertInvalid(properties -> properties.getRetry().setMaxIntervalMs(0L),
                SimpleKafkaOutboxConstant.REASON_RETRY_CONFIG_INVALID, "最大重试间隔零值");
        assertInvalid(properties -> properties.getRetry().setInitialIntervalMs(
                        properties.getRetry().getMaxIntervalMs() + 1L),
                SimpleKafkaOutboxConstant.REASON_RETRY_CONFIG_INVALID, "初始间隔超过最大间隔");
        assertInvalid(properties -> properties.getRetry().setMultiplier(Double.NaN),
                SimpleKafkaOutboxConstant.REASON_RETRY_CONFIG_INVALID, "退避倍数 NaN");
        assertInvalid(properties -> properties.getRetry().setMultiplier(Double.POSITIVE_INFINITY),
                SimpleKafkaOutboxConstant.REASON_RETRY_CONFIG_INVALID, "退避倍数正无穷");
        assertInvalid(properties -> properties.getRetry().setMultiplier(Double.NEGATIVE_INFINITY),
                SimpleKafkaOutboxConstant.REASON_RETRY_CONFIG_INVALID, "退避倍数负无穷");
        assertInvalid(properties -> properties.getRetry().setJitterFactor(Double.NaN),
                SimpleKafkaOutboxConstant.REASON_RETRY_CONFIG_INVALID, "抖动比例 NaN");
        assertInvalid(properties -> properties.getRetry().setJitterFactor(Double.POSITIVE_INFINITY),
                SimpleKafkaOutboxConstant.REASON_RETRY_CONFIG_INVALID, "抖动比例正无穷");
        assertInvalid(properties -> properties.getRetry().setJitterFactor(Double.NEGATIVE_INFINITY),
                SimpleKafkaOutboxConstant.REASON_RETRY_CONFIG_INVALID, "抖动比例负无穷");
        assertInvalid(properties -> properties.getRetry().setJitterFactor(Math.nextDown(0.0D)),
                SimpleKafkaOutboxConstant.REASON_RETRY_CONFIG_INVALID, "抖动比例低于下界");
        assertInvalid(properties -> properties.getRetry().setJitterFactor(Math.nextUp(1.0D)),
                SimpleKafkaOutboxConstant.REASON_RETRY_CONFIG_INVALID, "抖动比例高于上界");
    }

    @Test
    public void testCleanupBoundaries() {
        assertInvalid(properties -> properties.setCleanup(null),
                SimpleKafkaOutboxConstant.REASON_CLEANUP_CONFIG_INVALID, "null Cleanup 配置");
        assertInvalid(properties -> properties.getCleanup().setRetentionDays(0),
                SimpleKafkaOutboxConstant.REASON_CLEANUP_CONFIG_INVALID, "保留天数零值");
        assertInvalid(properties -> properties.getCleanup().setBatchSize(0),
                SimpleKafkaOutboxConstant.REASON_CLEANUP_CONFIG_INVALID, "清理批量大小零值");
        assertInvalid(properties -> properties.getCleanup().setIntervalMs(0L),
                SimpleKafkaOutboxConstant.REASON_CLEANUP_CONFIG_INVALID, "清理间隔零值");
    }

    private void assertInvalid(Consumer<SimpleKafkaOutboxProperties> mutation, String reason, String scenario) {
        SimpleKafkaOutboxProperties properties = mutation == null ? null : new SimpleKafkaOutboxProperties();
        if (mutation != null) {
            mutation.accept(properties);
        }
        log.info("待校验非法配置场景: {}, 输入: {}", scenario, properties);
        KafkaOutboxConfigurationException exception = assertThrows(KafkaOutboxConfigurationException.class,
                () -> validator.validate(properties), scenario + "应被配置校验拒绝");
        String expectedMessage = String.format(ErrorMessage.KAFKA_OUTBOX_001, reason);

        log.info("非法配置场景输出: {}, 错误码: {}, 错误消息: {}", scenario,
                exception.getErrorCode(), exception.getMessage());
        assertEquals(ErrorCode.KAFKA_OUTBOX_001, exception.getErrorCode(), scenario + "应返回统一配置错误码");
        assertEquals(expectedMessage, exception.getMessage(), scenario + "应返回精确配置错误消息");
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
