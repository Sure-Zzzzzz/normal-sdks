package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.classifier.DefaultBulkFailureClassifier;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.IndexOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.BulkOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkItem;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkItemFailure;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.DefaultTypedPersistence;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.TypedPersistence;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessor;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentPreProcessorChain;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.processor.DocumentProcessContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.ConflictUpdateResolver;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.DocumentMetadataHelper;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.test.support.PersistenceProtocolFixture;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 迁移实体元数据、Typed、处理链和失败分类场景，不引用旧 HLRC 适配器。
 */
@Slf4j
class PersistenceSupportMigrationTest {
    @Test
    void explicitMetadataWinsAndInheritedIdIsResolved() {
        Sample row = new Sample("annotated", 1);
        assertEquals("explicit", DocumentMetadataHelper.resolveIndex(row, "explicit"));
        assertEquals("sample-record", DocumentMetadataHelper.resolveIndex(row, null));
        assertEquals("sample-record", DocumentMetadataHelper.resolveIndex(Sample.class));
        assertEquals("explicit", DocumentMetadataHelper.resolveId(row, "explicit"));
        assertEquals("annotated", DocumentMetadataHelper.resolveId(row, null));
        assertEquals("inherited", DocumentMetadataHelper.resolveId(new Child(), null));
    }

