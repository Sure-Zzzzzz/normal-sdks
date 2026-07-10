package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.test.cases;

import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.model.EsPersistenceAuditFailureRecord;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.model.EsPersistenceAuditRecord;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.test.EsPersistenceAuditListenerTestApplication;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.test.TestEsPersistenceAuditHandler;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.test.provider.TestPersistenceAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.test.provider.TestPersistenceAuditUserProvider;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.event.EsPersistenceErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.event.EsPersistenceEvent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateByQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkItemFailure;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.ByQueryTaskResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchLowLevelRequestHelper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * ES Persistence 审计事件监听器测试
 *
 * @author surezzzzzz
 */
@Slf4j
@SpringBootTest(classes = EsPersistenceAuditListenerTestApplication.class)
public class EsPersistenceAuditEventListenerUnitTest {

    private static final String DATASOURCE = "secondary";
    private static final String E2E_INDEX = "test_persistence_audit_e2e";

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TestEsPersistenceAuditHandler testAuditHandler;

    @Autowired
    private TestPersistenceAuditUserProvider testUserProvider;

    @Autowired
    private TestPersistenceAuditTraceIdProvider testTraceIdProvider;

    @Autowired
    private PersistenceEngine persistenceEngine;

    @Autowired
    private SimpleElasticsearchRouteRegistry routeRegistry;

    @Data
    @Document(indexName = "test_persistence_audit_e2e")
    static class AuditE2eDoc {
        @Id
        private String id;
        private String name;
        private long ts;
    }

    @BeforeEach
    public void setUp() {
        log.info("========== 开始准备 ES Persistence 审计测试 ==========");
        testAuditHandler.reset();
        testUserProvider.reset();
        testTraceIdProvider.reset();
        log.info("========== ES Persistence 审计测试准备完成 ==========");
    }

    @AfterEach
    public void tearDown() {
        try {
            ElasticsearchLowLevelRequestHelper.deleteIndex(secondaryClient(), E2E_INDEX);
        } catch (Exception e) {
            log.warn("清理索引 {} 失败: {}", E2E_INDEX, e.getMessage());
        }
    }

    @Test
    public void testPersistenceIndexE2eAuditHandling() throws Exception {
        log.info("========== 测试：真实 persistence index 写入触发审计 ==========");

        testUserProvider.setClientId("client-001");
        testUserProvider.setClientType("internal");
        testUserProvider.setUserId("user-001");
        testUserProvider.setUsername("tester");
        testTraceIdProvider.setTraceId("trace-index-e2e-001");

        AuditE2eDoc doc = new AuditE2eDoc();
        doc.setId("audit-e2e-index-1");
        doc.setName("name-1");
        doc.setTs(100L);

        PersistenceResult result = persistenceEngine.index(doc);
        log.info("persistence index 结果={}", result);
        assertTrue(result.isSuccess(), "index 应成功");
        assertEquals("audit-e2e-index-1", result.getId(), "result.id 应与文档 id 一致");
        assertEquals(E2E_INDEX, result.getIndex(), "result.index 应为实体声明索引");
        assertEquals(DATASOURCE, result.getDatasource(), "result.datasource 应由 route 命中 secondary");

        ElasticsearchLowLevelRequestHelper.refreshIndex(secondaryClient(), E2E_INDEX);
        Map<String, Object> saved = ElasticsearchLowLevelRequestHelper.getDoc(secondaryClient(), E2E_INDEX, "audit-e2e-index-1");
        assertNotNull(saved, "文档应真实写入 ES");
        assertEquals("audit-e2e-index-1", saved.get("id"), "id 字段应一致");
        assertEquals("name-1", saved.get("name"), "name 字段应一致");
        assertEquals(100L, ((Number) saved.get("ts")).longValue(), "ts 字段应一致");

        assertTrue(testAuditHandler.latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, testAuditHandler.records.size());
        EsPersistenceAuditRecord record = testAuditHandler.records.get(0);
        assertEquals("client-001", record.getClientId());
        assertEquals("internal", record.getClientType());
        assertEquals("user-001", record.getUserId());
        assertEquals("tester", record.getUsername());
        assertEquals("trace-index-e2e-001", record.getTraceId());
        assertEquals("PERSISTENCE", record.getSourceType());
        assertEquals("index", record.getOperationType());
        assertEquals("IndexRequest", record.getRequestType());
        assertEquals("success", record.getResult());
        assertEquals(E2E_INDEX, record.getIndex());
        assertEquals(DATASOURCE, record.getDatasource());
        assertEquals("audit-e2e-index-1", record.getDocumentId());
        assertTrue(record.getSuccess());
        assertFalse(record.getConflict());
        assertFalse(record.getPartial());
        assertFalse(record.getClientAsync());
        assertFalse(record.getRouteAsyncWrite());
        assertFalse(record.getServerAsyncTask());
        assertNull(record.getTaskId());
        assertNotNull(record.getStartTimeMs());
        assertNotNull(record.getTookMs());
        assertNull(record.getErrorCode());
        assertNull(record.getErrorClass());
        assertNull(record.getErrorMessage());
        assertNull(record.getFailureList());
        assertNotNull(record.getTimestamp());
    }

