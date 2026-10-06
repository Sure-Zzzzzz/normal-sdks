package io.github.surezzzzzz.sdk.audit.search.elasticsearch.listener;

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
import org.springframework.context.event.EventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * Search 审计事件转换器：在事件发布线程提取主体，专用执行器分发 Handler。
 * 不访问请求正文、查询条件或原始响应；脱敏事件缺失的统计值保持 null。
 */
@Slf4j
public class EsAuditEventListener {
    private final List<EsAuditHandler> auditHandlers;
    private final EsAuditUserProvider userProvider;
    private final EsAuditTraceIdProvider traceIdProvider;
    private final Executor executor;

    /**
     * 创建监听器；Provider 可为空，执行器由宿主或自动配置提供。
     */
    public EsAuditEventListener(List<EsAuditHandler> handlers, EsAuditUserProvider users,
                                EsAuditTraceIdProvider traces, Executor executor) {
        this.auditHandlers = new ArrayList<>(handlers);
        this.userProvider = users;
        this.traceIdProvider = traces;
        this.executor = executor;
    }

    /**
     * 处理查询和计数成功事件。
     */
    @EventListener
    public void onEsQueryEvent(EsQueryEvent event) {
        submit(() -> base(event.getTimestamp()).result(SimpleElasticsearchAuditListenerConstant.RESULT_SUCCESS)
                .indexAlias(event.getRequest() == null ? null : event.getRequest().getIndex())
                .countOnly(event.getRequest() == null ? null : event.getRequest().getCountOnly())
                .actualIndices(event.getContext() == null ? null : copy(event.getContext().getActualIndices()))
                .datasource(event.getContext() == null ? null : event.getContext().getDatasource())
                .downgradeLevel(event.getContext() == null ? null : event.getContext().getDowngradeLevel())
                .sourceType(event.getContext() == null ? null : event.getContext().getSourceType())
                .total(event.getResponse() == null ? null : event.getResponse().getTotal())
                .took(event.getResponse() == null ? null : event.getResponse().getTook())
                // items=null 表示事件未发布文档，不能当作实际返回零条。
                .returnedSize(event.getResponse() == null || event.getResponse().getItems() == null ? null : event.getResponse().getItems().size())
                .build());
    }

    /**
     * 处理聚合成功事件。
     */
    @EventListener
    public void onEsAggEvent(EsAggEvent event) {
        submit(() -> base(event.getTimestamp()).result(SimpleElasticsearchAuditListenerConstant.RESULT_SUCCESS)
                .indexAlias(event.getRequest() == null ? null : event.getRequest().getIndex())
                .actualIndices(event.getContext() == null ? null : copy(event.getContext().getActualIndices()))
                .datasource(event.getContext() == null ? null : event.getContext().getDatasource())
                .downgradeLevel(event.getContext() == null ? null : event.getContext().getDowngradeLevel())
                .sourceType(event.getContext() == null ? null : event.getContext().getSourceType())
                .took(event.getResponse() == null ? null : event.getResponse().getTook())
                .returnedSize(event.getResponse() == null || event.getResponse().getAggregations() == null ? null : event.getResponse().getAggregations().size())
                .build());
    }

    /**
     * 处理查询或计数失败事件。
     */
    @EventListener
    public void onEsQueryErrorEvent(EsQueryErrorEvent event) {
        submit(() -> base(event.getTimestamp()).result(SimpleElasticsearchAuditListenerConstant.RESULT_FAILURE)
                .indexAlias(event.getRequest() == null ? null : event.getRequest().getIndex())
                .datasource(event.getDatasource()).sourceType(event.getSourceType()).countOnly(event.isCountOnly())
                .downgradeLevel(event.getContext() == null ? null : event.getContext().getDowngradeLevel())
                .errorMessage(event.getError() == null ? null : event.getError().getMessage()).build());
    }

    /**
     * 处理聚合失败事件。
     */
    @EventListener
    public void onEsAggErrorEvent(EsAggErrorEvent event) {
        submit(() -> base(event.getTimestamp()).result(SimpleElasticsearchAuditListenerConstant.RESULT_FAILURE)
                .indexAlias(event.getRequest() == null ? null : event.getRequest().getIndex())
                .datasource(event.getDatasource()).sourceType(event.getSourceType())
                .downgradeLevel(event.getContext() == null ? null : event.getContext().getDowngradeLevel())
                .errorMessage(event.getError() == null ? null : event.getError().getMessage()).build());
    }

    private EsAuditRecord.EsAuditRecordBuilder base(long timestamp) {
        return EsAuditRecord.builder().timestamp(timestamp)
                .clientId(userProvider == null ? null : safeGet(userProvider::getClientId))
                .clientType(userProvider == null ? null : safeGet(userProvider::getClientType))
                .userId(userProvider == null ? null : safeGet(userProvider::getUserId))
                .username(userProvider == null ? null : safeGet(userProvider::getUsername))
                .traceId(traceIdProvider == null ? null : safeGet(traceIdProvider::getTraceId));
    }

    private void submit(Supplier<EsAuditRecord> conversion) {
        try {
            EsAuditRecord record = conversion.get();
            log.debug("Search 审计转换完成 result={} countOnly={}", record.getResult(), record.getCountOnly());
            executor.execute(() -> {
                for (EsAuditHandler handler : auditHandlers) {
                    try {
                        handler.handle(record.toBuilder().actualIndices(copy(record.getActualIndices())).build());
                        log.debug("Search 审计处理完成 handler={}", handler.getClass().getSimpleName());
                    } catch (Exception error) {
                        log.warn("Search 审计处理失败 handler={} category={}", handler.getClass().getSimpleName(), error.getClass().getSimpleName());
                    }
                }
            });
        } catch (Exception error) {
            // 异常消息可能包含宿主数据，只记录类型，不输出消息和堆栈。
            log.warn("Search 审计转换或提交失败 category={}", error.getClass().getSimpleName());
        }
    }

    private String safeGet(Supplier<String> supplier) {
        try {
            return supplier.get();
        } catch (Exception error) {
            log.debug("Search 审计主体提取失败 category={}", error.getClass().getSimpleName());
            return null;
        }
    }

    private String[] copy(String[] values) {
        return values == null ? null : values.clone();
    }
}
