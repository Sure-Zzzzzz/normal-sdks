package io.github.surezzzzzz.sdk.messaging.kafka.outbox.engine;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.*;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.entity.OutboxRecordEntity;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.exception.KafkaOutboxException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.listener.KafkaOutboxEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxEventContext;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxSaveResult;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.repository.KafkaOutboxRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.serializer.KafkaOutboxMessageSerializer;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.support.KafkaOutboxStringHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.trace.KafkaOutboxTraceSnapshotResolver;
import io.github.surezzzzzz.sdk.messaging.kafka.publisher.model.KafkaPublishMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 默认 Kafka Outbox Engine
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultKafkaOutboxEngine implements KafkaOutboxEngine {

    private final DataSource dataSource;
    /**
     * 持久化 SPI
     */
    private final KafkaOutboxRepository repository;
    /**
     * 消息快照序列化器
     */
    private final KafkaOutboxMessageSerializer serializer;
    /**
     * traceId 快照解析器
     */
    private final KafkaOutboxTraceSnapshotResolver traceSnapshotResolver;
    /**
     * 事件 Listener
     */
    private final KafkaOutboxEventListener listener;

    /**
     * 创建默认 Engine
     *
     * @param dataSource            选中的业务 DataSource
     * @param repository            Repository
     * @param serializer            快照序列化器
     * @param traceSnapshotResolver traceId 解析器
     * @param listener              事件 Listener
     */
    public DefaultKafkaOutboxEngine(DataSource dataSource, KafkaOutboxRepository repository,
                                    KafkaOutboxMessageSerializer serializer,
                                    KafkaOutboxTraceSnapshotResolver traceSnapshotResolver,
                                    KafkaOutboxEventListener listener) {
        this.dataSource = dataSource;
        this.repository = repository;
        this.serializer = serializer;
        this.traceSnapshotResolver = traceSnapshotResolver;
        this.listener = listener;
    }

    /**
     * 在当前活跃事务中保存消息快照
     *
     * @param message 待发布消息
     * @param <T>     payload 类型
     * @return 保存结果
     */
    @Override
    public <T> OutboxSaveResult save(KafkaPublishMessage<T> message) {
        validateTransaction();
        validateMessage(message);
        String messageId = KafkaOutboxStringHelper.hasText(message.getMessageId())
                ? message.getMessageId() : UUID.randomUUID().toString();
        validateMessageId(messageId);
        String topic = message.getTopic();
        Map<String, String> headers = message.getHeaders() == null ? null
                : new LinkedHashMap<>(message.getHeaders());
        Map<String, Object> attributes = message.getAttributes() == null ? null
                : new LinkedHashMap<>(message.getAttributes());
        Object payload = message.getPayload();
        OutboxPayloadKind payloadKind = payload == null ? OutboxPayloadKind.NULL
                : payload instanceof String ? OutboxPayloadKind.STRING : OutboxPayloadKind.JSON;
        OutboxRecordEntity record = OutboxRecordEntity.builder()
                .messageId(messageId)
                .topic(topic)
                .recordKey(message.getKey())
                .routeKey(message.getRouteKey())
                .datasourceKey(message.getDatasourceKey())
                .partition(message.getPartition())
                .messageTimestamp(message.getTimestamp())
                .messageType(message.getMessageType())
                .payloadKind(payloadKind.getCode())
                .payloadJson(serializer.serializePayload(payload))
                .headersJson(serializer.serializeStringMap(headers))
                .attributesJson(serializer.serializeObjectMap(attributes))
                .envelopeEnabled(message.getEnvelopeEnabled())
                .traceId(KafkaOutboxStringHelper.trimToNull(traceSnapshotResolver.resolveTraceId()))
                .schemaVersion(SimpleKafkaOutboxConstant.SCHEMA_VERSION)
                .status(OutboxStatus.PENDING.getCode())
                .attempt(SimpleKafkaOutboxConstant.ZERO)
                .version(SimpleKafkaOutboxConstant.ZERO_LONG)
                .build();
        Long recordId = repository.save(record);
        if (recordId == null) {
            throw new KafkaOutboxException(ErrorCode.KAFKA_OUTBOX_006, ErrorMessage.KAFKA_OUTBOX_006);
        }
        record.setId(recordId);
        OutboxEventContext savedContext = toEvent(record);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                notifySaved(savedContext);
            }
        });
        return new OutboxSaveResult(recordId, messageId);
    }

    /**
     * 校验当前线程存在可写且绑定选中 DataSource 的活跃事务。
     */
    private void validateTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw transactionInvalid(SimpleKafkaOutboxConstant.REASON_TRANSACTION_INACTIVE);
        }
        if (TransactionSynchronizationManager.isCurrentTransactionReadOnly()) {
            throw transactionInvalid(SimpleKafkaOutboxConstant.REASON_TRANSACTION_READ_ONLY);
        }
        if (!TransactionSynchronizationManager.hasResource(dataSource)) {
            throw transactionInvalid(SimpleKafkaOutboxConstant.REASON_TRANSACTION_RESOURCE_MISSING);
        }
        Connection connection = null;
        try {
            connection = DataSourceUtils.getConnection(dataSource);
        } catch (RuntimeException e) {
            throw transactionInvalid(SimpleKafkaOutboxConstant.REASON_TRANSACTION_RESOURCE_MISSING);
        } finally {
            if (connection != null) {
                DataSourceUtils.releaseConnection(connection, dataSource);
            }
        }
    }

    /**
     * 校验消息非空、topic 必填、分区与时间戳非负。
     */
    private void validateMessage(KafkaPublishMessage<?> message) {
        if (message == null) {
            throw new KafkaOutboxException(ErrorCode.KAFKA_OUTBOX_007,
                    String.format(ErrorMessage.KAFKA_OUTBOX_007, SimpleKafkaOutboxConstant.REASON_MESSAGE_EMPTY));
        }
        if (!KafkaOutboxStringHelper.hasText(message.getTopic())) {
            throw new KafkaOutboxException(ErrorCode.KAFKA_OUTBOX_007,
                    String.format(ErrorMessage.KAFKA_OUTBOX_007, SimpleKafkaOutboxConstant.REASON_TOPIC_EMPTY));
        }
        if (message.getPartition() != null && message.getPartition() < SimpleKafkaOutboxConstant.ZERO) {
            throw new KafkaOutboxException(ErrorCode.KAFKA_OUTBOX_007,
                    String.format(ErrorMessage.KAFKA_OUTBOX_007, SimpleKafkaOutboxConstant.REASON_PARTITION_INVALID));
        }
        if (message.getTimestamp() != null && message.getTimestamp() < SimpleKafkaOutboxConstant.ZERO_LONG) {
            throw new KafkaOutboxException(ErrorCode.KAFKA_OUTBOX_007,
                    String.format(ErrorMessage.KAFKA_OUTBOX_007, SimpleKafkaOutboxConstant.REASON_TIMESTAMP_INVALID));
        }
    }

    /**
     * 校验 messageId 非空且长度不超过 191。
     */
    private void validateMessageId(String messageId) {
        if (!KafkaOutboxStringHelper.hasText(messageId)
                || messageId.length() > SimpleKafkaOutboxConstant.MAX_MESSAGE_ID_LENGTH) {
            throw new KafkaOutboxException(ErrorCode.KAFKA_OUTBOX_007,
                    String.format(ErrorMessage.KAFKA_OUTBOX_007, SimpleKafkaOutboxConstant.REASON_MESSAGE_ID_INVALID));
        }
    }

    /**
     * 构造事务无效异常。
     */
    private KafkaOutboxException transactionInvalid(String reason) {
        return new KafkaOutboxException(ErrorCode.KAFKA_OUTBOX_003,
                String.format(ErrorMessage.KAFKA_OUTBOX_003, reason));
    }

    /**
     * 将保存后的记录转换为脱敏事件上下文。
     */
    private OutboxEventContext toEvent(OutboxRecordEntity record) {
        return OutboxEventContext.builder()
                .recordId(record.getId())
                .messageId(record.getMessageId())
                .status(record.getStatus())
                .attempt(record.getAttempt())
                .schemaVersion(record.getSchemaVersion())
                .topic(KafkaOutboxStringHelper.safeDisplay(record.getTopic()))
                .datasourceKey(KafkaOutboxStringHelper.safeDisplay(record.getDatasourceKey()))
                .build();
    }

    /**
     * 事务提交后通知 onSaved，Listener 异常不影响提交结果。
     */
    private void notifySaved(OutboxEventContext context) {
        try {
            listener.onSaved(context);
        } catch (RuntimeException e) {
            log.warn("Kafka Outbox Listener onSaved 执行失败，recordId={}, messageId={}",
                    context.getRecordId(), context.getMessageId());
        }
    }
}
