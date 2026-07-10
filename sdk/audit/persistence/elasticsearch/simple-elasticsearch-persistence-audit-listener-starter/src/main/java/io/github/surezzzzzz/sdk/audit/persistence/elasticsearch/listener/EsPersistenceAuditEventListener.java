package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.listener;

import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.annotation.PersistenceAuditComponent;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.configuration.PersistenceAuditProperties;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.constant.PersistenceAuditConstant;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.handler.EsPersistenceAuditHandler;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.model.EsPersistenceAuditFailureRecord;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.model.EsPersistenceAuditRecord;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.provider.EsPersistenceAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.provider.EsPersistenceAuditUserProvider;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.event.EsPersistenceErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.event.EsPersistenceEvent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * ES Persistence 审计事件监听器
 *
 * @author surezzzzzz
 */
@Slf4j
@PersistenceAuditComponent
@ConditionalOnBean(EsPersistenceAuditHandler.class)
public class EsPersistenceAuditEventListener {

    private final List<EsPersistenceAuditHandler> auditHandlers;
    private final EsPersistenceAuditUserProvider userProvider;
    private final EsPersistenceAuditTraceIdProvider traceIdProvider;
    private final PersistenceAuditProperties properties;
    private final Executor executor;

    public EsPersistenceAuditEventListener(
            List<EsPersistenceAuditHandler> auditHandlers,
            @Autowired(required = false) EsPersistenceAuditUserProvider userProvider,
            @Autowired(required = false) EsPersistenceAuditTraceIdProvider traceIdProvider,
            PersistenceAuditProperties properties,
            @Qualifier(PersistenceAuditConstant.EXECUTOR_BEAN_NAME) Executor executor) {
        this.auditHandlers = auditHandlers;
        this.userProvider = userProvider;
        this.traceIdProvider = traceIdProvider;
        this.properties = properties;
        this.executor = executor;
        log.info("EsPersistenceAuditEventListener initialized with {} handler(s)", auditHandlers.size());
    }

    @EventListener
    public void onEsPersistenceEvent(EsPersistenceEvent event) {
        try {
            EsPersistenceAuditRecord record = buildSuccessRecord(event);
            executor.execute(() -> invokeHandlers(record));
        } catch (Exception e) {
            log.error("Failed to handle EsPersistenceEvent", e);
        }
    }

    @EventListener
    public void onEsPersistenceErrorEvent(EsPersistenceErrorEvent event) {
        try {
            EsPersistenceAuditRecord record = buildErrorRecord(event);
            executor.execute(() -> invokeHandlers(record));
        } catch (Exception e) {
            log.error("Failed to handle EsPersistenceErrorEvent", e);
        }
    }

    private void invokeHandlers(EsPersistenceAuditRecord record) {
        for (EsPersistenceAuditHandler handler : auditHandlers) {
            try {
                handler.handle(record);
            } catch (Exception e) {
                log.error("EsPersistenceAuditHandler {} failed", handler.getClass().getSimpleName(), e);
            }
        }
    }

    private EsPersistenceAuditRecord buildSuccessRecord(EsPersistenceEvent event) {
        EsPersistenceAuditRecord.EsPersistenceAuditRecordBuilder builder = buildBaseRecord(event.getRequest(), event.getContext());
        Object result = event.getResult();
        if (result instanceof PersistenceResult) {
            fillPersistenceResult(builder, (PersistenceResult) result);
        } else if (result instanceof BulkResult) {
            fillBulkResult(builder, (BulkResult) result);
        } else if (result instanceof ByQueryTaskResult) {
            fillByQueryTaskResult(builder, (ByQueryTaskResult) result);
        } else {
            builder.result(PersistenceAuditConstant.RESULT_SUCCESS).success(Boolean.TRUE);
        }
        return builder.build();
    }

    private EsPersistenceAuditRecord buildErrorRecord(EsPersistenceErrorEvent event) {
        Throwable error = event.getError();
        return buildBaseRecord(event.getRequest(), event.getContext())
                .result(PersistenceAuditConstant.RESULT_FAILURE)
                .success(Boolean.FALSE)
                .partial(Boolean.FALSE)
                .conflict(Boolean.FALSE)
                .errorCode(extractErrorCode(error))
                .errorClass(error != null ? error.getClass().getName() : null)
                .errorMessage(error != null ? error.getMessage() : null)
                .build();
    }

