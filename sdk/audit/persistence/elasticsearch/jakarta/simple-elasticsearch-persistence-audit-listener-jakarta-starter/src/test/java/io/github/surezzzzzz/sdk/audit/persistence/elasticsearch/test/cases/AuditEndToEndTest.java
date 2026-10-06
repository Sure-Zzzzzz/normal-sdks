package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.test.cases;

import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.model.EsPersistenceAuditRecord;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.test.AuditTestApplication;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.ByQueryOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query.PersistenceQuery;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.ByQueryTaskResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 真实 ES7/ES8：正式 Persistence -> Core 事件 -> 异步审计处理器。
 */
@SpringBootTest(classes = AuditTestApplication.class)
@Slf4j
class AuditEndToEndTest {
    private final Map<String, String> owned = new LinkedHashMap<>();
    @Autowired
    private PersistenceEngine engine;
    @Autowired
    private SimpleElasticsearchRouteRegistry routes;
    @Autowired
    private AuditTestApplication.Collector collector;
    @Autowired
    private AuditTestApplication.Users users;

    @BeforeEach
    void prepare() {
        collector.getRecords().clear();
        users.current.set("sample-audit-user");
    }

    @AfterEach
    void cleanup() throws Exception {
        users.current.remove();
        for (Map.Entry<String, String> entry : owned.entrySet()) {
            assertTrue(entry.getKey().matches("test-jpa[78]-[a-f0-9-]{36}"));
            routes.getLowLevelClient(entry.getValue()).performRequest(new Request("DELETE", "/" + entry.getKey()));
        }
        owned.clear();
    }

    private String index(String source) throws Exception {
        String value = "test-jpa" + (source.equals("primary") ? "7-" : "8-") + UUID.randomUUID();
        Request create = new Request("PUT", "/" + value);
        create.setJsonEntity("{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},\"mappings\":{\"properties\":{\"amount\":{\"type\":\"integer\"}}}}");
        routes.getLowLevelClient(source).performRequest(create);
        owned.put(value, source);
        return value;
    }

    private EsPersistenceAuditRecord take(String source, String operation, boolean clientAsync) throws Exception {
        EsPersistenceAuditRecord record = collector.getRecords().poll(5, TimeUnit.SECONDS);
        assertNotNull(record, "真实主链必须发布审计记录");
        assertEquals(source, record.getDatasource());
        assertEquals(operation, record.getOperationType());
        assertEquals(Boolean.valueOf(clientAsync), record.getClientAsync());
        assertEquals(clientAsync ? null : "sample-audit-user", record.getUserId());
        assertEquals(clientAsync ? null : "sample-audit-user", record.getTraceId());
        assertNull(record.getIndex());
        assertNull(record.getDocumentId());
        assertNull(record.getTaskId());
        assertNull(record.getRequestType());
        assertNotNull(record.getTookMs());
        assertTrue(collector.getThreadName().startsWith("es-persistence-audit-"));
        return record;
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void crudAndFailureAreAudited(String source) throws Exception {
        String index = index(source);
        assertTrue(engine.create(IndexRequest.builder().index(index).id("sample").document(Collections.singletonMap("amount", 1)).build()).isSuccess());
        assertEquals("success", take(source, "create", false).getResult());
        assertThrows(RuntimeException.class, () -> engine.create(IndexRequest.builder().index(index).id("sample").document(Collections.singletonMap("amount", 2)).build()));
        assertEquals("failure", take(source, "create", false).getResult());
        assertTrue(engine.index(IndexRequest.builder().index(index).id("sample").document(Collections.singletonMap("amount", 2)).build()).isSuccess());
        assertEquals("success", take(source, "index", false).getResult());
        assertTrue(engine.update(UpdateRequest.builder().index(index).id("sample").fieldMap(Collections.singletonMap("amount", 3)).build()).isSuccess());
        assertEquals("success", take(source, "update", false).getResult());
        assertTrue(engine.delete(DeleteRequest.builder().index(index).id("sample").build()).isSuccess());
        assertEquals("success", take(source, "delete", false).getResult());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void bulkPartialFailureKeepsCountsWithoutOriginalFailurePayload(String source) throws Exception {
        String index = index(source);
        BulkResult result = engine.bulk(BulkRequest.builder().defaultIndex(index).itemList(Arrays.asList(
                BulkItem.builder().type(BulkItemType.CREATE).id("same").document(Collections.singletonMap("amount", 1)).build(),
                BulkItem.builder().type(BulkItemType.CREATE).id("same").document(Collections.singletonMap("amount", 2)).build())).build());
        assertEquals(1, result.getFailed());
        EsPersistenceAuditRecord record = take(source, "bulk", false);
        assertEquals("partial_failure", record.getResult());
        assertEquals(Integer.valueOf(2), record.getBulkItemCount());
        assertEquals(Integer.valueOf(1), record.getBulkFailed());
        assertNull(record.getConflict());
        assertTrue(record.getFailureList().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void completedByQueryIsNotInventedAsSuccessfulFromCroppedEvent(String source) throws Exception {
        String index = index(source);
        Request seed = new Request("PUT", "/" + index + "/_doc/sample");
        seed.addParameter("refresh", "true");
        seed.setJsonEntity("{\"amount\":1}");
        routes.getLowLevelClient(source).performRequest(seed);
        ByQueryTaskResult updated = engine.updateByQuery(UpdateByQueryRequest.builder().index(index)
                .query(PersistenceQuery.builder().termMap(Collections.singletonMap("amount", 1)).build()).scriptSource("ctx._source.amount += 1")
                .options(ByQueryOptions.builder().waitForCompletion(true).refresh(true).build()).build());
        assertTrue(updated.isCompleted());
        assertEquals(1, updated.getUpdated());
        EsPersistenceAuditRecord record = take(source, "update_by_query", false);
        assertEquals("task_completed", record.getResult());
        assertEquals(Long.valueOf(1), record.getUpdated());
        assertNull(record.getSuccess());
        assertTrue(engine.deleteByQuery(DeleteByQueryRequest.builder().index(index)
                .query(PersistenceQuery.builder().termMap(Collections.singletonMap("amount", 2)).build())
                .options(ByQueryOptions.builder().waitForCompletion(true).build()).build()).isCompleted());
        assertEquals(Long.valueOf(1), take(source, "delete_by_query", false).getDeleted());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void upstreamAsyncWriteDoesNotInheritCallerThreadIdentity(String source) throws Exception {
        String index = index(source);
        assertTrue(engine.indexAsync(IndexRequest.builder().index(index).id("sample").document(Collections.singletonMap("amount", 1)).build()).get(5, TimeUnit.SECONDS).isSuccess());
        assertEquals("success", take(source, "index", true).getResult());
    }
}
