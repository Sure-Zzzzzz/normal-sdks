package io.github.surezzzzzz.sdk.messaging.kafka.consumer.handler;

import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ConsumerEventType;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorCode;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.constant.SimpleKafkaConsumerConstant;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.DeadLetterPublisher;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.ErrorHandlerDecision;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.ErrorHandlerOutcome;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.error.KafkaConsumerErrorHandler;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.exception.KafkaConsumerException;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyAcquireResult;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyAcquireStatus;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyChecker;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.idempotency.KafkaConsumerIdempotencyLease;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.listener.KafkaConsumerEventListener;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.model.KafkaConsumerRecord;
import io.github.surezzzzzz.sdk.messaging.kafka.consumer.support.KafkaConsumerStringHelper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.springframework.kafka.support.Acknowledgment;

import java.nio.charset.StandardCharsets;

/**
 * 消费处理器适配器。
 *
 * @author surezzzzzz
 */
@Slf4j
public class KafkaConsumerHandlerAdapter {

    private final KafkaConsumerHandler<String, String> handler;
    private final KafkaConsumerIdempotencyChecker idempotencyChecker;
    private final KafkaConsumerErrorHandler<String, String> errorHandler;
    private final DeadLetterPublisher deadLetterPublisher;
    private final KafkaConsumerEventListener eventListener;
    private final String datasourceKey;
    private final String groupId;

    public KafkaConsumerHandlerAdapter(KafkaConsumerHandler<String, String> handler,
                                       KafkaConsumerIdempotencyChecker idempotencyChecker,
                                       KafkaConsumerErrorHandler<String, String> errorHandler,
                                       DeadLetterPublisher deadLetterPublisher,
                                       KafkaConsumerEventListener eventListener,
                                       String datasourceKey) {
        this(handler, idempotencyChecker, errorHandler, deadLetterPublisher, eventListener, datasourceKey, null);
    }

    public KafkaConsumerHandlerAdapter(KafkaConsumerHandler<String, String> handler,
                                       KafkaConsumerIdempotencyChecker idempotencyChecker,
                                       KafkaConsumerErrorHandler<String, String> errorHandler,
                                       DeadLetterPublisher deadLetterPublisher,
                                       KafkaConsumerEventListener eventListener,
                                       String datasourceKey,
                                       String groupId) {
        this.handler = handler;
        this.idempotencyChecker = idempotencyChecker;
        this.errorHandler = errorHandler;
        this.deadLetterPublisher = deadLetterPublisher;
        this.eventListener = eventListener;
        this.datasourceKey = datasourceKey;
        this.groupId = groupId;
    }

    /**
     * 手动提交模式的消费入口。
     *
     * @param data           原始消息
     * @param acknowledgment offset 提交句柄
     */
    public void onManualCommitMessage(ConsumerRecord<String, String> data, Acknowledgment acknowledgment) {
        consume(data, acknowledgment);
    }

    private void consume(ConsumerRecord<String, String> data, Acknowledgment acknowledgment) {
        String messageId = resolveMessageId(data);
        KafkaConsumerRecord<String, String> record = KafkaConsumerRecord.of(data, messageId, datasourceKey,
                handler.resolveRegistrationId(data.topic()), acknowledgment);
        KafkaConsumerIdempotencyAcquireResult acquireResult = acquireSafe(record);
        if (acquireResult.getStatus() == KafkaConsumerIdempotencyAcquireStatus.COMPLETED) {
            fire(record, ConsumerEventType.IDEMPOTENT_REJECT, SimpleKafkaConsumerConstant.FIRST_ATTEMPT, null, null);
            record.acknowledge();
            return;
        }
        if (acquireResult.getStatus() == KafkaConsumerIdempotencyAcquireStatus.IN_PROGRESS) {
            throw idempotencyInProgress(record);
        }
        consumeAcquired(record, acquireResult.getLease());
    }