    @Test
    void missingMetadataHasExplicitNullAndFailureSemantics() {
        assertThrows(PersistenceExecutionException.class, () -> DocumentMetadataHelper.resolveIndex((Class<?>) null));
        assertThrows(PersistenceExecutionException.class, () -> DocumentMetadataHelper.resolveIndex(Plain.class));
        assertThrows(PersistenceExecutionException.class, () -> DocumentMetadataHelper.resolveIndex(null, null));
        assertNull(DocumentMetadataHelper.resolveId(null, null));
        assertNull(DocumentMetadataHelper.resolveId(new Plain(), null));
        assertNull(DocumentMetadataHelper.resolveId(new Sample(null, 1), null));
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500, 503})
    void retryableStatuses(int status) {
        assertTrue(new DefaultBulkFailureClassifier().retryable(status, null, null));
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 403, 404, 409})
    void permanentStatuses(int status) {
        assertFalse(new DefaultBulkFailureClassifier().retryable(status, null, null));
    }

    @Test
    void nullStatusIsNotRetryable() {
        assertFalse(new DefaultBulkFailureClassifier().retryable(null, "private-detail", "private-detail"));
    }

    @Test
    void preProcessorsKeepOrderAndUnsupportedTypesAreSkipped() {
        DocumentPreProcessor first = mock(DocumentPreProcessor.class), second = mock(DocumentPreProcessor.class), skipped = mock(DocumentPreProcessor.class);
        when(first.supports(String.class)).thenReturn(true);
        when(second.supports(String.class)).thenReturn(true);
        when(first.process(eq("a"), any())).thenReturn("ab");
        when(second.process(eq("ab"), any())).thenReturn("abc");
        assertEquals("abc", new DocumentPreProcessorChain(List.of(first, skipped, second)).process("a", DocumentProcessContext.builder().build()));
        verify(skipped, never()).process(any(), any());
        assertEquals("a", new DocumentPreProcessorChain(List.of()).process("a", null));
    }

    @Test
    void nullPreProcessorResultRejectsBeforeHttp() {
        DocumentPreProcessor processor = mock(DocumentPreProcessor.class);
        when(processor.supports(any())).thenReturn(true);
        PersistenceProtocolFixture fixture = new PersistenceProtocolFixture();
        fixture.configure(new DocumentPreProcessorChain(List.of(processor)), fixture.events::add);
        assertThrows(PersistenceExecutionException.class, () -> fixture.execute(IndexRequest.builder().index("sample-record").id("row1").document(Map.of("amount", 1)).build()));
        assertTrue(fixture.calls.isEmpty());
    }

    @Test
    void preProcessorReceivesFrozenTargetAndBulkOffsetButNotUpdates() {
        PersistenceProtocolFixture fixture = new PersistenceProtocolFixture();
        DocumentPreProcessor processor = mock(DocumentPreProcessor.class);
        when(processor.supports(any())).thenReturn(true);
        when(processor.process(any(), any())).thenAnswer(call -> call.getArgument(0));
        fixture.configure(new DocumentPreProcessorChain(List.of(processor)), fixture.events::add);
        when(fixture.targets.writeIndex("sample-record")).thenReturn("sample-record-2024");
        fixture.responses.add(PersistenceProtocolFixture.bulk("index", 201, 201));
        List<BulkItem> rows = List.of(BulkItem.builder().type(BulkItemType.INDEX).id("1").document(Map.of("amount", 1)).build(),
                BulkItem.builder().type(BulkItemType.INDEX).id("2").document(Map.of("amount", 2)).build());
        fixture.execute(BulkRequest.builder().defaultIndex("sample-record").itemList(rows).build());
        ArgumentCaptor<DocumentProcessContext> contexts = ArgumentCaptor.forClass(DocumentProcessContext.class);
        verify(processor, times(2)).process(any(), contexts.capture());
        for (int i = 0; i < 2; i++) {
            DocumentProcessContext context = contexts.getAllValues().get(i);
            assertTrue(context.isBulk());
            assertEquals(i, context.getBulkItemIndex());
            assertEquals("sample-record", context.getRawIndex());
            assertEquals("sample-record-2024", context.getRenderedIndex());
            assertEquals("sample", context.getDatasource());
        }
        fixture.responses.add(PersistenceProtocolFixture.single("updated"));
        fixture.execute(UpdateRequest.builder().index("sample-record-2023").id("row1").fieldMap(Map.of("amount", 3)).build());
        verify(processor, times(2)).process(any(), any());
        assertEquals("/sample-record-2023/_update/row1", fixture.calls.get(1).getPath());
    }

    private TypedPersistence<Sample> typed(PersistenceEngine engine) {
        return new DefaultTypedPersistence<>(engine, Sample.class, null, null, new ArrayList<>(), null,
                IndexOptions.builder().build(), BulkOptions.builder().build());
    }

    @Test
    void typedCreateForcesCreateButIndexPreservesExplicitCreate() {
        PersistenceEngine engine = mock(PersistenceEngine.class);
        Sample row = new Sample("1", 1);
        typed(engine).create(row, IndexOptions.builder().operationType(IndexOperationType.INDEX).build());
        typed(engine).index(row, IndexOptions.builder().operationType(IndexOperationType.CREATE).build());
        ArgumentCaptor<IndexRequest> request = ArgumentCaptor.forClass(IndexRequest.class);
        verify(engine).create(request.capture());
        assertEquals(IndexOperationType.CREATE, request.getValue().getOptions().getOperationType());
        verify(engine).index(request.capture());
        assertEquals(IndexOperationType.CREATE, request.getValue().getOptions().getOperationType());
    }

    @Test
    void typedExplicitOptionsDoNotMergeDefaultsAndDefaultsAreCopied() {
        PersistenceEngine engine = mock(PersistenceEngine.class);
        IndexOptions defaults = IndexOptions.builder().pipeline("default-pipeline").refresh(true).routing("default-route").build();
        TypedPersistence<Sample> typed = typed(engine).withDefaultIndexOptions(defaults);
        defaults.setPipeline("changed");
        typed.index(new Sample("1", 1));
        typed.index(new Sample("2", 2), IndexOptions.builder().timeoutMs(5L).build());
        ArgumentCaptor<IndexRequest> request = ArgumentCaptor.forClass(IndexRequest.class);
        verify(engine, times(2)).index(request.capture());
        assertEquals("default-pipeline", request.getAllValues().get(0).getOptions().getPipeline());
        IndexOptions explicit = request.getAllValues().get(1).getOptions();
        assertNull(explicit.getPipeline());
        assertNull(explicit.getRefresh());
        assertNull(explicit.getRouting());
        assertEquals(5L, explicit.getTimeoutMs());
    }

    @Test
    void typedRoutingResolverYieldsToExplicitRouting() {
        PersistenceEngine engine = mock(PersistenceEngine.class);
        TypedPersistence<Sample> typed = typed(engine)
                .withIndexResolver(row -> "resolved-index").withIdResolver(Sample::getId).withRoutingResolver(row -> "resolver-route");
        typed.index(new Sample("1", 1));
        typed.index(new Sample("2", 2), IndexOptions.builder().routing("explicit-route").build());
        ArgumentCaptor<IndexRequest> request = ArgumentCaptor.forClass(IndexRequest.class);
        verify(engine, times(2)).index(request.capture());
        assertEquals("resolved-index", request.getValue().getIndex());
        assertEquals("2", request.getValue().getId());
        assertEquals("resolver-route", request.getAllValues().get(0).getOptions().getRouting());
        assertEquals("explicit-route", request.getValue().getOptions().getRouting());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void typedBulkOperationTypeAndExplicitOptions(boolean create) {
        PersistenceEngine engine = mock(PersistenceEngine.class);
        TypedPersistence<Sample> typed = typed(engine)
                .withDefaultBulkOptions(BulkOptions.builder().batchSize(99).pipeline("default-pipeline").build());
        if (create) typed.bulkCreate(List.of(new Sample("1", 1)), BulkOptions.builder().batchSize(2).build());
        else typed.bulkIndex(List.of(new Sample("1", 1)), BulkOptions.builder().batchSize(2).build());
        ArgumentCaptor<BulkRequest> request = ArgumentCaptor.forClass(BulkRequest.class);
        verify(engine).bulk(request.capture());
        assertEquals(create ? BulkItemType.CREATE : BulkItemType.INDEX, request.getValue().getItemList().get(0).getType());
        assertEquals(2, request.getValue().getOptions().getBatchSize());
        assertNull(request.getValue().getOptions().getPipeline());
    }

    @Test
    void typedValidatorsAndNullConfigurationRejectBeforeDelegate() {
        PersistenceEngine engine = mock(PersistenceEngine.class);
        TypedPersistence<Sample> typed = typed(engine)
                .withValidator(row -> {
                    if (row.getAmount() < 0) throw new IllegalArgumentException("测试校验拒绝");
                });
        assertThrows(IllegalArgumentException.class, () -> typed.index(new Sample("1", -1)));
        assertThrows(PersistenceExecutionException.class, () -> typed.index(null));
        assertThrows(PersistenceExecutionException.class, () -> typed.index(new Sample("1", 1), null));
        assertThrows(PersistenceExecutionException.class, () -> typed.withValidator(null));
        assertThrows(PersistenceExecutionException.class, () -> typed.withIndexResolver(null));
        assertThrows(PersistenceExecutionException.class, () -> typed.withDefaultBulkOptions(null));
        verifyNoInteractions(engine);
    }

    @Test
    void typedSingleFallbackIsCopiedAndOriginalObjectIsNotModified() {
        PersistenceEngine engine = mock(PersistenceEngine.class);
        UpdateRequest fallback = UpdateRequest.builder().fieldMap(Map.of("amount", 2)).build();
        typed(engine).withIndexResolver(row -> "sample-record").withIdResolver(Sample::getId)
                .createThenUpdateOnConflict(new Sample("row1", 1), row -> fallback);
        ArgumentCaptor<IndexRequest> create = ArgumentCaptor.forClass(IndexRequest.class);
        ArgumentCaptor<UpdateRequest> update = ArgumentCaptor.forClass(UpdateRequest.class);
        verify(engine).createThenUpdateOnConflict(create.capture(), update.capture());
        assertEquals("row1", update.getValue().getId());
        assertEquals("sample-record", update.getValue().getIndex());
        assertNull(fallback.getIndex());
        assertNull(fallback.getId());
        assertNotSame(fallback, update.getValue());
    }

    @Test
    void typedBulkResolverUsesOriginalPositionAndOwnsListSnapshot() {
        PersistenceEngine engine = mock(PersistenceEngine.class);
        List<Sample> documents = new ArrayList<>(List.of(new Sample("1", 1), new Sample("2", 2)));
        List<Integer> resolved = new ArrayList<>();
        typed(engine).bulkCreateThenUpdateOnConflict(documents, row -> {
            resolved.add(row.getAmount());
            return BulkItem.builder().type(BulkItemType.UPDATE).fieldMap(Map.of("amount", row.getAmount())).build();
        }, BulkOptions.builder().build());
        documents.clear();
        ArgumentCaptor<BulkRequest> request = ArgumentCaptor.forClass(BulkRequest.class);
        ArgumentCaptor<ConflictUpdateResolver> resolver = ArgumentCaptor.forClass(ConflictUpdateResolver.class);
        verify(engine).bulkCreateThenUpdateOnConflict(request.capture(), resolver.capture());
        BulkItem value = resolver.getValue().resolve(request.getValue().getItemList().get(1), BulkItemFailure.builder().itemIndex(1).build());
        assertEquals(List.of(2), resolved);
        assertEquals(Map.of("amount", 2), value.getFieldMap());
        assertEquals(BulkItemType.UPDATE, value.getType());
    }

    @Test
    void typedAsyncMethodsUseAsyncDelegateWithoutSynchronousFallback() {
        PersistenceEngine engine = mock(PersistenceEngine.class);
        CompletableFuture<PersistenceResult> result = CompletableFuture.completedFuture(PersistenceResult.builder().success(true).build());
        when(engine.indexAsync(any(IndexRequest.class))).thenReturn(result);
        when(engine.createAsync(any(IndexRequest.class))).thenReturn(result);
        when(engine.bulkAsync(any())).thenReturn(CompletableFuture.completedFuture(BulkResult.builder().success(true).build()));
        TypedPersistence<Sample> typed = typed(engine);
        Sample row = new Sample("1", 1);
        assertSame(result, typed.indexAsync(row));
        assertSame(result, typed.createAsync(row));
        assertTrue(typed.bulkIndexAsync(List.of(row)).join().isSuccess());
        assertTrue(typed.bulkCreateAsync(List.of(row)).join().isSuccess());
        verify(engine, never()).index(any(IndexRequest.class));
        verify(engine, never()).create(any(IndexRequest.class));
        verify(engine, never()).bulk(any());
    }

    @Document(indexName = "sample-record")
    @Data
    @AllArgsConstructor
    static class Sample {
        @Id
        private String id;
        private int amount;
    }

    static class Plain {
    }

    static class Child extends Sample {
        Child() {
            super("inherited", 1);
        }
    }
}
