package io.github.surezzzzzz.sdk.audit.search.elasticsearch.listener;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.annotation.SimpleElasticsearchAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.constant.SimpleElasticsearchAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.handler.EsAuditHandler;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.model.EsAuditRecord;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider.EsAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider.EsAuditUserProvider;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.event.EventListener;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * ES Audit Event Listener
 *
 * <p>Listens to {@link EsQueryEvent}, {@link EsAggEvent}, {@link EsQueryErrorEvent} and
 * {@link EsAggErrorEvent}, converts them to {@link EsAuditRecord} and dispatches to
 * registered handlers.
 *
 * <p>User info is collected synchronously in the request thread (before async dispatch),
 * so that {@link EsAuditUserProvider} can safely access request-scoped context.
 * Handler invocation is offloaded to the dedicated {@code esAuditExecutor} thread pool.
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchAuditListenerComponent
@ConditionalOnBean(EsAuditHandler.class)
public class EsAuditEventListener {

    private final List<EsAuditHandler> auditHandlers;
    private final EsAuditUserProvider userProvider;
    private final EsAuditTraceIdProvider traceIdProvider;
    private final Executor executor;

    public EsAuditEventListener(
            List<EsAuditHandler> auditHandlers,
            @Autowired(required = false) EsAuditUserProvider userProvider,
            @Autowired(required = false) EsAuditTraceIdProvider traceIdProvider,
            @Qualifier(SimpleElasticsearchAuditListenerConstant.EXECUTOR_BEAN_NAME) Executor executor) {
        this.auditHandlers = auditHandlers;
        this.userProvider = userProvider;
        this.traceIdProvider = traceIdProvider;
        this.executor = executor;
        log.info("EsAuditEventListener initialized with {} handler(s)", auditHandlers.size());
        if (userProvider == null) {
            log.warn("No EsAuditUserProvider found, user info will be null in audit records");
        }
    }

    @EventListener
    public void onEsQueryEvent(EsQueryEvent event) {
        try {
            EsAuditRecord record = buildQueryRecord(event);
            executor.execute(() -> invokeHandlers(record));
        } catch (Exception e) {
            log.error("Failed to handle EsQueryEvent", e);
        }
    }

    @EventListener
    public void onEsAggEvent(EsAggEvent event) {
        try {
            EsAuditRecord record = buildAggRecord(event);
            executor.execute(() -> invokeHandlers(record));
        } catch (Exception e) {
            log.error("Failed to handle EsAggEvent", e);
        }
    }

    @EventListener
    public void onEsQueryErrorEvent(EsQueryErrorEvent event) {
        try {
            EsAuditRecord record = buildQueryErrorRecord(event);
            executor.execute(() -> invokeHandlers(record));
        } catch (Exception e) {
            log.error("Failed to handle EsQueryErrorEvent", e);
        }
    }

    @EventListener
    public void onEsAggErrorEvent(EsAggErrorEvent event) {
        try {
            EsAuditRecord record = buildAggErrorRecord(event);
            executor.execute(() -> invokeHandlers(record));
        } catch (Exception e) {
            log.error("Failed to handle EsAggErrorEvent", e);
        }
    }

    private void invokeHandlers(EsAuditRecord record) {
        for (EsAuditHandler handler : auditHandlers) {
            try {
                handler.handle(record);
            } catch (Exception e) {
                log.error("Handler {} failed", handler.getClass().getSimpleName(), e);
            }
        }
    }

    private EsAuditRecord buildQueryRecord(EsQueryEvent event) {
        return EsAuditRecord.builder()
                .clientId(userProvider != null ? safeGet(() -> userProvider.getClientId()) : null)
                .clientType(userProvider != null ? safeGet(() -> userProvider.getClientType()) : null)
                .userId(userProvider != null ? safeGet(() -> userProvider.getUserId()) : null)
                .username(userProvider != null ? safeGet(() -> userProvider.getUsername()) : null)
                .indexAlias(event.getRequest().getIndex())
                .actualIndices(event.getContext().getActualIndices())
                .datasource(event.getContext().getDatasource())
                .queryCondition(event.getRequest().getQuery() != null ? event.getRequest().getQuery().toString() : null)
                .total(event.getResponse().getTotal())
                .returnedSize(event.getResponse().getItems() != null ? event.getResponse().getItems().size() : 0)
                .took(event.getResponse().getTook())
                .result("success")
                .downgradeLevel(event.getContext().getDowngradeLevel())
                .sourceType(event.getContext().getSourceType())
                .timestamp(event.getTimestamp())
                .traceId(traceIdProvider != null ? safeGet(() -> traceIdProvider.getTraceId()) : null)
                .build();
    }

