package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.IndexOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.event.EsPersistenceErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.event.EsPersistenceEvent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query.PersistenceQuery;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkItemFailure;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.ByQueryTaskResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.BulkConflictExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.BulkPersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceRestException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.test.support.PersistenceProtocolFixture;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 迁移 javax 线的参数、批次与补偿场景，按 Jakarta 的脱敏和部分提交契约断言。
 */
@Slf4j
class PersistenceMigrationTest {
    private PersistenceProtocolFixture fixture;

    @BeforeEach
    void setup() {
        fixture = new PersistenceProtocolFixture();
    }

    private IndexRequest index() {
        return IndexRequest.builder().index("sample-record").id("row1").document(Map.of("amount", 1)).build();
    }

    private BulkRequest bulk(BulkItemType type, int count, boolean keepGoing) {
        List<BulkItem> items = new ArrayList<>();
        for (int i = 0; i < count; i++)
            items.add(BulkItem.builder().type(type).index("sample-record").id("row" + i).document(Map.of("amount", i)).build());
        return BulkRequest.builder().itemList(items).options(BulkOptions.builder().batchSize(1).continueOnFailure(keepGoing).build()).build();
    }

    private PersistenceQuery query() {
        return PersistenceQuery.builder().termMap(Map.of("status", "ready")).build();
    }

    private void noCallRejected(PersistenceRequest request) {
        PersistenceExecutionException error = assertThrows(PersistenceExecutionException.class, () -> fixture.execute(request));
        assertEquals(ErrorCode.REQUEST_VALIDATION_FAILED, error.getErrorCode());
        assertTrue(fixture.calls.isEmpty());
    }

