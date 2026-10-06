package io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.test.cases;

import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.configuration.PersistenceAuditProperties;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.handler.EsPersistenceAuditHandler;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.listener.EsPersistenceAuditEventListener;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.model.EsPersistenceAuditRecord;
import io.github.surezzzzzz.sdk.audit.persistence.elasticsearch.provider.EsPersistenceAuditUserProvider;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.event.EsPersistenceErrorEvent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.event.EsPersistenceEvent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.*;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 持久化事件转换、裁剪统计、失败明细与请求线程身份契约。
 */
@Slf4j
class AuditEventTest {
    private final PersistenceAuditProperties properties = new PersistenceAuditProperties();
    private final List<EsPersistenceAuditRecord> records = new ArrayList<>();

    private EsPersistenceAuditEventListener listener(Executor executor) {
        return new EsPersistenceAuditEventListener(Collections.singletonList(records::add), null, null, properties, executor);
    }

    @ParameterizedTest
    @EnumSource(PersistenceOperationType.class)
    void convertsEveryOperationWithoutOriginalRequest(PersistenceOperationType operation) {
        listener(Runnable::run).onEsPersistenceEvent(new EsPersistenceEvent(this, null,
                PersistenceResult.builder().success(true).operationType(operation).datasource("sample").tookMs(5).build(),
                PersistenceExecutionContext.builder().operationType(operation).clientAsync(false).startTimeMs(10L).build()));
        EsPersistenceAuditRecord record = records.get(0);
        assertEquals(operation.getCode(), record.getOperationType());
        assertEquals("success", record.getResult());
        assertEquals("sample", record.getDatasource());
        assertEquals(Long.valueOf(5), record.getTookMs());
        assertNull(record.getIndex());
        assertNull(record.getDocumentId());
        assertNull(record.getRequestType());
        assertNull(record.getUserId());
        assertNotNull(record.getTimestamp());
    }

    @Test
    void bulkSummaryDoesNotInventConflictFromMissingDetails() {
        listener(Runnable::run).onEsPersistenceEvent(new EsPersistenceEvent(this, null,
                BulkResult.builder().success(false).hasFailure(true).total(3).succeeded(2).failed(1).build(), null));
        EsPersistenceAuditRecord record = records.get(0);
        assertEquals("partial_failure", record.getResult());
        assertEquals(Integer.valueOf(3), record.getBulkItemCount());
        assertEquals(Integer.valueOf(1), record.getBulkFailed());
        assertNull(record.getConflict());
        assertTrue(record.getFailureList().isEmpty());
    }

    @Test
    void conflictAfterTruncationStillAffectsSummary() {
        properties.getRecord().setMaxFailureSize(1);
        listener(Runnable::run).onEsPersistenceEvent(new EsPersistenceEvent(this, null,
                BulkResult.builder().success(false).hasFailure(true).total(2).failed(2).failureList(Arrays.asList(
                        BulkItemFailure.builder().status(429).build(), BulkItemFailure.builder().status(409).build())).build(), null));
        assertEquals(1, records.get(0).getFailureList().size());
        assertTrue(records.get(0).getConflict());
    }

    @Test
    void submittedIsNotCompletedAndMissingDetailsDoNotProveSuccess() {
        EsPersistenceAuditEventListener listener = listener(Runnable::run);
        listener.onEsPersistenceEvent(new EsPersistenceEvent(this, null, ByQueryTaskResult.builder().completed(false).build(), null));
        listener.onEsPersistenceEvent(new EsPersistenceEvent(this, null, ByQueryTaskResult.builder().completed(true).total(4).updated(4).build(), null));
        assertEquals("task_submitted", records.get(0).getResult());
        assertTrue(records.get(0).getServerAsyncTask());
        assertNull(records.get(0).getTaskId());
        assertEquals("task_completed", records.get(1).getResult());
        assertNull(records.get(1).getSuccess());
        assertNull(records.get(1).getPartial());
        assertFalse(records.get(1).getServerAsyncTask());
    }

