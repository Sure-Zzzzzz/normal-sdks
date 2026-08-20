package io.github.surezzzzzz.sdk.ops.middleware.audit;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import io.github.surezzzzzz.sdk.ops.middleware.support.MiddlewareOpsLogHelper;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CompletableFuture;

/**
 * 基于 PersistenceEngine 的异步审计发布器。
 *
 * @author surezzzzzz
 */
@Slf4j
public class PersistenceEngineMiddlewareOpsAuditPublisher implements MiddlewareOpsAuditPublisher {

    private final PersistenceEngine persistenceEngine;

    /**
     * 创建异步审计发布器。
     *
     * @param persistenceEngine Elasticsearch 写侧入口
     */
    public PersistenceEngineMiddlewareOpsAuditPublisher(PersistenceEngine persistenceEngine) {
        this.persistenceEngine = persistenceEngine;
    }

    @Override
    public void publish(MiddlewareOpsAuditEvent event) {
        if (event == null || event.getRequestId() == null || event.getRequestId().trim().isEmpty()) {
            return;
        }
        IndexRequest request = IndexRequest.builder().index(SmartMiddlewareOpsServerConstant.AUDIT_WRITE_INDEX)
                .id(MiddlewareOpsAuditIndexDefinition.documentId(event.getRequestId()))
                .options(IndexOptions.builder().refresh(Boolean.TRUE).build())
                .document(MiddlewareOpsAuditIndexDefinition.document(event)).build();
        try {
            CompletableFuture<?> future = persistenceEngine.indexAsync(request);
            if (future != null) {
                future.whenComplete((result, throwable) -> {
                    if (throwable != null) {
                        log.warn("中间件运维审计异步写入失败，capability={}，status={}", event.getCapability(),
                                event.getStatus(), MiddlewareOpsLogHelper.sanitizedThrowable(throwable));
                    }
                });
            }
        } catch (RuntimeException e) {
            log.warn("中间件运维审计异步提交失败，capability={}，status={}", event.getCapability(), event.getStatus(),
                    MiddlewareOpsLogHelper.sanitizedThrowable(e));
        }
    }
}