    @Test
    void indexOptionsAndEncodedId() {
        IndexRequest request = index();
        request.setId("row /中文");
        request.setOptions(IndexOptions.builder().operationType(IndexOperationType.CREATE).routing("route").pipeline("sample-pipeline")
                .refreshPolicy("wait_for").timeoutMs(1250L).build());
        fixture.responses.add(PersistenceProtocolFixture.single("created"));
        fixture.execute(request);
        PersistenceProtocolFixture.Call call = fixture.calls.get(0);
        assertEquals("PUT", call.getMethod());
        assertEquals("/sample-record/_doc/row%20%2F%E4%B8%AD%E6%96%87", call.getPath());
        assertEquals(Map.of("op_type", "create", "routing", "route", "pipeline", "sample-pipeline", "refresh", "wait_for", "timeout", "1250ms"), call.getParameters());
        assertEquals(Map.of("amount", 1), fixture.codec.decode(call.getBody()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void refreshBooleanMapping(boolean refresh) {
        IndexRequest request = index();
        request.setOptions(IndexOptions.builder().refresh(refresh).build());
        fixture.responses.add(PersistenceProtocolFixture.single("created"));
        fixture.execute(request);
        assertEquals(Boolean.toString(refresh), fixture.calls.get(0).getParameters().get("refresh"));
    }

    @Test
    void conflictingRefreshAndMissingCreateIdRejectBeforeHttp() {
        IndexRequest request = index();
        request.setOptions(IndexOptions.builder().refresh(true).refreshPolicy("wait_for").build());
        noCallRejected(request);
        request.setId(null);
        request.setOptions(IndexOptions.builder().operationType(IndexOperationType.CREATE).build());
        noCallRejected(request);
    }

    @Test
    void updateScriptUpsertAndProtocolOptions() {
        fixture.responses.add(PersistenceProtocolFixture.single("created"));
        fixture.execute(UpdateRequest.builder().index("sample-record").id("row1").scriptSource("ctx._source.amount = params.value")
                .scriptParamMap(Map.of("value", 2)).options(UpdateOptions.builder().scriptedUpsert(true).upsertDoc(Map.of("amount", 0))
                        .detectNoop(false).fetchSource(true).retryOnConflict(3).routing("route").build()).build());
        PersistenceProtocolFixture.Call call = fixture.calls.get(0);
        Map<String, Object> body = fixture.codec.decode(call.getBody());
        assertEquals("/sample-record/_update/row1", call.getPath());
        assertEquals(Map.of("retry_on_conflict", "3", "_source", "true", "routing", "route"), call.getParameters());
        assertEquals(true, body.get("scripted_upsert"));
        assertEquals(false, body.get("detect_noop"));
        assertEquals(Map.of("amount", 0), body.get("upsert"));
        assertEquals(Map.of("lang", "painless", "source", "ctx._source.amount = params.value", "params", Map.of("value", 2)), body.get("script"));
    }

    @Test
    void invalidUpdateCombinationsRejectBeforeHttp() {
        noCallRejected(UpdateRequest.builder().index("sample-record").id("row1").fieldMap(Map.of("amount", 1)).scriptSource("return").build());
        noCallRejected(UpdateRequest.builder().index("sample-record").id("row1").scriptSource("return")
                .options(UpdateOptions.builder().scriptedUpsert(true).build()).build());
        noCallRejected(UpdateRequest.builder().index("sample-record").id("row1").fieldMap(Map.of("amount", 1))
                .options(UpdateOptions.builder().docAsUpsert(true).upsertDoc(Map.of("amount", 0)).build()).build());
    }

    @Test
    void deleteVersionAndRoutingOptions() {
        fixture.responses.add(PersistenceProtocolFixture.single("deleted"));
        fixture.execute(DeleteRequest.builder().index("sample-record").id("row1")
                .options(DeleteOptions.builder().version(7L).versionType("external_gte").routing("route").refresh(true).build()).build());
        assertEquals(Map.of("version", "7", "version_type", "external_gte", "routing", "route", "refresh", "true"), fixture.calls.get(0).getParameters());
        assertNull(fixture.calls.get(0).getBody());
        noCallRejectedAfterReset(DeleteRequest.builder().index("sample-record").id("row1").options(DeleteOptions.builder().versionType("external").build()).build());
    }

    private void noCallRejectedAfterReset(PersistenceRequest request) {
        fixture.calls.clear();
        noCallRejected(request);
    }

    @Test
    void bulkItemOptionsOverrideRequestOptionsAndBodyEndsWithNewline() {
        BulkRequest request = bulk(BulkItemType.INDEX, 1, true);
        request.getItemList().get(0).setRouting("item-route");
        request.getItemList().get(0).setPipeline("item-pipeline");
        request.setOptions(BulkOptions.builder().routing("request-route").pipeline("request-pipeline").refreshPolicy("wait_for").build());
        fixture.responses.add(PersistenceProtocolFixture.bulk("index", 201));
        fixture.execute(request);
        PersistenceProtocolFixture.Call call = fixture.calls.get(0);
        String[] lines = call.getBody().split("\n");
        assertTrue(call.getBody().endsWith("\n"));
        assertEquals(2, lines.length);
        assertEquals(Map.of("pipeline", "request-pipeline", "refresh", "wait_for", "routing", "request-route"), call.getParameters());
        Map<?, ?> metadata = (Map<?, ?>) fixture.codec.decode(lines[0]).get("index");
        assertEquals("item-route", metadata.get("routing"));
        assertEquals("item-pipeline", metadata.get("pipeline"));
        assertFalse(metadata.containsKey("refresh"));
    }

    @Test
    void laterInvalidBulkItemPreventsAllSubmission() {
        BulkRequest request = bulk(BulkItemType.INDEX, 2, true);
        request.getItemList().get(1).setDocument(null);
        noCallRejected(request);
    }

    @Test
    void continuingAfterItemFailurePreservesGlobalOffsetAndSanitizesDetail() {
        fixture.responses.add(PersistenceProtocolFixture.bulk("index", 201));
        fixture.responses.add(PersistenceProtocolFixture.bulk("index", 429));
        fixture.responses.add(PersistenceProtocolFixture.bulk("index", 201));
        BulkResult result = (BulkResult) fixture.execute(bulk(BulkItemType.INDEX, 3, true));
        assertFalse(result.isSuccess());
        assertFalse(result.getStoppedOnFailure());
        assertEquals(3, result.getBatchTotal());
        assertEquals(2, result.getSucceeded());
        assertEquals(1, result.getFailed());
        BulkItemFailure failure = result.getFailureList().get(0);
        assertEquals(1, failure.getItemIndex());
        assertEquals(429, failure.getStatus());
        assertTrue(failure.getRetryable());
        assertNull(failure.getErrorType());
        assertFalse(failure.getErrorReason().contains("private-detail"));
        assertEquals(3, fixture.calls.size());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void failedBatchCarriesOnlyConfirmedPriorResults(int completedBatches) {
        if (completedBatches == 1) fixture.responses.add(PersistenceProtocolFixture.bulk("index", 201));
        fixture.responses.add(new PersistenceRestException(503));
        BulkPersistenceExecutionException error = assertThrows(BulkPersistenceExecutionException.class,
                () -> fixture.execute(bulk(BulkItemType.INDEX, 3, true)));
        BulkResult partial = error.getPartialResult();
        assertTrue(partial.getPartial());
        assertFalse(partial.isSuccess());
        assertEquals(completedBatches, partial.getSucceeded());
        assertEquals(0, partial.getFailed());
        assertEquals(completedBatches, partial.getBatchTotal());
        assertEquals(completedBatches + 1, fixture.calls.size());
        assertInstanceOf(PersistenceRestException.class, error.getCause());
        assertNull(error.getCause().getCause());
    }

    @Test
    void malformedBulkResponseCarriesConfirmedPreviousBatch() {
        fixture.responses.add(PersistenceProtocolFixture.bulk("index", 201));
        fixture.responses.add(Map.of("items", List.of()));
        BulkPersistenceExecutionException error = assertThrows(BulkPersistenceExecutionException.class, () -> fixture.execute(bulk(BulkItemType.INDEX, 2, true)));
        assertEquals(1, error.getPartialResult().getSucceeded());
        assertEquals(1, error.getPartialResult().getBatchTotal());
        assertTrue(error.getPartialResult().getPartial());
        assertEquals(2, fixture.calls.size());
    }

    @Test
    void createSuccessDoesNotExecuteFallbackAndNonConflictNeverCompensates() {
        UpdateRequest fallback = UpdateRequest.builder().fieldMap(Map.of("amount", 2)).build();
        fixture.responses.add(PersistenceProtocolFixture.single("created"));
        assertTrue(fixture.helper.prepareConflict(index(), fallback, false).get().isSuccess());
        assertEquals(1, fixture.calls.size());
        fixture.calls.clear();
        fixture.responses.add(new PersistenceRestException(403));
        PersistenceRestException error = assertThrows(PersistenceRestException.class, () -> fixture.helper.prepareConflict(index(), fallback, false).get());
        assertEquals(403, error.getStatus());
        assertEquals(1, fixture.calls.size());
    }

    @Test
    void compensationTargetMismatchRejectsBeforeCreate() {
        assertThrows(PersistenceExecutionException.class, () -> fixture.helper.prepareConflict(index(),
                UpdateRequest.builder().index("other-record").fieldMap(Map.of("amount", 2)).build(), false));
        assertThrows(PersistenceExecutionException.class, () -> fixture.helper.prepareConflict(index(),
                UpdateRequest.builder().id("other-id").fieldMap(Map.of("amount", 2)).build(), false));
        assertTrue(fixture.calls.isEmpty());
    }

    @Test
    void failedCompensationMapsBackToOriginalItemAndRetainsBatchHistory() {
        fixture.responses.add(PersistenceProtocolFixture.bulk("create", 201));
        fixture.responses.add(PersistenceProtocolFixture.bulk("create", 409));
        fixture.responses.add(PersistenceProtocolFixture.bulk("update", 400));
        BulkResult result = fixture.helper.prepareBulkConflict(bulk(BulkItemType.CREATE, 2, true),
                (item, failure) -> BulkItem.builder().type(BulkItemType.UPDATE).fieldMap(Map.of("amount", 2)).build(), false).get();
        assertFalse(result.isSuccess());
        assertEquals(1, result.getSucceeded());
        assertEquals(1, result.getFailed());
        assertEquals(1, result.getFailureList().get(0).getItemIndex());
        assertEquals(400, result.getFailureList().get(0).getStatus());
        assertEquals(3, result.getBatchTotal());
        assertEquals(2, result.getBatchFailed());
    }

    @Test
    void compensationHttpFailureRetainsBothStagesAndOffsets() {
        fixture.responses.add(PersistenceProtocolFixture.bulk("create", 201));
        fixture.responses.add(PersistenceProtocolFixture.bulk("create", 409));
        fixture.responses.add(new PersistenceRestException(503));
        BulkConflictExecutionException error = assertThrows(BulkConflictExecutionException.class, () -> fixture.helper.prepareBulkConflict(bulk(BulkItemType.CREATE, 2, true),
                (item, failure) -> BulkItem.builder().type(BulkItemType.UPDATE).fieldMap(Map.of("amount", 2)).build(), false).get());
        assertEquals(1, error.getPartialResult().getSucceeded());
        assertEquals(1, error.getPartialResult().getFailed());
        assertTrue(error.getPartialResult().getPartial());
        assertEquals(List.of(1), error.getOriginalItemIndices());
        assertNotNull(error.getCompensationResult());
        assertEquals(0, error.getCompensationResult().getSucceeded());
        assertTrue(error.getCompensationResult().getPartial());
        assertEquals(3, fixture.calls.size());
    }

    @Test
    void resolverNullAndTargetMutationFailWithoutSecondStageWrites() {
        for (boolean returnsNull : List.of(true, false)) {
            fixture = new PersistenceProtocolFixture();
            fixture.responses.add(PersistenceProtocolFixture.bulk("create", 409));
            BulkConflictExecutionException error = assertThrows(BulkConflictExecutionException.class, () -> fixture.helper.prepareBulkConflict(bulk(BulkItemType.CREATE, 1, true),
                    (item, failure) -> returnsNull ? null : BulkItem.builder().type(BulkItemType.UPDATE).index("other-record").fieldMap(Map.of("amount", 2)).build(), false).get());
            assertEquals(1, error.getPartialResult().getFailed());
            assertNull(error.getCompensationResult());
            assertEquals(1, fixture.calls.size());
        }
    }

    @Test
    void compensationFailuresKeepEveryOriginalOffset() {
        BulkRequest request = bulk(BulkItemType.CREATE, 2, false);
        request.setOptions(BulkOptions.builder().batchSize(2).continueOnFailure(false).build());
        fixture.responses.add(PersistenceProtocolFixture.bulk("create", 409, 409));
        fixture.responses.add(PersistenceProtocolFixture.bulk("update", 400, 400));
        BulkResult result = fixture.helper.prepareBulkConflict(request,
                (item, failure) -> BulkItem.builder().type(BulkItemType.UPDATE).fieldMap(Map.of("amount", 2)).build(), false).get();
        assertEquals(2, result.getFailed());
        assertEquals(List.of(0, 1), result.getFailureList().stream().map(BulkItemFailure::getItemIndex).collect(java.util.stream.Collectors.toList()));
        assertFalse(result.isSuccess());
        assertEquals(2, result.getBatchTotal());
    }

    @Test
    void byQueryOptionsAndTermRangeCompileExactly() {
        fixture.responses.add(Map.of("total", 0));
        fixture.execute(DeleteByQueryRequest.builder().index("sample-record")
                .query(PersistenceQuery.builder().termMap(Map.of("status", "ready")).rangeMap(Map.of("amount", Map.of("gte", 1))).build())
                .options(ByQueryOptions.builder().waitForCompletion(true).batchSize(8).scrollSize(9).slices(2).conflicts("proceed")
                        .requestsPerSecond(-1f).maxDocs(10L).waitForActiveShards(1).routing("route").refresh(true).timeoutMs(1200L).build()).build());
        assertEquals(Map.ofEntries(Map.entry("wait_for_completion", "true"), Map.entry("scroll_size", "9"), Map.entry("slices", "2"),
                Map.entry("conflicts", "proceed"), Map.entry("requests_per_second", "-1.0"), Map.entry("max_docs", "10"),
                Map.entry("wait_for_active_shards", "1"), Map.entry("routing", "route"), Map.entry("refresh", "true"), Map.entry("timeout", "1200ms")), fixture.calls.get(0).getParameters());
        Map<?, ?> dsl = (Map<?, ?>) fixture.codec.decode(fixture.calls.get(0).getBody()).get("query");
        assertEquals(Map.of("bool", Map.of("filter", List.of(Map.of("term", Map.of("status", "ready")), Map.of("range", Map.of("amount", Map.of("gte", 1)))))), dsl);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7})
    void scrollSizeFallsBackToBatchSizeOnlyWhenAbsent(int scrollSize) {
        fixture.responses.add(Map.of("total", 0));
        fixture.execute(DeleteByQueryRequest.builder().index("sample-record").query(query())
                .options(ByQueryOptions.builder().batchSize(8).scrollSize(scrollSize == 0 ? null : scrollSize).build()).build());
        assertEquals(scrollSize == 0 ? "8" : "7", fixture.calls.get(0).getParameters().get("scroll_size"));
    }

    @Test
    void rawQueryIsExclusiveAndMatchAllRequiresExplicitBoundedOptIn() {
        noCallRejected(DeleteByQueryRequest.builder().index("sample-record").query(PersistenceQuery.builder().rawJson("{\"term\":{\"amount\":1}}")
                .termMap(Map.of("amount", 1)).build()).build());
        fixture.properties.getByQuery().setAllowMatchAll(true);
        noCallRejected(DeleteByQueryRequest.builder().index("sample-record").build());
        fixture.responses.add(Map.of("total", 0));
        assertTrue(((ByQueryTaskResult) fixture.execute(DeleteByQueryRequest.builder().index("sample-record")
                .options(ByQueryOptions.builder().maxDocs(2L).build()).build())).isCompleted());
    }

    @ParameterizedTest
    @ValueSource(strings = {"wait_for", "invalid"})
    void invalidByQueryRefreshRejectsBeforeHttp(String refresh) {
        noCallRejected(DeleteByQueryRequest.builder().index("sample-record").query(query()).options(ByQueryOptions.builder().refreshPolicy(refresh).build()).build());
    }

    @Test
    void taskFailureTimeoutAndOwnershipAreExplicit() {
        fixture.responses.add(Map.of("completed", true, "response", Map.of("total", 3, "updated", 1, "version_conflicts", 1,
                "timed_out", true, "failures", List.of(Map.of("status", 400, "cause", "private-detail")))));
        ByQueryTaskResult result = (ByQueryTaskResult) fixture.execute(io.github.surezzzzzz.sdk.elasticsearch.persistence.model.request.TaskQueryRequest.builder().datasource("sample").taskId("node:1").build());
        assertTrue(result.isCompleted());
        assertEquals(3, result.getTotal());
        assertEquals(1, result.getVersionConflicts());
        assertEquals(2, result.getFailureList().size());
        assertTrue(result.getFailureList().stream().noneMatch(failure -> failure.getCause().contains("private-detail")));
        assertEquals("sample", fixture.calls.get(0).getSource());
        assertEquals("/_tasks/node%3A1", fixture.calls.get(0).getPath());
        noCallRejectedAfterReset(io.github.surezzzzzz.sdk.elasticsearch.persistence.model.request.TaskQueryRequest.builder().datasource("unknown").taskId("node:1").build());
    }

    @Test
    void taskErrorResponseIsNotSuccess() {
        fixture.responses.add(Map.of("completed", true, "error", Map.of("reason", "private-detail")));
        PersistenceExecutionException error = assertThrows(PersistenceExecutionException.class, () -> fixture.execute(
                io.github.surezzzzzz.sdk.elasticsearch.persistence.model.request.TaskQueryRequest.builder().datasource("sample").taskId("node:1").build()));
        assertEquals(ErrorCode.ES_RESPONSE_PARSE_FAILED, error.getErrorCode());
        assertNull(error.getCause());
        assertFalse(error.getMessage().contains("private-detail"));
    }

    @Test
    void successAndFailureEventsAreIndependentSanitizedSnapshots() {
        fixture.responses.add(PersistenceProtocolFixture.single("created"));
        PersistenceResult result = (PersistenceResult) fixture.execute(index());
        EsPersistenceEvent event = assertInstanceOf(EsPersistenceEvent.class, fixture.events.get(0));
        assertNull(event.getRequest());
        PersistenceResult snapshot = assertInstanceOf(PersistenceResult.class, event.getResult());
        assertNull(snapshot.getId());
        assertNull(snapshot.getIndex());
        assertNotSame(result, snapshot);
        result.setSuccess(false);
        assertTrue(snapshot.isSuccess());
        fixture.responses.add(new PersistenceRestException(503));
        assertThrows(PersistenceRestException.class, () -> fixture.execute(index()));
        EsPersistenceErrorEvent failed = assertInstanceOf(EsPersistenceErrorEvent.class, fixture.events.get(1));
        assertNull(failed.getRequest());
        assertNull(failed.getError().getCause());
        assertEquals("sample", failed.getContext().getDatasource());
    }

    @Test
    void listenerFailureCannotChangeSuccessOrMaskOriginalFailure() {
        fixture.configure(new io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessorChain(List.of()),
                event -> {
                    throw new IllegalStateException("private-detail");
                });
        fixture.responses.add(PersistenceProtocolFixture.single("created"));
        assertTrue(((PersistenceResult) fixture.execute(index())).isSuccess());
        fixture.responses.add(new PersistenceRestException(409));
        assertEquals(409, assertThrows(PersistenceRestException.class, () -> fixture.execute(index())).getStatus());
    }
}
