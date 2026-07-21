package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.annotation.SimpleKafkaOutboxComponent;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration.KafkaOutboxPropertiesValidator;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.configuration.SimpleKafkaOutboxProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.*;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.exception.KafkaOutboxConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outbox 常量、枚举与配置测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class OutboxConstantAndPropertiesTest {

    @Test
    public void testEnumContracts() {
        String[] statusCodes = OutboxStatus.getAllCodes();
        String[] payloadCodes = OutboxPayloadKind.getAllCodes();

        log.info("Outbox 状态代码: {}", Arrays.toString(statusCodes));
        log.info("Outbox payload 类型代码: {}", Arrays.toString(payloadCodes));
        assertArrayEquals(new String[]{"PENDING", "PROCESSING", "RETRY_WAIT", "SENT", "POISON"},
                statusCodes, "状态代码应完整且顺序稳定");
        assertArrayEquals(new String[]{"STRING", "JSON", "NULL"}, payloadCodes,
                "payload 类型代码应完整且顺序稳定");
        assertSame(OutboxStatus.RETRY_WAIT, OutboxStatus.fromCode("retry_wait"),
                "状态解析应忽略大小写");
        assertTrue(OutboxPayloadKind.isValid("json"), "JSON 类型应忽略大小写验证");
        assertNull(OutboxStatus.fromCode("UNKNOWN"), "未知状态应返回 null");
        assertFalse(OutboxPayloadKind.isValid(null), "null payload 类型应无效");
        assertEquals(OutboxStatus.SENT.getCode(), OutboxStatus.SENT.toString(),
                "状态 toString 应返回 code");
    }

    @Test
    public void testPropertiesDefaultsReferenceContract() {
        SimpleKafkaOutboxProperties properties = new SimpleKafkaOutboxProperties();

        log.info("Outbox 默认配置: {}", properties);
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_ENABLE, properties.isEnable(),
                "enable 默认值应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_TABLE_NAME, properties.getTableName(),
                "table-name 默认值应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_WORKER_CONCURRENCY,
                properties.getWorker().getConcurrency(), "worker concurrency 默认值应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_WORKER_BATCH_SIZE,
                properties.getWorker().getBatchSize(), "worker batch-size 默认值应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_LEASE_MS, properties.getWorker().getLeaseMs(),
                "lease 默认值应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_SEND_TIMEOUT_MS, properties.getSend().getTimeoutMs(),
                "send timeout 默认值应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_MAX_ATTEMPTS,
                properties.getRetry().getMaxAttempts(), "max-attempts 默认值应与常量一致");
        assertEquals(SimpleKafkaOutboxConstant.DEFAULT_CLEANUP_RETENTION_DAYS,
                properties.getCleanup().getRetentionDays(), "retention-days 默认值应与常量一致");
    }

    @Test
    public void testPropertiesValidationBoundaries() {
        KafkaOutboxPropertiesValidator validator = new KafkaOutboxPropertiesValidator();
        SimpleKafkaOutboxProperties valid = new SimpleKafkaOutboxProperties();
        validator.validate(valid);

        SimpleKafkaOutboxProperties invalidTable = new SimpleKafkaOutboxProperties();
        invalidTable.setTableName("invalid-table");
        KafkaOutboxConfigurationException tableException = assertThrows(KafkaOutboxConfigurationException.class,
                () -> validator.validate(invalidTable));

        SimpleKafkaOutboxProperties invalidTimeout = new SimpleKafkaOutboxProperties();
        invalidTimeout.getSend().setTimeoutMs(invalidTimeout.getWorker().getLeaseMs());
        KafkaOutboxConfigurationException timeoutException = assertThrows(KafkaOutboxConfigurationException.class,
                () -> validator.validate(invalidTimeout));

        SimpleKafkaOutboxProperties invalidJitter = new SimpleKafkaOutboxProperties();
        invalidJitter.getRetry().setJitterFactor(1.1D);
        KafkaOutboxConfigurationException jitterException = assertThrows(KafkaOutboxConfigurationException.class,
                () -> validator.validate(invalidJitter));

        log.info("非法表名错误: {}", tableException.getMessage());
        log.info("非法超时错误: {}", timeoutException.getMessage());
        log.info("非法抖动错误: {}", jitterException.getMessage());
        assertEquals(ErrorCode.KAFKA_OUTBOX_001, tableException.getErrorCode(),
                "非法表名应使用配置错误码");
        assertEquals(ErrorCode.KAFKA_OUTBOX_001, timeoutException.getErrorCode(),
                "非法超时应使用配置错误码");
        assertEquals(ErrorCode.KAFKA_OUTBOX_001, jitterException.getErrorCode(),
                "非法抖动应使用配置错误码");
    }

    @Test
    public void testErrorCodeAndMessageAreOneToOne() {
        Set<String> codeNames = publicStringFieldNames(ErrorCode.class);
        Set<String> messageNames = publicStringFieldNames(ErrorMessage.class);

        log.info("错误码字段: {}", codeNames);
        log.info("错误消息字段: {}", messageNames);
        assertEquals(codeNames, messageNames, "ErrorCode 和 ErrorMessage 字段必须一一对应");
        assertEquals(9, codeNames.size(), "1.0.0 应固定九个公共错误码");
    }

    @Test
    public void testComponentAnnotationIsPureMarker() {
        Component component = SimpleKafkaOutboxComponent.class.getAnnotation(Component.class);
        int declaredMethodCount = SimpleKafkaOutboxComponent.class.getDeclaredMethods().length;

        log.info("组件注解元 Component: {}, 声明方法数: {}", component, declaredMethodCount);
        assertNull(component, "自定义组件注解不能叠加 @Component");
        assertEquals(0, declaredMethodCount, "自定义组件注解应为纯标记注解");
    }

    private Set<String> publicStringFieldNames(Class<?> type) {
        Set<String> names = new HashSet<>();
        for (Field field : type.getDeclaredFields()) {
            if (Modifier.isPublic(field.getModifiers()) && Modifier.isStatic(field.getModifiers())
                    && field.getType() == String.class) {
                names.add(field.getName());
            }
        }
        return names;
    }
}