    private EsAuditRecord buildAggRecord(EsAggEvent event) {
        return EsAuditRecord.builder()
                .clientId(userProvider != null ? safeGet(() -> userProvider.getClientId()) : null)
                .clientType(userProvider != null ? safeGet(() -> userProvider.getClientType()) : null)
                .userId(userProvider != null ? safeGet(() -> userProvider.getUserId()) : null)
                .username(userProvider != null ? safeGet(() -> userProvider.getUsername()) : null)
                .indexAlias(event.getRequest().getIndex())
                .actualIndices(event.getContext().getActualIndices())
                .datasource(event.getContext().getDatasource())
                .queryCondition(event.getRequest().getAggs() != null ? event.getRequest().getAggs().toString() : null)
                .total(null)
                .returnedSize(event.getResponse().getAggregations() != null ? event.getResponse().getAggregations().size() : 0)
                .took(event.getResponse().getTook())
                .result("success")
                .downgradeLevel(event.getContext().getDowngradeLevel())
                .sourceType(event.getContext().getSourceType())
                .timestamp(event.getTimestamp())
                .traceId(traceIdProvider != null ? safeGet(() -> traceIdProvider.getTraceId()) : null)
                .build();
    }

    private EsAuditRecord buildQueryErrorRecord(EsQueryErrorEvent event) {
        return EsAuditRecord.builder()
                .clientId(userProvider != null ? safeGet(() -> userProvider.getClientId()) : null)
                .clientType(userProvider != null ? safeGet(() -> userProvider.getClientType()) : null)
                .userId(userProvider != null ? safeGet(() -> userProvider.getUserId()) : null)
                .username(userProvider != null ? safeGet(() -> userProvider.getUsername()) : null)
                .indexAlias(event.getRequest().getIndex())
                .actualIndices(null)
                .datasource(event.getDatasource())
                .queryCondition(event.getRequest().getQuery() != null ? event.getRequest().getQuery().toString() : null)
                .total(null)
                .returnedSize(null)
                .took(null)
                .result("failure")
                .downgradeLevel(0)
                .sourceType(event.getSourceType())
                .errorMessage(event.getError() != null ? event.getError().getMessage() : null)
                .timestamp(event.getTimestamp())
                .traceId(traceIdProvider != null ? safeGet(() -> traceIdProvider.getTraceId()) : null)
                .build();
    }

    private EsAuditRecord buildAggErrorRecord(EsAggErrorEvent event) {
        return EsAuditRecord.builder()
                .clientId(userProvider != null ? safeGet(() -> userProvider.getClientId()) : null)
                .clientType(userProvider != null ? safeGet(() -> userProvider.getClientType()) : null)
                .userId(userProvider != null ? safeGet(() -> userProvider.getUserId()) : null)
                .username(userProvider != null ? safeGet(() -> userProvider.getUsername()) : null)
                .indexAlias(event.getRequest().getIndex())
                .actualIndices(null)
                .datasource(event.getDatasource())
                .queryCondition(event.getRequest().getAggs() != null ? event.getRequest().getAggs().toString() : null)
                .total(null)
                .returnedSize(null)
                .took(null)
                .result("failure")
                .downgradeLevel(0)
                .sourceType(event.getSourceType())
                .errorMessage(event.getError() != null ? event.getError().getMessage() : null)
                .timestamp(event.getTimestamp())
                .traceId(traceIdProvider != null ? safeGet(() -> traceIdProvider.getTraceId()) : null)
                .build();
    }

    private String safeGet(ProviderSupplier supplier) {
        if (supplier == null) {
            return null;
        }
        try {
            return supplier.get();
        } catch (Exception e) {
            log.debug("Failed to get value from provider", e);
            return null;
        }
    }

    @FunctionalInterface
    private interface ProviderSupplier {
        String get();
    }
}
