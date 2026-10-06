package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration.SimpleKafkaOutboxProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.SimpleKafkaOutboxConstant;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Simple Kafka Outbox 默认配置测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class SimpleKafkaOutboxPropertiesTest {

    @Test
    public void testAllDefaultValues() {
        SimpleKafkaOutboxProperties properties = new SimpleKafkaOutboxProperties();

        log.info("Kafka Outbox 全部默认配置: {}", properties);
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_ENABLE, properties.isEnable(), "默认总开关应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_RESOURCE_BEAN_NAME, properties.getDataSourceBeanName(),
                "默认 DataSource Bean 名应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_RESOURCE_BEAN_NAME,
                properties.getTransactionManagerBeanName(), "默认事务管理器 Bean 名应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_TABLE_NAME, properties.getTableName(),
                "默认表名应与常量一致");
        assertNotNull(properties.getWorker(), "默认 Worker 配置不能为空");
        assertNotNull(properties.getSend(), "默认发送配置不能为空");
        assertNotNull(properties.getRetry(), "默认重试配置不能为空");
        assertNotNull(properties.getCleanup(), "默认清理配置不能为空");

        SimpleKafkaOutboxProperties.WorkerConfig worker = properties.getWorker();
        log.info("Worker 默认配置: {}", worker);
        assertTrue(worker.isEnable(), "默认应启用 Worker");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_WORKER_CONCURRENCY, worker.getConcurrency(),
                "默认 Worker 并发数应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_WORKER_BATCH_SIZE, worker.getBatchSize(),
                "默认 Worker 批量大小应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_SCAN_INTERVAL_MS, worker.getScanIntervalMs(),
                "默认扫描间隔应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_IDLE_INTERVAL_MS, worker.getIdleIntervalMs(),
                "默认空闲间隔应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_LEASE_MS, worker.getLeaseMs(),
                "默认租约时长应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_SHUTDOWN_AWAIT_MS, worker.getShutdownAwaitMs(),
                "默认停机等待时长应与常量一致");

        log.info("Send 默认配置: {}", properties.getSend());
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_SEND_TIMEOUT_MS, properties.getSend().getTimeoutMs(),
                "默认发送超时应与常量一致");

        SimpleKafkaOutboxProperties.RetryConfig retry = properties.getRetry();
        log.info("Retry 默认配置: {}", retry);
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_MAX_ATTEMPTS, retry.getMaxAttempts(),
                "默认最大尝试次数应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_RETRY_INITIAL_INTERVAL_MS, retry.getInitialIntervalMs(),
                "默认初始重试间隔应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_RETRY_MULTIPLIER, retry.getMultiplier(),
                "默认退避倍数应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_RETRY_MAX_INTERVAL_MS, retry.getMaxIntervalMs(),
                "默认最大重试间隔应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_RETRY_JITTER_FACTOR, retry.getJitterFactor(),
                "默认抖动比例应与常量一致");

        SimpleKafkaOutboxProperties.CleanupConfig cleanup = properties.getCleanup();
        log.info("Cleanup 默认配置: {}", cleanup);
        assertTrue(cleanup.isEnable(), "默认应启用自动清理");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_CLEANUP_RETENTION_DAYS, cleanup.getRetentionDays(),
                "默认保留天数应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_CLEANUP_BATCH_SIZE, cleanup.getBatchSize(),
                "默认清理批量大小应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_CLEANUP_INTERVAL_MS, cleanup.getIntervalMs(),
                "默认清理间隔应与常量一致");
        assertFalse(properties.isEnable(), "SDK 默认总开关应保持关闭");
    }
}