    @Test
    public void testBulkPartialFailureEventHandling() throws InterruptedException {
        log.info("========== 测试：Bulk 部分失败事件处理 ==========");

        BulkRequest request = BulkRequest.builder()
                .defaultIndex("test_wildcard")
                .build();
        BulkResult result = BulkResult.builder()
                .success(false)
                .hasFailure(true)
                .total(3)
                .succeeded(2)
                .failed(1)
                .datasource("primary")
                .tookMs(42L)
                .batchTotal(1)
                .batchSucceeded(0)
                .batchFailed(1)
                .partial(true)
                .failureList(Arrays.asList(
                        BulkItemFailure.builder()
                                .itemIndex(1)
                                .type(BulkItemType.CREATE)
                                .index("test_wildcard-2026.07.09")
                                .id("doc-dup")
                                .status(409)
                                .errorCode("ES_PERSISTENCE_BULK_ITEM_FAILED")
                                .errorType("version_conflict_engine_exception")
                                .errorReason("document already exists")
                                .retryable(false)
                                .build(),
                        BulkItemFailure.builder()
                                .itemIndex(2)
                                .type(BulkItemType.INDEX)
                                .index("test_wildcard-2026.07.09")
                                .id("doc-limited")
                                .status(429)
                                .errorCode("ES_PERSISTENCE_BULK_ITEM_FAILED")
                                .errorReason("too many requests")
                                .retryable(true)
                                .build()))
                .build();

        eventPublisher.publishEvent(new EsPersistenceEvent(this, request, result, buildContext(PersistenceOperationType.BULK)));

        assertTrue(testAuditHandler.latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, testAuditHandler.records.size());
        EsPersistenceAuditRecord record = testAuditHandler.records.get(0);
        assertEquals("bulk", record.getOperationType());
        assertEquals("partial_failure", record.getResult());
        assertFalse(record.getSuccess());
        assertTrue(record.getPartial());
        assertTrue(record.getConflict());
        assertEquals(Integer.valueOf(3), record.getBulkItemCount());
        assertEquals(Integer.valueOf(2), record.getBulkSucceeded());
        assertEquals(Integer.valueOf(1), record.getBulkFailed());
        assertEquals(Integer.valueOf(1), record.getBatchTotal());
        assertEquals(Integer.valueOf(0), record.getBatchSucceeded());
        assertEquals(Integer.valueOf(1), record.getBatchFailed());
        assertEquals("primary", record.getDatasource());
        assertEquals(Long.valueOf(42L), record.getTookMs());
        assertNull(record.getDocumentId());
        assertEquals(1, record.getFailureList().size());

        EsPersistenceAuditFailureRecord failure = record.getFailureList().get(0);
        assertEquals(Integer.valueOf(1), failure.getItemIndex());
        assertEquals("CREATE", failure.getType());
        assertEquals("test_wildcard-2026.07.09", failure.getIndex());
        assertEquals("doc-dup", failure.getId());
        assertEquals(Integer.valueOf(409), failure.getStatus());
        assertNull(failure.getStatusText());
        assertEquals("ES_PERSISTENCE_BULK_ITEM_FAILED", failure.getErrorCode());
        assertEquals("version_conflict_engine_exception", failure.getErrorType());
        assertEquals("document already exists", failure.getErrorReason());
        assertFalse(failure.getRetryable());
        assertTrue(failure.getConflict());
    }