    private void consumeAcquired(KafkaConsumerRecord<String, String> record, KafkaConsumerIdempotencyLease lease) {
        int attempt = SimpleKafkaConsumerConstant.FIRST_ATTEMPT;
        while (true) {
            Exception cause;
            try {
                handler.handle(record);
            } catch (Exception e) {
                cause = e;
                if (cause instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                    releaseSafe(lease, record);
                    fire(record, ConsumerEventType.ERROR, attempt, ErrorCode.CONSUME_RETRYABLE, summary(cause));
                    throw retryInterrupted(record, cause, attempt);
                }
                ErrorHandlerDecision decision = resolveDecision(record, cause, attempt);
                if (decision.getOutcome() == ErrorHandlerOutcome.RETRY) {
                    fire(record, ConsumerEventType.RETRY, attempt, decision.getErrorCode(), summary(cause));
                    if (!sleepBackoff(decision.getBackoffMs(), record)) {
                        releaseSafe(lease, record);
                        fire(record, ConsumerEventType.ERROR, attempt, ErrorCode.CONSUME_RETRYABLE, summary(cause));
                        throw retryInterrupted(record, cause, attempt);
                    }
                    attempt++;
                    continue;
                }
                handleDeadLetter(record, lease, cause, attempt, decision);
                return;
            }
            completeAndAcknowledge(record, lease, ConsumerEventType.CONSUMED, attempt, null, null);
            return;
        }
    }

    private void handleDeadLetter(KafkaConsumerRecord<String, String> record, KafkaConsumerIdempotencyLease lease,
                                  Exception cause, int attempt, ErrorHandlerDecision decision) {
        boolean published = false;
        try {
            published = deadLetterPublisher.publish(record, cause, attempt, decision.getErrorCode());
        } catch (RuntimeException e) {
            log.error("死信投递抛异常：topic=[{}]，messageId=[{}]", record.getTopic(),
                    KafkaConsumerStringHelper.safeDisplay(record.getMessageId()), e);
        }
        if (published) {
            completeAndAcknowledge(record, lease, ConsumerEventType.DEAD_LETTER, attempt,
                    decision.getErrorCode(), summary(cause));
            return;
        }
        releaseSafe(lease, record);
        fire(record, ConsumerEventType.ERROR, attempt, ErrorCode.DEAD_LETTER_PUBLISH_FAILED, summary(cause));
        throw deadLetterPublishFailed(record, cause);
    }

    private void completeAndAcknowledge(KafkaConsumerRecord<String, String> record, KafkaConsumerIdempotencyLease lease,
                                        ConsumerEventType eventType, int attempt, String errorCode, String errorSummary) {
        if (!completeSafe(lease, record)) {
            throw idempotencyCheckFailed(record);
        }
        fire(record, eventType, attempt, errorCode, errorSummary);
        record.acknowledge();
    }

    private ErrorHandlerDecision resolveDecision(KafkaConsumerRecord<String, String> record,
                                                 Exception cause, int attempt) {
        try {
            ErrorHandlerDecision decision = errorHandler.onError(record, cause, attempt);
            if (decision != null && decision.getOutcome() != null
                    && (decision.getOutcome() != ErrorHandlerOutcome.RETRY
                    || decision.getBackoffMs() >= SimpleKafkaConsumerConstant.ZERO)) {
                return decision;
            }
        } catch (RuntimeException e) {
            log.error("消费错误处理器异常，转入死信：topic=[{}]", record.getTopic(), e);
        }
        return ErrorHandlerDecision.builder()
                .outcome(ErrorHandlerOutcome.DEAD_LETTER)
                .errorCode(ErrorCode.CONSUME_UNKNOWN)
                .retryable(false)
                .backoffMs(SimpleKafkaConsumerConstant.ZERO)
                .build();
    }

    private KafkaConsumerIdempotencyAcquireResult acquireSafe(KafkaConsumerRecord<String, String> record) {
        try {
            return idempotencyChecker.acquire(record.getMessageId(), datasourceKey, groupId);
        } catch (RuntimeException e) {
            log.warn("幂等领取异常，按未启用幂等放行：messageId=[{}]",
                    KafkaConsumerStringHelper.safeDisplay(record.getMessageId()), e);
            return KafkaConsumerIdempotencyAcquireResult.acquired(NoOpLease.INSTANCE);
        }
    }

    private boolean completeSafe(KafkaConsumerIdempotencyLease lease, KafkaConsumerRecord<String, String> record) {
        try {
            return lease.complete();
        } catch (RuntimeException e) {
            log.warn("幂等完成标记异常，消息不 ack：messageId=[{}]",
                    KafkaConsumerStringHelper.safeDisplay(record.getMessageId()), e);
            return false;
        }
    }