    @Test
    void byQueryFailureAndVersionConflictRemainVisible() {
        listener(Runnable::run).onEsPersistenceEvent(new EsPersistenceEvent(this, null,
                ByQueryTaskResult.builder().completed(true).versionConflicts(1).failureList(Collections.singletonList(
                        ByQueryFailure.builder().status("409").cause("sample").build())).build(), null));
        assertEquals("partial_failure", records.get(0).getResult());
        assertTrue(records.get(0).getConflict());
        assertFalse(records.get(0).getSuccess());
        listener(Runnable::run).onEsPersistenceEvent(new EsPersistenceEvent(this, null,
                ByQueryTaskResult.builder().completed(true).versionConflicts(1).build(), null));
        assertEquals("task_completed", records.get(1).getResult());
        assertTrue(records.get(1).getPartial());
        assertNull(records.get(1).getSuccess());
    }

    @Test
    void failureCarriesErrorCodeWithoutOriginalPayload() {
        listener(Runnable::run).onEsPersistenceErrorEvent(new EsPersistenceErrorEvent(this, null,
                new SimpleElasticsearchPersistenceException("SAMPLE", "safe"), null));
        assertEquals("failure", records.get(0).getResult());
        assertEquals("SAMPLE", records.get(0).getErrorCode());
        assertEquals("safe", records.get(0).getErrorMessage());
        assertFalse(records.get(0).getSuccess());
    }

    @Test
    void identityIsCapturedBeforeAsyncDispatchAndProviderFailureIsIsolated() {
        EsPersistenceAuditUserProvider users = mock(EsPersistenceAuditUserProvider.class);
        when(users.getClientId()).thenThrow(new IllegalStateException("private"));
        when(users.getUserId()).thenReturn("sample-user");
        List<Runnable> queue = new ArrayList<>();
        EsPersistenceAuditEventListener listener = new EsPersistenceAuditEventListener(Collections.singletonList(records::add), users,
                () -> "sample-trace", properties, queue::add);
        listener.onEsPersistenceEvent(new EsPersistenceEvent(this, null, PersistenceResult.builder().success(true).build(), null));
        assertTrue(records.isEmpty());
        when(users.getUserId()).thenReturn("changed");
        queue.get(0).run();
        assertNull(records.get(0).getClientId());
        assertEquals("sample-user", records.get(0).getUserId());
        assertEquals("sample-trace", records.get(0).getTraceId());
        verify(users, times(1)).getUserId();
    }

    @Test
    void handlerMutationAndFailureDoNotAffectNextHandler() {
        EsPersistenceAuditHandler broken = record -> {
            record.setDatasource("changed");
            record.getFailureList().get(0).setStatus(500);
            throw new IllegalStateException("private");
        };
        EsPersistenceAuditEventListener listener = new EsPersistenceAuditEventListener(Arrays.asList(broken, records::add), null, null, properties, Runnable::run);
        assertDoesNotThrow(() -> listener.onEsPersistenceEvent(new EsPersistenceEvent(this, null,
                BulkResult.builder().datasource("sample").failureList(Collections.singletonList(BulkItemFailure.builder().status(409).build())).build(), null)));
        assertEquals("sample", records.get(0).getDatasource());
        assertEquals(Integer.valueOf(409), records.get(0).getFailureList().get(0).getStatus());
    }

    @Test
    void rejectionAndMalformedEventDoNotEscape() {
        EsPersistenceAuditEventListener listener = listener(task -> {
            throw new RejectedExecutionException("private");
        });
        assertDoesNotThrow(() -> listener.onEsPersistenceEvent(new EsPersistenceEvent(this, null, PersistenceResult.builder().success(true).build(), null)));
        assertDoesNotThrow(() -> listener.onEsPersistenceEvent(new EsPersistenceEvent(this, null, new Object(), null)));
        assertDoesNotThrow(() -> listener.onEsPersistenceErrorEvent(null));
        assertTrue(records.isEmpty());
    }
}