    @Test
    public void testByQueryTaskSubmittedEventHandling() throws InterruptedException {
        log.info("========== 测试：ByQuery 服务端异步提交事件处理 ==========");

        UpdateByQueryRequest request = UpdateByQueryRequest.builder()
                .index("test_wildcard")
                .build();
        ByQueryTaskResult result = ByQueryTaskResult.builder()
                .completed(false)
                .taskId("node-001:12345")
                .datasource("primary")
                .index("test_wildcard")
                .total(10L)
                .updated(0L)
                .deleted(0L)
                .versionConflicts(0L)
                .tookMs(7L)
                .failureList(Collections.emptyList())
                .build();

        eventPublisher.publishEvent(new EsPersistenceEvent(this, request, result, buildContext(PersistenceOperationType.UPDATE_BY_QUERY)));

        assertTrue(testAuditHandler.latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, testAuditHandler.records.size());
        EsPersistenceAuditRecord record = testAuditHandler.records.get(0);
        assertEquals("update_by_query", record.getOperationType());
        assertEquals("task_submitted", record.getResult());
        assertTrue(record.getSuccess());
        assertFalse(record.getPartial());
        assertFalse(record.getConflict());
        assertTrue(record.getServerAsyncTask());
        assertEquals("node-001:12345", record.getTaskId());
        assertEquals("test_wildcard", record.getIndex());
        assertEquals("primary", record.getDatasource());
        assertEquals(Long.valueOf(10L), record.getTotal());
        assertEquals(Long.valueOf(0L), record.getUpdated());
        assertEquals(Long.valueOf(0L), record.getDeleted());
        assertEquals(Long.valueOf(0L), record.getVersionConflicts());
        assertEquals(Long.valueOf(7L), record.getTookMs());
        assertTrue(record.getFailureList().isEmpty());
    }

    @Test
    public void testPersistenceErrorEventHandling() throws InterruptedException {
        log.info("========== 测试：写入失败事件处理 ==========");

        IndexRequest request = IndexRequest.builder()
                .index("test_wildcard")
                .id("doc-error")
                .document(new HashMap<String, Object>())
                .build();
        SimpleElasticsearchPersistenceException error = new SimpleElasticsearchPersistenceException(
                "ES_PERSISTENCE_WRITE_FAILED", "write failed");

        eventPublisher.publishEvent(new EsPersistenceErrorEvent(this, request, error, buildContext(PersistenceOperationType.INDEX)));

        assertTrue(testAuditHandler.latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, testAuditHandler.records.size());
        EsPersistenceAuditRecord record = testAuditHandler.records.get(0);
        assertEquals("failure", record.getResult());
        assertFalse(record.getSuccess());
        assertFalse(record.getPartial());
        assertFalse(record.getConflict());
        assertEquals("PERSISTENCE", record.getSourceType());
        assertEquals("index", record.getOperationType());
        assertEquals("test_wildcard", record.getIndex());
        assertEquals("primary", record.getDatasource());
        assertEquals("doc-error", record.getDocumentId());
        assertNull(record.getFailureList());
        assertEquals("ES_PERSISTENCE_WRITE_FAILED", record.getErrorCode());
        assertEquals(SimpleElasticsearchPersistenceException.class.getName(), record.getErrorClass());
        assertEquals("write failed", record.getErrorMessage());
    }

    @Test
    public void testHandlerExceptionIsolation() throws InterruptedException {
        log.info("========== 测试：Handler 异常隔离 ==========");

        IndexRequest request = IndexRequest.builder()
                .index("test_wildcard")
                .id("doc-isolation")
                .document(new HashMap<String, Object>())
                .build();
        PersistenceResult result = PersistenceResult.builder()
                .success(true)
                .id("doc-isolation")
                .index("test_wildcard")
                .datasource("primary")
                .operationType(PersistenceOperationType.INDEX)
                .tookMs(1L)
                .build();

        eventPublisher.publishEvent(new EsPersistenceEvent(this, request, result, buildContext(PersistenceOperationType.INDEX)));

        assertTrue(testAuditHandler.latch.await(5, TimeUnit.SECONDS));
        assertEquals(1, testAuditHandler.records.size());
        EsPersistenceAuditRecord record = testAuditHandler.records.get(0);
        assertEquals("success", record.getResult());
        assertTrue(record.getSuccess());
        assertEquals("doc-isolation", record.getDocumentId());
        assertEquals("test_wildcard", record.getIndex());
        assertEquals("primary", record.getDatasource());
    }

    private PersistenceExecutionContext buildContext(PersistenceOperationType operationType) {
        return PersistenceExecutionContext.builder()
                .operationType(operationType)
                .index("test_wildcard")
                .datasource("primary")
                .clientAsync(false)
                .routeAsyncWrite(false)
                .serverAsyncTask(operationType == PersistenceOperationType.UPDATE_BY_QUERY)
                .taskId(null)
                .startTimeMs(1000L)
                .tookMs(20L)
                .build();
    }

    private RestClient secondaryClient() {
        return routeRegistry.getLowLevelClient(DATASOURCE);
    }
}
