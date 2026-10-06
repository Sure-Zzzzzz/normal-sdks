package io.github.surezzzzzz.sdk.messaging.kafka.outbox.test.cases;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxPayloadKind;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine.DefaultKafkaOutboxEngine;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.entity.OutboxRecordEntity;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.exception.KafkaOutboxException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.listener.KafkaOutboxEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxEventContext;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxSaveResult;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository.KafkaOutboxRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.serializer.KafkaOutboxMessageSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace.KafkaOutboxTraceSnapshotResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.datasource.ConnectionHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 默认 Kafka Outbox Engine 单元测试
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultKafkaOutboxEngineTest {

    private DataSource dataSource;
    private Connection connection;
    private KafkaOutboxRepository repository;
    private KafkaOutboxMessageSerializer serializer;
    private KafkaOutboxTraceSnapshotResolver traceSnapshotResolver;
    private KafkaOutboxEventListener listener;
    private DefaultKafkaOutboxEngine engine;

    @BeforeEach
    public void setUp() {
        dataSource = mock(DataSource.class);
        connection = mock(Connection.class);
        repository = mock(KafkaOutboxRepository.class);
        serializer = mock(KafkaOutboxMessageSerializer.class);
        traceSnapshotResolver = mock(KafkaOutboxTraceSnapshotResolver.class);
        listener = mock(KafkaOutboxEventListener.class);
        engine = new DefaultKafkaOutboxEngine(dataSource, repository, serializer,
                traceSnapshotResolver, listener);
        when(repository.save(any(OutboxRecordEntity.class))).thenReturn(101L);
        when(serializer.serializePayload(any())).thenReturn("mock-payload-json");
        when(serializer.serializeStringMap(any())).thenReturn("mock-headers-json");
        when(serializer.serializeObjectMap(any())).thenReturn("mock-attributes-json");
        when(traceSnapshotResolver.resolveTraceId()).thenReturn(" mock-trace-id ");
    }

    @AfterEach
    public void tearDown() {
        if (TransactionSynchronizationManager.hasResource(dataSource)) {
            TransactionSynchronizationManager.unbindResource(dataSource);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setCurrentTransactionName(null);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(false);
        TransactionSynchronizationManager.setCurrentTransactionIsolationLevel(null);
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    public void testSaveRejectsInactiveTransaction() {
        KafkaPublishMessage<Object> input = validMessage("mock-payload");

        KafkaOutboxException output = assertThrows(KafkaOutboxException.class,
                () -> engine.save(input), "无活跃事务时应拒绝保存");

        log.info("无活跃事务输入: {}, 输出错误码: {}, 输出错误消息: {}",
                input, output.getErrorCode(), output.getMessage());
        assertEquals(ErrorCode.KAFKA_OUTBOX_003, output.getErrorCode(),
                "无活跃事务的错误码应为事务非法");
        assertTrue(output.getMessage().contains("当前没有活跃的 Spring 本地事务"),
                "无活跃事务的错误消息应保留准确原因");
        verify(repository, never()).save(any(OutboxRecordEntity.class));
    }

    @Test
    public void testSaveRejectsReadOnlyTransaction() {
        beginTransaction(true, true);
        KafkaPublishMessage<Object> input = validMessage("mock-payload");

        KafkaOutboxException output = assertThrows(KafkaOutboxException.class,
                () -> engine.save(input), "只读事务中应拒绝保存");

        log.info("只读事务输入: {}, 输出错误码: {}, 输出错误消息: {}",
                input, output.getErrorCode(), output.getMessage());
        assertEquals(ErrorCode.KAFKA_OUTBOX_003, output.getErrorCode(),
                "只读事务的错误码应为事务非法");
        assertTrue(output.getMessage().contains("当前事务是只读事务"),
                "只读事务的错误消息应保留准确原因");
        verify(repository, never()).save(any(OutboxRecordEntity.class));
    }

    @Test
    public void testSaveRejectsTransactionWithoutSelectedDataSource() {
        beginTransaction(false, false);
        KafkaPublishMessage<Object> input = validMessage("mock-payload");

        KafkaOutboxException output = assertThrows(KafkaOutboxException.class,
                () -> engine.save(input), "事务未绑定选中 DataSource 时应拒绝保存");

        log.info("未绑定选中 DataSource 的输入: {}, 输出错误码: {}, 输出错误消息: {}",
                input, output.getErrorCode(), output.getMessage());
        assertEquals(ErrorCode.KAFKA_OUTBOX_003, output.getErrorCode(),
                "未绑定选中 DataSource 的错误码应为事务非法");
        assertTrue(output.getMessage().contains("当前事务未绑定选中的 DataSource"),
                "未绑定选中 DataSource 的错误消息应保留准确原因");
        verify(repository, never()).save(any(OutboxRecordEntity.class));
    }

    @Test
    public void testSavePreservesOriginalMessageAndBuildsRecord() {
        beginTransaction(false, true);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("mock-header", "mock-header-value");
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("mock-attribute", 7);
        KafkaPublishMessage<Object> input = validMessage("mock-payload");
        input.setKey("mock-key");
        input.setRouteKey("mock-route-key");
        input.setDatasourceKey("mock-datasource-key");
        input.setPartition(2);
        input.setTimestamp(123456L);
        input.setMessageId("mock-message-id");
        input.setMessageType("mock-message-type");
        input.setHeaders(headers);
        input.setAttributes(attributes);
        input.setEnvelopeEnabled(Boolean.TRUE);
        ArgumentCaptor<OutboxRecordEntity> recordCaptor = ArgumentCaptor.forClass(OutboxRecordEntity.class);

        OutboxSaveResult output = engine.save(input);
        verify(repository).save(recordCaptor.capture());
        OutboxRecordEntity record = recordCaptor.getValue();

        log.info("正常保存输入: {}, 输出记录主键: {}, 输出消息 ID: {}, 持久化记录: {}",
                input, output.getOutboxRecordId(), output.getMessageId(), record);
        assertEquals(Long.valueOf(101L), output.getOutboxRecordId(), "保存结果应返回 Repository 主键");
        assertEquals("mock-message-id", output.getMessageId(), "保存结果应返回原消息 ID");
        assertEquals("mock-topic", input.getTopic(), "保存过程不应修改原 topic");
        assertEquals("mock-key", input.getKey(), "保存过程不应修改原 key");
        assertEquals("mock-route-key", input.getRouteKey(), "保存过程不应修改原 routeKey");
        assertEquals("mock-datasource-key", input.getDatasourceKey(), "保存过程不应修改原 datasourceKey");
        assertEquals(Integer.valueOf(2), input.getPartition(), "保存过程不应修改原 partition");
        assertEquals(Long.valueOf(123456L), input.getTimestamp(), "保存过程不应修改原 timestamp");
        assertEquals("mock-message-id", input.getMessageId(), "保存过程不应修改原 messageId");
        assertEquals("mock-message-type", input.getMessageType(), "保存过程不应修改原 messageType");
        assertEquals("mock-payload", input.getPayload(), "保存过程不应修改原 payload");
        assertSame(headers, input.getHeaders(), "保存过程不应替换原 headers 引用");
        assertSame(attributes, input.getAttributes(), "保存过程不应替换原 attributes 引用");
        assertEquals(Boolean.TRUE, input.getEnvelopeEnabled(), "保存过程不应修改原 envelopeEnabled");
        assertEquals("mock-message-id", record.getMessageId(), "持久化记录应保留消息 ID");
        assertEquals("mock-topic", record.getTopic(), "持久化记录应保留 topic");
        assertEquals(OutboxPayloadKind.STRING.getCode(), record.getPayloadKind(),
                "字符串 payload 应记录为 STRING 类型");
        assertEquals("mock-trace-id", record.getTraceId(), "持久化记录应保存去除首尾空白的 traceId");
    }

    @Test
    public void testSaveDefensivelyCopiesHeadersAndAttributes() {
        beginTransaction(false, true);
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("mock-header", "before");
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("mock-attribute", "before");
        KafkaPublishMessage<Object> input = validMessage("mock-payload");
        input.setHeaders(headers);
        input.setAttributes(attributes);
        ArgumentCaptor<Map> headerCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<Map> attributeCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<OutboxRecordEntity> recordCaptor = ArgumentCaptor.forClass(OutboxRecordEntity.class);

        OutboxSaveResult output = engine.save(input);
        verify(serializer).serializeStringMap(headerCaptor.capture());
        verify(serializer).serializeObjectMap(attributeCaptor.capture());
        verify(repository).save(recordCaptor.capture());
        Map<String, String> serializerHeaders = headerCaptor.getValue();
        Map<String, Object> serializerAttributes = attributeCaptor.getValue();
        OutboxRecordEntity record = recordCaptor.getValue();
        headers.put("mock-header", "after");
        attributes.put("mock-attribute", "after");

        log.info("防御性复制输入 headers: {}, attributes: {}, serializer headers: {}, serializer attributes: {}, 持久化记录: {}, 输出: {}",
                headers, attributes, serializerHeaders, serializerAttributes, record, output.getOutboxRecordId());
        assertNotSame(headers, serializerHeaders, "Serializer 接收的 headers 应为防御性副本");
        assertNotSame(attributes, serializerAttributes, "Serializer 接收的 attributes 应为防御性副本");
        assertEquals("before", serializerHeaders.get("mock-header"),
                "原 headers 后续修改不应影响 Serializer 输入副本");
        assertEquals("before", serializerAttributes.get("mock-attribute"),
                "原 attributes 后续修改不应影响 Serializer 输入副本");
        assertEquals("mock-headers-json", record.getHeadersJson(), "持久化记录应使用 headers 序列化结果");
        assertEquals("mock-attributes-json", record.getAttributesJson(),
                "持久化记录应使用 attributes 序列化结果");
    }

    @Test
    public void testSaveGeneratesMessageIdWithoutWritingBackToMessage() {
        beginTransaction(false, true);
        KafkaPublishMessage<Object> input = validMessage("mock-payload");
        input.setMessageId(null);
        ArgumentCaptor<OutboxRecordEntity> recordCaptor = ArgumentCaptor.forClass(OutboxRecordEntity.class);

        OutboxSaveResult output = engine.save(input);
        verify(repository).save(recordCaptor.capture());
        OutboxRecordEntity record = recordCaptor.getValue();

        log.info("自动生成消息 ID 的输入 messageId: {}, 输出 messageId: {}, 持久化 messageId: {}",
                input.getMessageId(), output.getMessageId(), record.getMessageId());
        assertNull(input.getMessageId(), "自动生成消息 ID 不应回写原消息");
        assertNotNull(output.getMessageId(), "未提供消息 ID 时应自动生成");
        assertFalse(output.getMessageId().trim().isEmpty(), "自动生成的消息 ID 不应为空白");
        assertEquals(output.getMessageId(), record.getMessageId(), "保存结果与持久化记录应使用同一生成消息 ID");
        assertTrue(output.getMessageId().length() <= 191, "自动生成的消息 ID 不应超过 191 个字符");
    }

    @Test
    public void testSaveAccepts191CharacterMessageIdAndRejects192Characters() {
        beginTransaction(false, true);
        String acceptedMessageId = repeat('a', 191);
        String rejectedMessageId = repeat('b', 192);
        KafkaPublishMessage<Object> acceptedInput = validMessage("mock-payload");
        acceptedInput.setMessageId(acceptedMessageId);
        KafkaPublishMessage<Object> rejectedInput = validMessage("mock-payload");
        rejectedInput.setMessageId(rejectedMessageId);

        OutboxSaveResult acceptedOutput = engine.save(acceptedInput);
        KafkaOutboxException rejectedOutput = assertThrows(KafkaOutboxException.class,
                () -> engine.save(rejectedInput), "192 字符 messageId 应被拒绝");

        log.info("messageId 边界输入长度: [{}, {}], 接受输出长度: {}, 拒绝错误码: {}, 拒绝错误消息: {}",
                acceptedMessageId.length(), rejectedMessageId.length(), acceptedOutput.getMessageId().length(),
                rejectedOutput.getErrorCode(), rejectedOutput.getMessage());
        assertEquals(acceptedMessageId, acceptedOutput.getMessageId(), "191 字符 messageId 应被完整接受");
        assertEquals(ErrorCode.KAFKA_OUTBOX_007, rejectedOutput.getErrorCode(),
                "192 字符 messageId 应返回消息非法错误码");
        assertTrue(rejectedOutput.getMessage().contains("长度不能超过 191"),
                "192 字符 messageId 的错误消息应说明长度边界");
        verify(repository, times(1)).save(any(OutboxRecordEntity.class));
    }

    @Test
    public void testSaveRejectsBlankTopic() {
        beginTransaction(false, true);
        KafkaPublishMessage<Object> input = validMessage("mock-payload");
        input.setTopic("  ");

        KafkaOutboxException output = assertThrows(KafkaOutboxException.class,
                () -> engine.save(input), "空白 topic 应被拒绝");

        log.info("非法 topic 输入: '{}', 输出错误码: {}, 输出错误消息: {}",
                input.getTopic(), output.getErrorCode(), output.getMessage());
        assertEquals(ErrorCode.KAFKA_OUTBOX_007, output.getErrorCode(), "空白 topic 应返回消息非法错误码");
        assertTrue(output.getMessage().contains("topic 必须显式提供且不能为空"),
                "空白 topic 的错误消息应保留准确原因");
        verify(repository, never()).save(any(OutboxRecordEntity.class));
    }

    @Test
    public void testSaveRejectsNegativePartition() {
        beginTransaction(false, true);
        KafkaPublishMessage<Object> input = validMessage("mock-payload");
        input.setPartition(-1);

        KafkaOutboxException output = assertThrows(KafkaOutboxException.class,
                () -> engine.save(input), "负数 partition 应被拒绝");

        log.info("非法 partition 输入: {}, 输出错误码: {}, 输出错误消息: {}",
                input.getPartition(), output.getErrorCode(), output.getMessage());
        assertEquals(ErrorCode.KAFKA_OUTBOX_007, output.getErrorCode(), "负数 partition 应返回消息非法错误码");
        assertTrue(output.getMessage().contains("partition 不能小于 0"),
                "负数 partition 的错误消息应保留准确原因");
        verify(repository, never()).save(any(OutboxRecordEntity.class));
    }

    @Test
    public void testSaveRejectsNegativeTimestamp() {
        beginTransaction(false, true);
        KafkaPublishMessage<Object> input = validMessage("mock-payload");
        input.setTimestamp(-1L);

        KafkaOutboxException output = assertThrows(KafkaOutboxException.class,
                () -> engine.save(input), "负数 timestamp 应被拒绝");

        log.info("非法 timestamp 输入: {}, 输出错误码: {}, 输出错误消息: {}",
                input.getTimestamp(), output.getErrorCode(), output.getMessage());
        assertEquals(ErrorCode.KAFKA_OUTBOX_007, output.getErrorCode(), "负数 timestamp 应返回消息非法错误码");
        assertTrue(output.getMessage().contains("timestamp 不能小于 0"),
                "负数 timestamp 的错误消息应保留准确原因");
        verify(repository, never()).save(any(OutboxRecordEntity.class));
    }

    @Test
    public void testSaveClassifiesStringJsonAndNullPayload() {
        beginTransaction(false, true);
        Map<String, Object> jsonPayload = new LinkedHashMap<>();
        jsonPayload.put("mock-field", 9);
        KafkaPublishMessage<Object> stringInput = validMessage("mock-string");
        KafkaPublishMessage<Object> jsonInput = validMessage(jsonPayload);
        KafkaPublishMessage<Object> nullInput = validMessage(null);
        when(repository.save(any(OutboxRecordEntity.class))).thenReturn(201L, 202L, 203L);
        ArgumentCaptor<OutboxRecordEntity> recordCaptor = ArgumentCaptor.forClass(OutboxRecordEntity.class);

        engine.save(stringInput);
        engine.save(jsonInput);
        engine.save(nullInput);
        verify(repository, times(3)).save(recordCaptor.capture());
        List<OutboxRecordEntity> records = recordCaptor.getAllValues();

        log.info("payload 类型输入: [{}, {}, {}], 输出类型: [{}, {}, {}]",
                stringInput.getPayload(), jsonInput.getPayload(), nullInput.getPayload(),
                records.get(0).getPayloadKind(), records.get(1).getPayloadKind(), records.get(2).getPayloadKind());
        assertEquals(OutboxPayloadKind.STRING.getCode(), records.get(0).getPayloadKind(),
                "String payload 应分类为 STRING");
        assertEquals(OutboxPayloadKind.JSON.getCode(), records.get(1).getPayloadKind(),
                "对象 payload 应分类为 JSON");
        assertEquals(OutboxPayloadKind.NULL.getCode(), records.get(2).getPayloadKind(),
                "null payload 应分类为 NULL");
        verify(serializer).serializePayload("mock-string");
        verify(serializer).serializePayload(jsonPayload);
        verify(serializer).serializePayload(null);
    }

    @Test
    public void testSavePreservesSerializerFailure() {
        beginTransaction(false, true);
        KafkaPublishMessage<Object> input = validMessage("mock-payload");
        IllegalStateException expected = new IllegalStateException("mock-serializer-failure");
        when(serializer.serializePayload("mock-payload")).thenThrow(expected);

        IllegalStateException output = assertThrows(IllegalStateException.class,
                () -> engine.save(input), "Serializer 异常应原样抛出");

        log.info("Serializer 失败输入: {}, 预期异常: {}, 输出异常: {}",
                input, expected.getMessage(), output.getMessage());
        assertSame(expected, output, "Serializer 异常实例及错误信息应保持不变");
        assertEquals("mock-serializer-failure", output.getMessage(), "Serializer 错误消息应保持不变");
        verify(repository, never()).save(any(OutboxRecordEntity.class));
    }

    @Test
    public void testSavePreservesRepositoryFailure() {
        beginTransaction(false, true);
        KafkaPublishMessage<Object> input = validMessage("mock-payload");
        IllegalArgumentException expected = new IllegalArgumentException("mock-repository-failure");
        when(repository.save(any(OutboxRecordEntity.class))).thenThrow(expected);

        IllegalArgumentException output = assertThrows(IllegalArgumentException.class,
                () -> engine.save(input), "Repository 异常应原样抛出");

        log.info("Repository 失败输入: {}, 预期异常: {}, 输出异常: {}",
                input, expected.getMessage(), output.getMessage());
        assertSame(expected, output, "Repository 异常实例及错误信息应保持不变");
        assertEquals("mock-repository-failure", output.getMessage(), "Repository 错误消息应保持不变");
        verify(listener, never()).onSaved(any(OutboxEventContext.class));
    }

    @Test
    public void testSaveNotifiesListenerOnlyAfterManualAfterCommit() {
        beginTransaction(false, true);
        KafkaPublishMessage<Object> input = validMessage("mock-payload");
        input.setMessageId("mock-listener-message-id");
        input.setDatasourceKey("mock-datasource-key");
        ArgumentCaptor<OutboxEventContext> contextCaptor = ArgumentCaptor.forClass(OutboxEventContext.class);

        OutboxSaveResult output = engine.save(input);
        List<TransactionSynchronization> synchronizations =
                TransactionSynchronizationManager.getSynchronizations();
        verify(listener, never()).onSaved(any(OutboxEventContext.class));

        log.info("事务提交前输入: {}, 保存输出: {}, 同步器数量: {}, Listener 调用次数: 0",
                input, output.getOutboxRecordId(), synchronizations.size());
        assertEquals(1, synchronizations.size(), "每次保存应精确注册一个事务同步器");

        synchronizations.get(0).afterCommit();
        verify(listener).onSaved(contextCaptor.capture());
        OutboxEventContext context = contextCaptor.getValue();

        log.info("手动 afterCommit 后 Listener 上下文: recordId={}, messageId={}, topic={}, datasourceKey={}",
                context.getRecordId(), context.getMessageId(), context.getTopic(), context.getDatasourceKey());
        assertEquals(Long.valueOf(101L), context.getRecordId(), "Listener 上下文应包含持久化主键");
        assertEquals("mock-listener-message-id", context.getMessageId(), "Listener 上下文应包含消息 ID");
        assertEquals("mock-topic", context.getTopic(), "Listener 上下文应包含 topic");
        assertEquals("mock-datasource-key", context.getDatasourceKey(),
                "Listener 上下文应包含 datasourceKey");
    }

    @Test
    public void testListenerFailureIsIsolatedAfterCommit() {
        beginTransaction(false, true);
        KafkaPublishMessage<Object> input = validMessage("mock-payload");
        RuntimeException listenerFailure = new RuntimeException("mock-listener-failure");
        doThrow(listenerFailure).when(listener).onSaved(any(OutboxEventContext.class));

        OutboxSaveResult output = engine.save(input);
        TransactionSynchronization synchronization =
                TransactionSynchronizationManager.getSynchronizations().get(0);

        log.info("Listener 异常隔离输入: {}, 保存输出: {}, Listener 异常: {}",
                input, output.getOutboxRecordId(), listenerFailure.getMessage());
        assertDoesNotThrow(synchronization::afterCommit, "Listener 异常不应逃逸 afterCommit");
        log.info("Listener 异常隔离输出: afterCommit 正常返回");
        assertEquals(Long.valueOf(101L), output.getOutboxRecordId(),
                "Listener 异常不应改变已完成的保存结果");
        verify(listener).onSaved(any(OutboxEventContext.class));
    }

    @Test
    public void testSaveRejectsNullMessageAfterTransactionValidation() {
        beginTransaction(false, true);

        KafkaOutboxException output = assertThrows(KafkaOutboxException.class,
                () -> engine.save(null), "空消息必须拒绝保存");

        assertEquals(ErrorCode.KAFKA_OUTBOX_007, output.getErrorCode(), "空消息必须返回消息非法错误码");
        verify(repository, never()).save(any(OutboxRecordEntity.class));
        assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size(),
                "校验失败不得注册提交后回调");
    }

    @Test
    public void testSaveGeneratesMessageIdForBlankValueWithoutWritingBack() {
        beginTransaction(false, true);
        KafkaPublishMessage<Object> input = validMessage("mock-payload");
        input.setMessageId("   ");
        ArgumentCaptor<OutboxRecordEntity> recordCaptor = ArgumentCaptor.forClass(OutboxRecordEntity.class);

        OutboxSaveResult output = engine.save(input);

        verify(repository).save(recordCaptor.capture());
        assertEquals("   ", input.getMessageId(), "自动生成 ID 不得改写调用方的空白值");
        assertNotNull(output.getMessageId(), "空白 messageId 必须生成稳定 ID");
        assertFalse(output.getMessageId().trim().isEmpty(), "生成 ID 不得为空白");
        assertEquals(output.getMessageId(), recordCaptor.getValue().getMessageId(),
                "保存记录必须使用返回的生成 ID");
    }

    @Test
    public void testSaveRejectsRepositoryNullIdWithoutRegisteringCallback() {
        beginTransaction(false, true);
        KafkaPublishMessage<Object> input = validMessage("mock-payload");
        when(repository.save(any(OutboxRecordEntity.class))).thenReturn(null);

        KafkaOutboxException output = assertThrows(KafkaOutboxException.class,
                () -> engine.save(input), "Repository 未返回主键必须终止保存");

        assertEquals(ErrorCode.KAFKA_OUTBOX_006, output.getErrorCode(), "空主键必须返回持久化失败错误码");
        verify(repository).save(any(OutboxRecordEntity.class));
        verify(listener, never()).onSaved(any(OutboxEventContext.class));
        assertEquals(0, TransactionSynchronizationManager.getSynchronizations().size(),
                "保存失败不得注册提交后回调");
    }

    private void beginTransaction(boolean readOnly, boolean bindSelectedDataSource) {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        TransactionSynchronizationManager.setCurrentTransactionReadOnly(readOnly);
        if (bindSelectedDataSource) {
            TransactionSynchronizationManager.bindResource(dataSource, new ConnectionHolder(connection));
        }
    }

    private KafkaPublishMessage<Object> validMessage(Object payload) {
        return KafkaPublishMessage.<Object>builder()
                .topic("mock-topic")
                .payload(payload)
                .build();
    }

    private String repeat(char value, int count) {
        char[] values = new char[count];
        Arrays.fill(values, value);
        return new String(values);
    }
}
