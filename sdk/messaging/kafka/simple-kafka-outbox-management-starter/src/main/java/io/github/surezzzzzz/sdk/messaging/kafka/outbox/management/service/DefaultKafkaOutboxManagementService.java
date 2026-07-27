package io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.service;

import io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant.OutboxStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.configuration.SimpleKafkaOutboxManagementProperties;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception.KafkaOutboxManagementException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception.KafkaOutboxRecordNotFoundException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.exception.KafkaOutboxRecordStateConflictException;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.model.query.OutboxRecordBrowseQuery;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.model.view.OutboxRecordCursorPage;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.model.view.OutboxRecordDetailView;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.model.view.OutboxRecordListItemView;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.model.view.OutboxStatusSummaryView;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.repository.KafkaOutboxManagementRepository;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.management.support.KafkaOutboxManagementCursorHelper;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.model.OutboxRecord;
import io.github.surezzzzzz.sdk.messaging.kafka.outbox.support.KafkaOutboxStringHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 默认 Outbox 管理服务。
 *
 * @author surezzzzzz
 */
public class DefaultKafkaOutboxManagementService implements KafkaOutboxManagementService {
    private final KafkaOutboxManagementRepository repository;
    private final SimpleKafkaOutboxManagementProperties properties;

    public DefaultKafkaOutboxManagementService(KafkaOutboxManagementRepository repository,
                                               SimpleKafkaOutboxManagementProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Override
    public List<OutboxStatusSummaryView> summaries() {
        Map<OutboxStatus, Long> counts = repository.countByStatus();
        List<OutboxStatusSummaryView> result = new ArrayList<OutboxStatusSummaryView>();
        for (OutboxStatus status : OutboxStatus.values()) {
            Long count = counts.get(status);
            result.add(OutboxStatusSummaryView.builder().status(status).count(count == null ? 0L : count).build());
        }
        return result;
    }

    @Override
    public OutboxRecordDetailView detail(Long recordId) {
        return detail(required(repository.findById(recordId)));
    }

    @Override
    public OutboxRecordDetailView detailByMessageId(String messageId) {
        return detail(required(repository.findByMessageId(messageId)));
    }

    @Override
    public OutboxRecordCursorPage browse(String statusCode, String cursor, Integer size) {
        OutboxStatus status = OutboxStatus.fromCode(statusCode);
        if (status == null) throw invalid();
        int pageSize = size == null ? properties.getPage().getDefaultSize() : size;
        if (pageSize < 1 || pageSize > properties.getPage().getMaxSize()) throw invalid();
        KafkaOutboxManagementCursorHelper.Cursor decoded = cursor == null ? null : KafkaOutboxManagementCursorHelper.decode(cursor, status);
        List<OutboxRecord> records = repository.browse(OutboxRecordBrowseQuery.builder().status(status)
                .cursorAvailableAt(decoded == null ? null : decoded.getAvailableAt()).cursorId(decoded == null ? null : decoded.getRecordId()).size(pageSize).build());
        boolean hasNext = records.size() > pageSize;
        int count = hasNext ? pageSize : records.size();
        List<OutboxRecordListItemView> views = new ArrayList<OutboxRecordListItemView>();
        for (int i = 0; i < count; i++) views.add(listItem(records.get(i)));
        String nextCursor = hasNext ? KafkaOutboxManagementCursorHelper.encode(status, records.get(count - 1).getAvailableAt(), records.get(count - 1).getRecordId()) : null;
        return OutboxRecordCursorPage.builder().records(views).hasNext(hasNext).nextCursor(nextCursor).build();
    }

    @Override
    public void resetPoison(Long recordId) {
        if (repository.resetPoison(recordId) == 1) return;
        OutboxRecord record = repository.findById(recordId);
        if (record == null)
            throw new KafkaOutboxRecordNotFoundException(ErrorCode.RECORD_NOT_FOUND, ErrorMessage.RECORD_NOT_FOUND);
        throw new KafkaOutboxRecordStateConflictException(ErrorCode.RECORD_STATE_CONFLICT, ErrorMessage.RECORD_STATE_CONFLICT);
    }

    private OutboxRecord required(OutboxRecord record) {
        if (record == null)
            throw new KafkaOutboxRecordNotFoundException(ErrorCode.RECORD_NOT_FOUND, ErrorMessage.RECORD_NOT_FOUND);
        return record;
    }

    private OutboxRecordListItemView listItem(OutboxRecord record) {
        return OutboxRecordListItemView.builder().recordId(record.getRecordId()).messageId(safe(record.getMessageId()))
                .topic(safe(record.getTopic())).status(record.getStatus()).attempt(record.getAttempt()).availableAt(record.getAvailableAt()).lastErrorSummary(KafkaOutboxStringHelper.truncateErrorSummary(record.getLastErrorSummary())).build();
    }

    private OutboxRecordDetailView detail(OutboxRecord record) {
        return OutboxRecordDetailView.builder().recordId(record.getRecordId()).messageId(safe(record.getMessageId())).topic(safe(record.getTopic()))
                .routeKey(safe(record.getRouteKey())).datasourceKey(safe(record.getDatasourceKey())).messageType(safe(record.getMessageType())).payloadKind(record.getPayloadKind()).status(record.getStatus()).attempt(record.getAttempt())
                .availableAt(record.getAvailableAt()).lastErrorCode(safe(record.getLastErrorCode())).lastErrorSummary(KafkaOutboxStringHelper.truncateErrorSummary(record.getLastErrorSummary()))
                .brokerTopic(safe(record.getBrokerTopic())).brokerPartition(record.getBrokerPartition()).brokerOffset(record.getBrokerOffset()).brokerTimestamp(record.getBrokerTimestamp()).createdAt(record.getCreatedAt()).sentAt(record.getSentAt()).updatedAt(record.getUpdatedAt()).build();
    }

    private String safe(String value) {
        return value == null ? null : KafkaOutboxStringHelper.safeDisplay(value);
    }

    private KafkaOutboxManagementException invalid() {
        return new KafkaOutboxManagementException(ErrorCode.REQUEST_INVALID, ErrorMessage.REQUEST_INVALID);
    }
}
