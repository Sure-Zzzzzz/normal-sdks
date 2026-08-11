package io.github.surezzzzzz.sdk.ops.middleware.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditContext;
import io.github.surezzzzzz.sdk.ops.middleware.audit.MiddlewareOpsAuditEvent;
import io.github.surezzzzzz.sdk.ops.middleware.audit.PersistenceEngineMiddlewareOpsAuditPublisher;
import io.github.surezzzzzz.sdk.ops.middleware.constant.SmartMiddlewareOpsServerConstant;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareOpsCapability;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareType;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Persistence 审计发布器的异步与安全投影测试。
 *
 * @author surezzzzzz
 */
class PersistenceEngineMiddlewareOpsAuditPublisherTest {

    @Test
    void shouldSubmitLogicalDailyWriteIndexAndRequestIdDocument() {
        PersistenceEngine engine = mock(PersistenceEngine.class);
        when(engine.indexAsync(any(IndexRequest.class))).thenReturn(CompletableFuture.<PersistenceResult>completedFuture(null));
        PersistenceEngineMiddlewareOpsAuditPublisher publisher = new PersistenceEngineMiddlewareOpsAuditPublisher(engine);

        publisher.publish(event("request-1"));

        ArgumentCaptor<IndexRequest> captor = ArgumentCaptor.forClass(IndexRequest.class);
        verify(engine).indexAsync(captor.capture());
        IndexRequest request = captor.getValue();
        assertEquals(SmartMiddlewareOpsServerConstant.AUDIT_WRITE_INDEX, request.getIndex());
        assertEquals("request-1", request.getId());
        assertTrue(request.getOptions().getRefresh());
        @SuppressWarnings("unchecked")
        Map<String, Object> document = (Map<String, Object>) request.getDocument();
        assertEquals("request-1", document.get("id"));
        assertEquals("ops-user", document.get("subject"));
        assertEquals("REDIS_SUMMARY", document.get("capability"));
        assertEquals("redis", document.get("middlewareType"));
        assertEquals("cache-primary", document.get("datasourceKey"));
        assertEquals("sha256", document.get("resourceDigest"));
        assertEquals(200, document.get("httpStatus"));
        assertEquals(15L, document.get("durationMillis"));
        assertEquals("orders-v1", document.get("elasticsearchIndex"));
        assertEquals("{\"query\":{\"match_all\":{}}}", document.get("elasticsearchDsl"));
        assertEquals("SELECT id FROM orders", document.get("mysqlSql"));
        assertEquals("order:7", document.get("redisKey"));
        assertEquals("status", document.get("redisField"));
        assertEquals("order-events", document.get("kafkaTopic"));
        assertEquals("order-group", document.get("kafkaGroupId"));
        assertEquals(2, document.get("page"));
        assertEquals(50, document.get("size"));
        assertEquals(10L, document.get("offset"));
    }

    @Test
    void shouldIgnoreMissingRequestIdAndAsyncWriteFailures() {
        PersistenceEngine engine = mock(PersistenceEngine.class);
        PersistenceEngineMiddlewareOpsAuditPublisher publisher = new PersistenceEngineMiddlewareOpsAuditPublisher(engine);

        publisher.publish(event(null));
        verifyNoInteractions(engine);

        when(engine.indexAsync(any(IndexRequest.class))).thenReturn(failedFuture());
        assertDoesNotThrow(() -> publisher.publish(event("request-2")));
        verify(engine).indexAsync(any(IndexRequest.class));

        PersistenceEngine rejectedEngine = mock(PersistenceEngine.class);
        when(rejectedEngine.indexAsync(any(IndexRequest.class))).thenThrow(new IllegalStateException("rejected"));
        assertDoesNotThrow(() -> new PersistenceEngineMiddlewareOpsAuditPublisher(rejectedEngine).publish(event("request-3")));
    }

    private CompletableFuture<PersistenceResult> failedFuture() {
        CompletableFuture<PersistenceResult> future = new CompletableFuture<>();
        future.completeExceptionally(new IllegalStateException("unavailable"));
        return future;
    }

    private MiddlewareOpsAuditEvent event(String requestId) {
        return MiddlewareOpsAuditEvent.builder().occurredAt(Instant.parse("2026-08-04T08:00:00Z"))
                .subject("ops-user").capability(MiddlewareOpsCapability.REDIS_SUMMARY)
                .middlewareType(MiddlewareType.REDIS).datasourceKey("cache-primary").clusterTag("缓存集群")
                .resourceDigest("sha256").context(MiddlewareOpsAuditContext.builder().elasticsearchIndex("orders-v1")
                        .elasticsearchDsl("{\"query\":{\"match_all\":{}}}").mysqlSql("SELECT id FROM orders")
                        .redisKey("order:7").redisField("status").kafkaTopic("order-events")
                        .kafkaGroupId("order-group").page(2).size(50).offset(10L).build())
                .status(200).durationMillis(15L).requestId(requestId).build();
    }
}
