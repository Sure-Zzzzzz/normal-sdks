package io.github.surezzzzzz.sdk.audit.search.elasticsearch.test.cases;

import io.github.surezzzzzz.sdk.audit.search.elasticsearch.handler.EsAuditHandler;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.listener.EsAuditEventListener;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.model.EsAuditRecord;
import io.github.surezzzzzz.sdk.audit.search.elasticsearch.provider.EsAuditUserProvider;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryEvent;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.AggExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.QueryExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 四种事件、裁剪统计、线程上下文与处理器隔离契约。
 */
@Slf4j
class AuditEventTest {
    private final List<EsAuditRecord> records = new ArrayList<>();

    private EsAuditEventListener listener(Executor executor) {
        return new EsAuditEventListener(Collections.singletonList(records::add), null, null, executor);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void querySnapshotPreservesCountFlagAndDoesNotInventReturnedSize(boolean count) {
        EsQueryEvent event = new EsQueryEvent(this, QueryRequest.builder().countOnly(count).build(),
                QueryResponse.builder().total(5L).took(7L).items(null).build(),
                new QueryExecutionContext(null, "sample", 2, "QUERY_API"));
        listener(Runnable::run).onEsQueryEvent(event);
        EsAuditRecord record = records.get(0);
        assertEquals("success", record.getResult());
        assertEquals(Boolean.valueOf(count), record.getCountOnly());
        assertEquals(Long.valueOf(5), record.getTotal());
        assertEquals(Long.valueOf(7), record.getTook());
        assertEquals(Integer.valueOf(2), record.getDowngradeLevel());
        assertEquals(Long.valueOf(event.getTimestamp()), record.getTimestamp());
        assertNull(record.getReturnedSize());
        assertNull(record.getQueryCondition());
        assertNull(record.getActualIndices());
        assertNull(record.getIndexAlias());
        assertNull(record.getUserId());
    }

    @Test
    void explicitEmptyResultsAreDifferentFromOmittedResults() {
        listener(Runnable::run).onEsQueryEvent(new EsQueryEvent(this, null,
                QueryResponse.builder().items(Collections.emptyList()).total(0L).build(), null));
        assertEquals(Integer.valueOf(0), records.get(0).getReturnedSize());
    }

    @Test
    void aggregationSnapshotKeepsOnlyPublishedFacts() {
        listener(Runnable::run).onEsAggEvent(new EsAggEvent(this, AggRequest.builder().build(),
                AggResponse.builder().took(8L).aggregations(null).build(), new AggExecutionContext(null, "sample", 0, "QUERY_API")));
        assertEquals("success", records.get(0).getResult());
        assertEquals(Long.valueOf(8), records.get(0).getTook());
        assertNull(records.get(0).getTotal());
        assertNull(records.get(0).getCountOnly());
        assertNull(records.get(0).getReturnedSize());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void queryFailuresKeepCountFlag(boolean count) {
        listener(Runnable::run).onEsQueryErrorEvent(new EsQueryErrorEvent(this,
                QueryRequest.builder().sourceType("QUERY_API").countOnly(count).build(), new IllegalStateException("sample"), "sample", 0, count, null));
        assertEquals("failure", records.get(0).getResult());
        assertEquals(Boolean.valueOf(count), records.get(0).getCountOnly());
        assertEquals("sample", records.get(0).getErrorMessage());
        assertNull(records.get(0).getTotal());
        assertNull(records.get(0).getReturnedSize());
    }

    @Test
    void aggregationErrorHasNoCountOrResultStatistics() {
        listener(Runnable::run).onEsAggErrorEvent(new EsAggErrorEvent(this,
                AggRequest.builder().sourceType("QUERY_API").build(), new IllegalStateException("sample"), null));
        assertEquals("failure", records.get(0).getResult());
        assertNull(records.get(0).getDatasource());
        assertNull(records.get(0).getCountOnly());
        assertNull(records.get(0).getTook());
    }

    @Test
    void providersAreReadBeforeDispatchAndTheirErrorsAreIsolated() {
        EsAuditUserProvider users = mock(EsAuditUserProvider.class);
        when(users.getClientId()).thenThrow(new IllegalStateException("private"));
        when(users.getUserId()).thenReturn("sample-user");
        List<Runnable> queue = new ArrayList<>();
        EsAuditEventListener listener = new EsAuditEventListener(Collections.singletonList(records::add), users, () -> "sample-trace", queue::add);
        listener.onEsQueryEvent(new EsQueryEvent(this, null, null, null));
        when(users.getUserId()).thenReturn("changed");
        queue.get(0).run();
        assertNull(records.get(0).getClientId());
        assertEquals("sample-user", records.get(0).getUserId());
        assertEquals("sample-trace", records.get(0).getTraceId());
        verify(users, times(1)).getUserId();
    }

    @Test
    void handlerMutationAndFailureDoNotCorruptNextHandler() {
        EsAuditHandler broken = record -> {
            record.setDatasource("changed");
            record.getActualIndices()[0] = "changed";
            throw new IllegalStateException("private");
        };
        EsAuditEventListener listener = new EsAuditEventListener(Arrays.asList(broken, records::add), null, null, Runnable::run);
        String[] indices = {"sample"};
        assertDoesNotThrow(() -> listener.onEsQueryEvent(new EsQueryEvent(this, null, null, new QueryExecutionContext(indices, "sample", 0, "QUERY_API"))));
        assertEquals("sample", records.get(0).getDatasource());
        assertArrayEquals(new String[]{"sample"}, records.get(0).getActualIndices());
        assertArrayEquals(new String[]{"sample"}, indices);
    }

    @Test
    void malformedEventAndRejectedSubmissionDoNotEscape() {
        EsAuditEventListener listener = listener(task -> {
            throw new RejectedExecutionException("private");
        });
        assertDoesNotThrow(() -> listener.onEsQueryEvent(new EsQueryEvent(this, null, null, null)));
        assertDoesNotThrow(() -> listener.onEsAggEvent(null));
        assertDoesNotThrow(() -> listener.onEsQueryErrorEvent(null));
        assertDoesNotThrow(() -> listener.onEsAggErrorEvent(null));
        assertTrue(records.isEmpty());
    }
}
