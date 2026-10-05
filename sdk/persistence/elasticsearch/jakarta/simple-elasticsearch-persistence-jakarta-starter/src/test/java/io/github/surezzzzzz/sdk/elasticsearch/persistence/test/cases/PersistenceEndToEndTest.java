package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query.PersistenceQuery;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.ByQueryTaskResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.DefaultPersistenceEngine;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.TypedPersistence;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceRestException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.executor.PersistenceExecutorRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.DocumentIdHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.ElasticsearchWriteApiHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.FieldValueNormalizerHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.test.SimpleElasticsearchPersistenceTestApplication;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.test.support.PersistenceFixture;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 相同持久化合同分别在真实 ES7、ES8 执行，测试只拥有精确创建的索引。
 */
@Slf4j
@SpringBootTest(classes = SimpleElasticsearchPersistenceTestApplication.class)
class PersistenceEndToEndTest {
    private final Map<String, String> ownedIndices = new LinkedHashMap<>();
    @Autowired
    private PersistenceEngine engine;
    @Autowired
    private PersistenceFixture fixture;
    @Autowired
    private PersistenceExecutorRegistry executors;
    @Autowired
    private ElasticsearchWriteApiHelper helper;
    @Autowired
    private io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver targets;
    @Autowired
    private io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistencePayloadCodec payloadCodec;
    @Autowired
    private io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceRestHelper rest;
    @Autowired
    private io.github.surezzzzzz.sdk.elasticsearch.persistence.configuration.SimpleElasticsearchPersistenceProperties properties;

    private String index(String source) throws Exception {
        String name = (source.equals("primary") ? "test-jp7-" : "test-jp8-") + UUID.randomUUID();
        fixture.request(source, "PUT", "/" + name,
                "{\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0},\"mappings\":{\"properties\":{\"status\":{\"type\":\"keyword\"},\"amount\":{\"type\":\"integer\"}}}}");
        ownedIndices.put(name, source);
        return name;
    }

    @AfterEach
    void cleanup() throws Exception {
        for (Map.Entry<String, String> entry : ownedIndices.entrySet())
            fixture.delete(entry.getValue(), entry.getKey());
    }