    private EsPersistenceAuditRecord.EsPersistenceAuditRecordBuilder buildBaseRecord(PersistenceRequest request,
                                                                                     PersistenceExecutionContext context) {
        return EsPersistenceAuditRecord.builder()
                .clientId(userProvider != null ? safeGet(() -> userProvider.getClientId()) : null)
                .clientType(userProvider != null ? safeGet(() -> userProvider.getClientType()) : null)
                .userId(userProvider != null ? safeGet(() -> userProvider.getUserId()) : null)
                .username(userProvider != null ? safeGet(() -> userProvider.getUsername()) : null)
                .traceId(traceIdProvider != null ? safeGet(() -> traceIdProvider.getTraceId()) : null)
                .timestamp(System.currentTimeMillis())
                .sourceType(PersistenceAuditConstant.SOURCE_TYPE)
                .operationType(resolveOperationType(context))
                .requestType(request != null ? request.getClass().getSimpleName() : null)
                .index(resolveIndex(request, context))
                .datasource(context != null ? context.getDatasource() : null)
                .documentId(resolveDocumentId(request))
                .clientAsync(context != null ? context.isClientAsync() : null)
                .routeAsyncWrite(context != null ? context.isRouteAsyncWrite() : null)
                .serverAsyncTask(context != null ? context.isServerAsyncTask() : null)
                .taskId(context != null ? context.getTaskId() : null)
                .startTimeMs(context != null ? context.getStartTimeMs() : null)
                .tookMs(context != null ? context.getTookMs() : null);
    }

    private void fillPersistenceResult(EsPersistenceAuditRecord.EsPersistenceAuditRecordBuilder builder,
                                       PersistenceResult result) {
        builder.result(result.isSuccess() ? PersistenceAuditConstant.RESULT_SUCCESS
                        : PersistenceAuditConstant.RESULT_FAILURE)
                .success(result.isSuccess())
                .partial(Boolean.FALSE)
                .conflict(Boolean.FALSE)
                .index(result.getIndex())
                .datasource(result.getDatasource())
                .documentId(result.getId())
                .operationType(result.getOperationType() != null ? result.getOperationType().getCode() : null)
                .routeAsyncWrite(result.isAsyncRouted())
                .tookMs(result.getTookMs());
    }

    private void fillBulkResult(EsPersistenceAuditRecord.EsPersistenceAuditRecordBuilder builder, BulkResult result) {
        List<EsPersistenceAuditFailureRecord> failureList = buildBulkFailureList(result.getFailureList());
        boolean hasFailure = result.isHasFailure() || result.getFailed() > 0 || !failureList.isEmpty();
        boolean partial = Boolean.TRUE.equals(result.getPartial()) || hasFailure;
        builder.result(resolveBatchResult(result.isSuccess(), partial))
                .success(result.isSuccess())
                .partial(partial)
                .conflict(hasConflict(failureList))
                .datasource(result.getDatasource())
                .bulkItemCount(result.getTotal())
                .bulkSucceeded(result.getSucceeded())
                .bulkFailed(result.getFailed())
                .batchTotal(result.getBatchTotal())
                .batchSucceeded(result.getBatchSucceeded())
                .batchFailed(result.getBatchFailed())
                .tookMs(result.getTookMs())
                .failureList(failureList);
    }

    private void fillByQueryTaskResult(EsPersistenceAuditRecord.EsPersistenceAuditRecordBuilder builder,
                                       ByQueryTaskResult result) {
        List<EsPersistenceAuditFailureRecord> failureList = buildByQueryFailureList(result.getFailureList());
        boolean taskSubmitted = !result.isCompleted();
        boolean conflict = result.getVersionConflicts() > 0 || hasConflict(failureList);
        boolean partial = !failureList.isEmpty() || conflict;
        builder.result(taskSubmitted ? PersistenceAuditConstant.RESULT_TASK_SUBMITTED
                        : resolveBatchResult(failureList.isEmpty(), partial))
                .success(taskSubmitted || failureList.isEmpty())
                .partial(partial)
                .conflict(conflict)
                .datasource(result.getDatasource())
                .index(result.getIndex())
                .serverAsyncTask(taskSubmitted)
                .taskId(result.getTaskId())
                .total(result.getTotal())
                .updated(result.getUpdated())
                .deleted(result.getDeleted())
                .versionConflicts(result.getVersionConflicts())
                .tookMs(result.getTookMs())
                .failureList(failureList);
    }

    private List<EsPersistenceAuditFailureRecord> buildBulkFailureList(List<BulkItemFailure> failureList) {
        if (failureList == null || failureList.isEmpty()) {
            return Collections.emptyList();
        }
        int limit = resolveFailureLimit();
        List<EsPersistenceAuditFailureRecord> records = new ArrayList<>();
        for (BulkItemFailure failure : failureList) {
            if (records.size() >= limit) {
                break;
            }
            records.add(EsPersistenceAuditFailureRecord.builder()
                    .itemIndex(failure.getItemIndex())
                    .type(failure.getType() != null ? failure.getType().name() : null)
                    .index(failure.getIndex())
                    .id(failure.getId())
                    .status(failure.getStatus())
                    .errorCode(failure.getErrorCode())
                    .errorType(failure.getErrorType())
                    .errorReason(resolveFailureReason(failure.getErrorReason(), failure.getErrorMessage()))
                    .retryable(failure.getRetryable())
                    .conflict(isConflict(failure.getStatus()))
                    .build());
        }
        return records;
    }