    private void releaseSafe(KafkaConsumerIdempotencyLease lease, KafkaConsumerRecord<String, String> record) {
        try {
            if (!lease.release()) {
                log.warn("幂等租约未释放，等待租约过期后重投：messageId=[{}]",
                        KafkaConsumerStringHelper.safeDisplay(record.getMessageId()));
            }
        } catch (RuntimeException e) {
            log.warn("幂等租约释放异常，等待租约过期后重投：messageId=[{}]",
                    KafkaConsumerStringHelper.safeDisplay(record.getMessageId()), e);
        }
    }

    private KafkaConsumerException idempotencyInProgress(KafkaConsumerRecord<String, String> record) {
        String message = String.format(ErrorMessage.IDEMPOTENCY_IN_PROGRESS, record.getTopic(),
                KafkaConsumerStringHelper.safeDisplay(record.getMessageId()));
        return new KafkaConsumerException(ErrorCode.IDEMPOTENCY_IN_PROGRESS, message);
    }

    private KafkaConsumerException idempotencyCheckFailed(KafkaConsumerRecord<String, String> record) {
        String message = String.format(ErrorMessage.IDEMPOTENCY_CHECK_FAILED, record.getTopic(),
                KafkaConsumerStringHelper.safeDisplay(record.getMessageId()));
        return new KafkaConsumerException(ErrorCode.IDEMPOTENCY_CHECK_FAILED, message);
    }

    private KafkaConsumerException deadLetterPublishFailed(KafkaConsumerRecord<String, String> record,
                                                           Exception cause) {
        String message = String.format(ErrorMessage.DEAD_LETTER_PUBLISH_FAILED, record.getTopic(),
                KafkaConsumerStringHelper.safeDisplay(record.getMessageId()), record.getTopic());
        return new KafkaConsumerException(ErrorCode.DEAD_LETTER_PUBLISH_FAILED, message, cause);
    }

    private KafkaConsumerException retryInterrupted(KafkaConsumerRecord<String, String> record, Exception cause,
                                                    int attempt) {
        String message = String.format(ErrorMessage.CONSUME_RETRYABLE, record.getTopic(),
                KafkaConsumerStringHelper.safeDisplay(record.getMessageId()), attempt);
        return new KafkaConsumerException(ErrorCode.CONSUME_RETRYABLE, message, cause);
    }

    private boolean sleepBackoff(long backoffMs, KafkaConsumerRecord<String, String> record) {
        if (backoffMs <= SimpleKafkaConsumerConstant.ZERO) {
            return true;
        }
        try {
            Thread.sleep(backoffMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("重试退避被中断，停止重试，消息不 ack 等待重投：topic=[{}]，messageId=[{}]",
                    record.getTopic(), KafkaConsumerStringHelper.safeDisplay(record.getMessageId()));
            return false;
        }
    }

    private void fire(KafkaConsumerRecord<String, String> record, ConsumerEventType type, int attempt,
                      String errorCode, String errorSummary) {
        if (eventListener == null) {
            return;
        }
        try {
            eventListener.onEvent(record.toEventContext(type, attempt, errorCode, errorSummary));
        } catch (RuntimeException e) {
            log.warn("事件监听器回调异常，忽略：type=[{}]", type, e);
        }
    }

    private String resolveMessageId(ConsumerRecord<String, String> data) {
        String messageId = KafkaConsumerStringHelper.trimToNull(readHeader(data, SimpleKafkaConsumerConstant.HEADER_MESSAGE_ID));
        return messageId != null ? messageId : String.format(SimpleKafkaConsumerConstant.MESSAGE_ID_FALLBACK_TEMPLATE,
                data.topic(), data.partition(), data.offset());
    }

    private String readHeader(ConsumerRecord<String, String> data, String name) {
        Headers headers = data.headers();
        if (headers == null) {
            return null;
        }
        Header header = headers.lastHeader(name);
        return header == null || header.value() == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private String summary(Exception cause) {
        return cause == null ? null : KafkaConsumerStringHelper.safeForErrorMessage(cause.getMessage());
    }

    private enum NoOpLease implements KafkaConsumerIdempotencyLease {
        INSTANCE;

        @Override
        public boolean complete() {
            return true;
        }

        @Override
        public boolean release() {
            return true;
        }
    }
}