    private IndexRequest write(String index, String id, Object document) {
        return IndexRequest.builder().index(index).id(id).document(document)
                .options(IndexOptions.builder().refreshPolicy("wait_for").build()).build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> document(String source, String index, String id) throws Exception {
        return (Map<String, Object>) fixture.request(source, "GET", "/" + index + "/_doc/" + id, null).get("_source");
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void singleWriteUpdateDelete(String source) throws Exception {
        String index = index(source);
        PersistenceResult saved = engine.index(write(index, "row1", Map.of("status", "ready", "amount", 10)));
        assertTrue(saved.isSuccess());
        assertEquals(source, saved.getDatasource());
        assertEquals(index, saved.getIndex());
        engine.update(UpdateRequest.builder().index(index).id("row1").fieldMap(Map.of("status", "done")).build());
        assertEquals("done", document(source, index, "row1").get("status"));
        assertTrue(engine.delete(DeleteRequest.builder().index(index).id("row1").build()).isSuccess());
        org.elasticsearch.client.ResponseException missing = assertThrows(org.elasticsearch.client.ResponseException.class,
                () -> fixture.request(source, "GET", "/" + index + "/_doc/row1", null));
        assertEquals(404, missing.getResponse().getStatusLine().getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void automaticIdAndCreateConflict(String source) throws Exception {
        String index = index(source);
        PersistenceResult saved = engine.index(write(index, null, Map.of("status", "ready")));
        assertNotNull(saved.getId());
        IndexRequest create = write(index, "existing", Map.of("status", "ready"));
        engine.create(create);
        PersistenceRestException conflict = assertThrows(PersistenceRestException.class, () -> engine.create(create));
        assertEquals(409, conflict.getStatus());
        assertNull(create.getOptions().getOperationType(), "create 不改调用方选项");
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void updateUpsertScriptAndNoop(String source) throws Exception {
        String index = index(source);
        engine.update(UpdateRequest.builder().index(index).id("row1").fieldMap(Map.of("amount", 1))
                .options(UpdateOptions.builder().docAsUpsert(true).build()).build());
        PersistenceResult noop = engine.update(UpdateRequest.builder().index(index).id("row1")
                .fieldMap(Map.of("amount", 1)).build());
        assertEquals("noop", noop.getResult());
        engine.update(UpdateRequest.builder().index(index).id("row1").scriptSource("ctx._source.amount += params.increment")
                .scriptParamMap(Map.of("increment", 2)).build());
        assertEquals(3, document(source, index, "row1").get("amount"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void scriptedUpsertAndPreProcessorAffectStoredDocument(String source) throws Exception {
        String index = index(source);
        engine.update(UpdateRequest.builder().index(index).id("upserted").scriptSource("ctx._source.amount += params.increment")
                .scriptParamMap(Map.of("increment", 2)).options(UpdateOptions.builder().scriptedUpsert(true).upsertDoc(Map.of("amount", 1)).build()).build());
        assertEquals(3, document(source, index, "upserted").get("amount"));
        io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessor processor = new io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessor() {
            public boolean supports(Class<?> type) {
                return Map.class.isAssignableFrom(type);
            }

            public Object process(Object value, io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentProcessContext context) {
                Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) value);
                copy.put("status", "processed");
                return copy;
            }
        };
        ElasticsearchWriteApiHelper processedHelper = new ElasticsearchWriteApiHelper(
                targets, payloadCodec, rest, new io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessorChain(Collections.singletonList(processor)),
                new io.github.surezzzzzz.sdk.elasticsearch.persistence.classifier.DefaultBulkFailureClassifier(), properties, event -> {
        });
        Map<String, Object> original = new LinkedHashMap<>(Map.of("amount", 4));
        assertTrue(((PersistenceResult) processedHelper.prepare(write(index, "processed", original), false).get()).isSuccess());
        assertEquals("processed", document(source, index, "processed").get("status"));
        assertFalse(original.containsKey("status"));
        assertEquals(404, assertThrows(PersistenceRestException.class, () -> engine.getTask(source, "missing-node:999999")).getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void distinguishMissingDocumentAndIndex(String source) throws Exception {
        String index = index(source);
        DeleteOptions options = DeleteOptions.builder().notFoundAsSuccess(true).build();
        assertTrue(engine.delete(DeleteRequest.builder().index(index).id("missing").options(options).build()).isSuccess());
        PersistenceRestException missingIndex = assertThrows(PersistenceRestException.class,
                () -> engine.delete(DeleteRequest.builder().index(index + "-absent").id("missing").options(options).build()));
        assertEquals(404, missingIndex.getStatus());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void bulkMixedItemsAndBatches(String source) throws Exception {
        String index = index(source);
        engine.index(write(index, "old", Map.of("amount", 1)));
        BulkResult result = engine.bulk(BulkRequest.builder().itemList(List.of(
                        BulkItem.builder().type(BulkItemType.INDEX).index(index).id("new").document(Map.of("amount", 2)).build(),
                        BulkItem.builder().type(BulkItemType.UPDATE).index(index).id("old").fieldMap(Map.of("amount", 3)).build(),
                        BulkItem.builder().type(BulkItemType.DELETE).index(index).id("new").build()))
                .options(BulkOptions.builder().batchSize(2).refreshPolicy("wait_for").build()).build());
        assertTrue(result.isSuccess());
        assertEquals(3, result.getSucceeded());
        assertEquals(2, result.getBatchTotal());
        assertEquals(3, document(source, index, "old").get("amount"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void bulkStopsAtFailedBatch(String source) throws Exception {
        String index = index(source);
        engine.create(write(index, "existing", Map.of("amount", 1)));
        BulkResult result = engine.bulk(BulkRequest.builder().itemList(List.of(
                        BulkItem.builder().type(BulkItemType.CREATE).index(index).id("existing").document(Map.of("amount", 2)).build(),
                        BulkItem.builder().type(BulkItemType.CREATE).index(index).id("unsubmitted").document(Map.of("amount", 3)).build()))
                .options(BulkOptions.builder().batchSize(1).continueOnFailure(false).refreshPolicy("wait_for").build()).build());
        assertFalse(result.isSuccess());
        assertEquals(1, result.getFailed());
        assertEquals(0, result.getSucceeded());
        assertEquals(true, result.getStoppedOnFailure());
        assertEquals(409, result.getFailureList().get(0).getStatus());
        assertEquals(1, ((Number) fixture.request(source, "GET", "/" + index + "/_count", null).get("count")).intValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void createConflictCompensatesWithoutMutatingRequest(String source) throws Exception {
        String index = index(source);
        engine.create(write(index, "row1", Map.of("amount", 1)));
        UpdateRequest update = UpdateRequest.builder().fieldMap(Map.of("amount", 4)).build();
        PersistenceResult result = engine.createThenUpdateOnConflict(write(index, "row1", Map.of("amount", 2)), update);
        assertTrue(result.isSuccess());
        assertEquals(4, document(source, index, "row1").get("amount"));
        assertNull(update.getIndex());
        assertNull(update.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void bulkConflictCompensationPreservesOriginalPosition(String source) throws Exception {
        String index = index(source);
        engine.create(write(index, "row1", Map.of("amount", 1)));
        BulkResult result = engine.bulkCreateThenUpdateOnConflict(BulkRequest.builder().itemList(List.of(
                                BulkItem.builder().type(BulkItemType.CREATE).index(index).id("row0").document(Map.of("amount", 0)).build(),
                                BulkItem.builder().type(BulkItemType.CREATE).index(index).id("row1").document(Map.of("amount", 2)).build()))
                        .options(BulkOptions.builder().refreshPolicy("wait_for").build()).build(),
                (item, failure) -> {
                    assertEquals(1, failure.getItemIndex());
                    return BulkItem.builder().type(BulkItemType.UPDATE).fieldMap(Map.of("amount", 5)).build();
                });
        assertTrue(result.isSuccess());
        assertEquals(2, result.getSucceeded());
        assertEquals(0, result.getFailed());
        assertEquals(5, document(source, index, "row1").get("amount"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void synchronousByQueryAndServerTask(String source) throws Exception {
        String index = index(source);
        engine.index(write(index, "row1", Map.of("status", "ready", "amount", 1)));
        PersistenceQuery query = PersistenceQuery.builder().termMap(Map.of("status", "ready")).build();
        ByQueryTaskResult updated = engine.updateByQuery(UpdateByQueryRequest.builder().index(index).query(query)
                .scriptSource("ctx._source.amount = params.value").scriptParamMap(Map.of("value", 9))
                .options(ByQueryOptions.builder().refreshPolicy("true").build()).build());
        assertTrue(updated.isCompleted());
        assertEquals(1, updated.getUpdated());
        assertEquals(9, document(source, index, "row1").get("amount"));
        ByQueryTaskResult task = engine.deleteByQuery(DeleteByQueryRequest.builder().index(index).query(query)
                .options(ByQueryOptions.builder().waitForCompletion(false).refreshPolicy("true").build()).build());
        assertNotNull(task.getTaskId());
        assertEquals(source, task.getDatasource());
        long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
        ByQueryTaskResult completed;
        do {
            completed = engine.getTask(source, task.getTaskId());
            if (completed.isCompleted()) break;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        assertTrue(completed.isCompleted());
        assertEquals(1, completed.getDeleted());
        assertTrue(completed.getFailureList().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void asynchronousInputIsFrozenBeforeQueueing(String source) throws Exception {
        String index = index(source);
        List<Runnable> queue = new ArrayList<>();
        PersistenceEngine queued = new DefaultPersistenceEngine(executors, queue::add, helper);
        Map<String, Object> body = new LinkedHashMap<>(Map.of("amount", 1));
        IndexRequest request = write(index, "row1", body);
        CompletableFuture<PersistenceResult> future = queued.indexAsync(request);
        body.put("amount", 99);
        request.setId("changed");
        queue.remove(0).run();
        assertTrue(future.get(10, TimeUnit.SECONDS).isSuccess());
        assertEquals(1, document(source, index, "row1").get("amount"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void queueRejectionAndAsyncExecution(String source) throws Exception {
        String index = index(source);
        PersistenceEngine rejected = new DefaultPersistenceEngine(executors,
                task -> {
                    throw new RejectedExecutionException("测试队列拒绝");
                }, helper);
        CompletableFuture<PersistenceResult> failure = rejected.indexAsync(write(index, "rejected", Map.of("amount", 1)));
        assertThrows(CompletionException.class, failure::join);
        assertTrue(engine.indexAsync(write(index, "accepted", Map.of("amount", 2))).get(10, TimeUnit.SECONDS).isSuccess());
        assertEquals(2, document(source, index, "accepted").get("amount"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void rejectsDangerousAndMixedSourceOperations(String source) throws Exception {
        String index = index(source);
        for (String json : List.of("{}", "{\"match_all\":{}}", "{\"bool\":{}}", "{\"bool\":{\"must_not\":[{\"term\":{\"amount\":1}}]}}")) {
            assertThrows(RuntimeException.class, () -> engine.deleteByQuery(DeleteByQueryRequest.builder().index(index)
                    .query(PersistenceQuery.builder().rawJson(json).build()).build()));
        }
        assertThrows(RuntimeException.class, () -> engine.bulk(BulkRequest.builder().itemList(List.of(
                BulkItem.builder().type(BulkItemType.INDEX).index("test-jp7-mixed").id("1").document(Map.of("amount", 1)).build(),
                BulkItem.builder().type(BulkItemType.INDEX).index("test-jp8-mixed").id("2").document(Map.of("amount", 2)).build())).build()));
        assertEquals(0, ((Number) fixture.request(source, "GET", "/" + index + "/_count", null).get("count")).intValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void typedDefaultsValidationAndExplicitOptions(String source) throws Exception {
        String index = index(source);
        TypedPersistence<SampleDocument> typed = engine.forEntity(SampleDocument.class)
                .withIndexResolver(row -> index).withIdResolver(SampleDocument::getId)
                .withValidator(row -> {
                    if (row.getAmount() < 0) throw new IllegalArgumentException("测试实体非法");
                })
                .withDefaultIndexOptions(IndexOptions.builder().refreshPolicy("wait_for").build());
        assertTrue(typed.create(new SampleDocument("typed1", 3)).isSuccess());
        assertEquals(3, document(source, index, "typed1").get("amount"));
        assertThrows(RuntimeException.class, () -> typed.create(new SampleDocument("invalid", -1)));
        assertThrows(RuntimeException.class, () -> typed.index(new SampleDocument("typed2", 2), null));
        assertTrue(typed.indexAsync(new SampleDocument("typed3", 5), IndexOptions.builder().refreshPolicy("wait_for").build())
                .get(10, TimeUnit.SECONDS).isSuccess());
        assertTrue(typed.bulkCreate(List.of(new SampleDocument("typed4", 6), new SampleDocument("typed5", 7)),
                BulkOptions.builder().refreshPolicy("wait_for").build()).isSuccess());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    void stableIdResolverReusesDocumentAfterNormalization(String source) throws Exception {
        String index = index(source);
        TypedPersistence<SampleDocument> typed = engine.forEntity(SampleDocument.class)
                .withIndexResolver(row -> index)
                .withIdResolver(row -> DocumentIdHelper.sha256(FieldValueNormalizerHelper.trimLowerCase(row.getId()), row.getAmount()))
                .withDefaultIndexOptions(IndexOptions.builder().refreshPolicy("wait_for").build());
        PersistenceResult first = typed.index(new SampleDocument(" Sample ", 7));
        PersistenceResult second = typed.index(new SampleDocument("sample", 7));
        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertEquals(first.getId(), second.getId(), "相同规范字段应定位同一文档");
        assertEquals(source, second.getDatasource());
        assertEquals("sample", document(source, index, first.getId()).get("id"));
        assertEquals(7, document(source, index, first.getId()).get("amount"));
        assertEquals(1, ((Number) fixture.request(source, "GET", "/" + index + "/_count", null).get("count")).intValue());
    }

    @ParameterizedTest
    @ValueSource(strings = {"primary", "secondary"})
    @SuppressWarnings("unchecked")
    void normalizerInPreProcessorChangesStoredValueOnly(String source) throws Exception {
        String index = index(source);
        io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessor processor = new io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessor() {
            public boolean supports(Class<?> type) {
                return Map.class.isAssignableFrom(type);
            }

            public Object process(Object value, io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentProcessContext context) {
                Map<String, Object> copy = new LinkedHashMap<>((Map<String, Object>) value);
                copy.put("status", FieldValueNormalizerHelper.trimLowerCase(
                        FieldValueNormalizerHelper.fullWidthToHalfWidth((String) copy.get("status"))));
                return copy;
            }
        };
        ElasticsearchWriteApiHelper normalizedHelper = new ElasticsearchWriteApiHelper(
                targets, payloadCodec, rest, new io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessorChain(Collections.singletonList(processor)),
                new io.github.surezzzzzz.sdk.elasticsearch.persistence.classifier.DefaultBulkFailureClassifier(), properties, event -> {
        });
        Map<String, Object> original = new LinkedHashMap<>(Map.of("status", "\u3000\uff32\uff25\uff21\uff24\uff39\u3000", "amount", 4));
        PersistenceResult result = (PersistenceResult) normalizedHelper.prepare(write(index, "normalized", original), false).get();
        assertTrue(result.isSuccess());
        assertEquals("ready", document(source, index, "normalized").get("status"));
        assertEquals(4, document(source, index, "normalized").get("amount"));
        assertEquals("\u3000\uff32\uff25\uff21\uff24\uff39\u3000", original.get("status"), "处理链不能修改调用方输入");
    }

    /**
     * 实体定位由 Typed 门面的显式解析器覆盖，避免绑定固定测试索引。
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    @org.springframework.data.elasticsearch.annotations.Document(indexName = "sample-record")
    static class SampleDocument {
        @org.springframework.data.annotation.Id
        private String id;
        private int amount;
    }
}