    private List<EsPersistenceAuditFailureRecord> buildByQueryFailureList(List<ByQueryFailure> failureList) {
        if (failureList == null || failureList.isEmpty()) {
            return Collections.emptyList();
        }
        int limit = resolveFailureLimit();
        List<EsPersistenceAuditFailureRecord> records = new ArrayList<>();
        for (ByQueryFailure failure : failureList) {
            if (records.size() >= limit) {
                break;
            }
            String status = failure.getStatus();
            records.add(EsPersistenceAuditFailureRecord.builder()
                    .index(failure.getIndex())
                    .id(failure.getId())
                    .statusText(status)
                    .errorReason(failure.getCause())
                    .conflict(isConflict(status))
                    .build());
        }
        return records;
    }

    private int resolveFailureLimit() {
        int limit = properties.getRecord().getMaxFailureSize();
        return limit > 0 ? limit : PersistenceAuditConstant.DEFAULT_MAX_FAILURE_SIZE;
    }

    private String resolveBatchResult(boolean success, boolean partial) {
        if (success && !partial) {
            return PersistenceAuditConstant.RESULT_SUCCESS;
        }
        if (partial) {
            return PersistenceAuditConstant.RESULT_PARTIAL_FAILURE;
        }
        return PersistenceAuditConstant.RESULT_FAILURE;
    }

    private boolean hasConflict(List<EsPersistenceAuditFailureRecord> failureList) {
        if (failureList == null || failureList.isEmpty()) {
            return false;
        }
        for (EsPersistenceAuditFailureRecord failure : failureList) {
            if (Boolean.TRUE.equals(failure.getConflict())) {
                return true;
            }
        }
        return false;
    }

    private boolean isConflict(Integer status) {
        return status != null && status == PersistenceAuditConstant.HTTP_STATUS_CONFLICT;
    }

    private boolean isConflict(String status) {
        if (status == null) {
            return false;
        }
        return String.valueOf(PersistenceAuditConstant.HTTP_STATUS_CONFLICT).equals(status)
                || "conflict".equalsIgnoreCase(status)
                || status.toLowerCase().contains("version_conflict");
    }

    private String resolveFailureReason(String errorReason, String errorMessage) {
        return errorReason != null ? errorReason : errorMessage;
    }

    private String resolveOperationType(PersistenceExecutionContext context) {
        PersistenceOperationType operationType = context != null ? context.getOperationType() : null;
        return operationType != null ? operationType.getCode() : null;
    }

    private String resolveIndex(PersistenceRequest request, PersistenceExecutionContext context) {
        if (context != null && context.getIndex() != null) {
            return context.getIndex();
        }
        if (request instanceof IndexRequest) {
            return ((IndexRequest) request).getIndex();
        }
        if (request instanceof UpdateRequest) {
            return ((UpdateRequest) request).getIndex();
        }
        if (request instanceof DeleteRequest) {
            return ((DeleteRequest) request).getIndex();
        }
        if (request instanceof BulkRequest) {
            return ((BulkRequest) request).getDefaultIndex();
        }
        if (request instanceof UpdateByQueryRequest) {
            return ((UpdateByQueryRequest) request).getIndex();
        }
        if (request instanceof DeleteByQueryRequest) {
            return ((DeleteByQueryRequest) request).getIndex();
        }
        return null;
    }

    private String resolveDocumentId(PersistenceRequest request) {
        if (request instanceof IndexRequest) {
            return ((IndexRequest) request).getId();
        }
        if (request instanceof UpdateRequest) {
            return ((UpdateRequest) request).getId();
        }
        if (request instanceof DeleteRequest) {
            return ((DeleteRequest) request).getId();
        }
        return null;
    }

    private String extractErrorCode(Throwable error) {
        if (error instanceof SimpleElasticsearchPersistenceException) {
            return ((SimpleElasticsearchPersistenceException) error).getErrorCode();
        }
        return null;
    }

    private String safeGet(ProviderSupplier supplier) {
        if (supplier == null) {
            return null;
        }
        try {
            return supplier.get();
        } catch (Exception e) {
            log.debug("Failed to get value from persistence audit provider", e);
            return null;
        }
    }

    @FunctionalInterface
    private interface ProviderSupplier {
        String get();
    }
}
