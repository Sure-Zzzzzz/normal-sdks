package io.github.surezzzzzz.sdk.elasticsearch.persistence.test.cases;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.IndexOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.SimpleElasticsearchPersistenceCoreConstant;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.BulkOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkItem;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.DeleteRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkItemFailure;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.ByQueryTaskResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.PersistenceEngine;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.engine.TypedPersistence;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.ConflictUpdateResolver;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.CreateThenUpdateOnConflictHelper;
import org.elasticsearch.ElasticsearchStatusException;
import org.elasticsearch.rest.RestStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CreateThenUpdateOnConflictHelper 单元测试
 *
 * @author surezzzzzz
 */
class CreateThenUpdateOnConflictHelperTest {

    @Test
    @DisplayName("单条 CREATE 成功时不执行 UPDATE")
    void createSuccessSkipsUpdate() {
        FakeEngine engine = new FakeEngine();
        engine.createResult = PersistenceResult.builder()
                .success(true)
                .operationType(PersistenceOperationType.CREATE)
                .build();

        PersistenceResult result = CreateThenUpdateOnConflictHelper.createThenUpdateOnConflict(engine,
                IndexRequest.builder().index("test_index").id("id-1").document(new Object()).build(),
                UpdateRequest.builder().fieldMap(Collections.singletonMap("extraField", "v2")).build());

        assertTrue(result.isSuccess(), "CREATE 成功应直接返回成功结果");
        assertEquals(1, engine.createCount, "CREATE 应执行一次");
        assertEquals(0, engine.updateCount, "无冲突时不应执行 UPDATE");
        assertEquals(IndexOperationType.CREATE, engine.lastCreateRequest.getOptions().getOperationType(),
                "helper 应强制 CREATE");
    }

    @Test
    @DisplayName("单条 CREATE 409 时执行 UPDATE，并填充 index/id/routing")
    void createConflictFallbackToUpdate() {
        FakeEngine engine = new FakeEngine();
        engine.createException = new RuntimeException("wrap",
                new ElasticsearchStatusException("conflict", RestStatus.CONFLICT));
        engine.updateResult = PersistenceResult.builder()
                .success(true)
                .operationType(PersistenceOperationType.UPDATE)
                .build();

        IndexRequest createRequest = IndexRequest.builder()
                .index("test_index")
                .id("id-2")
                .document(new Object())
                .options(IndexOptions.builder().routing("r-1").build())
                .build();
        UpdateRequest updateRequest = UpdateRequest.builder()
                .fieldMap(Collections.singletonMap("extraField", "v3"))
                .build();

        PersistenceResult result = CreateThenUpdateOnConflictHelper.createThenUpdateOnConflict(engine,
                createRequest, updateRequest);

        assertTrue(result.isSuccess(), "409 后 UPDATE 成功应返回成功");
        assertEquals(1, engine.createCount, "CREATE 应执行一次");
        assertEquals(1, engine.updateCount, "409 冲突后应执行 UPDATE");
        assertEquals("test_index", engine.lastUpdateRequest.getIndex(), "UPDATE index 应从 CREATE 继承");
        assertEquals("id-2", engine.lastUpdateRequest.getId(), "UPDATE id 应从 CREATE 继承");
        assertEquals("r-1", engine.lastUpdateRequest.getOptions().getRouting(), "UPDATE routing 应从 CREATE 继承");
    }

    @Test
    @DisplayName("单条 CREATE 非 409 异常原样抛出")
    void createNonConflictRethrows() {
        FakeEngine engine = new FakeEngine();
        RuntimeException error = new RuntimeException("boom");
        engine.createException = error;

        RuntimeException thrown = assertThrows(RuntimeException.class, () ->
                CreateThenUpdateOnConflictHelper.createThenUpdateOnConflict(engine,
                        IndexRequest.builder().index("test_index").id("id-3").document(new Object()).build(),
                        UpdateRequest.builder().fieldMap(Collections.singletonMap("extraField", "v4")).build()));

        assertSame(error, thrown, "非 409 异常应原样抛出");
        assertEquals(0, engine.updateCount, "非 409 异常不应执行 UPDATE");
    }

    @Test
    @DisplayName("bulk CREATE 冲突 resolver 返回 null 时保留原冲突失败")
    void bulkConflictResolverNullKeepsConflictFailure() {
        FakeEngine engine = new FakeEngine();
        engine.bulkResults.add(BulkResult.builder()
                .success(false)
                .hasFailure(true)
                .total(1)
                .succeeded(0)
                .failed(1)
                .datasource("primary")
                .failureList(Arrays.asList(BulkItemFailure.builder()
                        .itemIndex(0)
                        .type(BulkItemType.CREATE)
                        .id("id-null")
                        .index("test_index")
                        .status(SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_CONFLICT)
                        .build()))
                .build());

        BulkResult result = CreateThenUpdateOnConflictHelper.bulkCreateThenUpdateOnConflict(engine,
                BulkRequest.builder()
                        .defaultIndex("test_index")
                        .itemList(Arrays.asList(BulkItem.builder().document(new Object()).id("id-null").build()))
                        .build(),
                (createItem, failure) -> null);

        assertFalse(result.isSuccess(), "resolver 返回 null 时不能误判成功");
        assertEquals(1, result.getFailed(), "冲突失败应保留");
        assertEquals(1, result.getFailureList().size(), "失败明细应保留");
        assertEquals(SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_CONFLICT,
                result.getFailureList().get(0).getStatus().intValue(), "保留的失败应仍为 409");
        assertEquals(1, engine.bulkRequests.size(), "无 update item 时不应执行第二阶段 bulk");
    }

    @Test
    @DisplayName("bulk UPDATE 阶段失败时映射回原始 itemIndex")
    void bulkUpdateFailureMapsOriginalItemIndex() {
        FakeEngine engine = new FakeEngine();
        engine.bulkResults.add(BulkResult.builder()
                .success(false)
                .hasFailure(true)
                .total(2)
                .succeeded(0)
                .failed(2)
                .datasource("primary")
                .failureList(Arrays.asList(
                        BulkItemFailure.builder()
                                .itemIndex(0)
                                .type(BulkItemType.CREATE)
                                .id("id-1")
                                .status(SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_CONFLICT)
                                .build(),
                        BulkItemFailure.builder()
                                .itemIndex(1)
                                .type(BulkItemType.CREATE)
                                .id("id-2")
                                .status(SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_CONFLICT)
                                .build()))
                .build());
        engine.bulkResults.add(BulkResult.builder()
                .success(false)
                .hasFailure(true)
                .total(2)
                .succeeded(1)
                .failed(1)
                .datasource("primary")
                .failureList(Arrays.asList(BulkItemFailure.builder()
                        .itemIndex(1)
                        .type(BulkItemType.UPDATE)
                        .id("id-2")
                        .status(SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_BAD_REQUEST)
                        .errorType("mapper_parsing_exception")
                        .build()))
                .build());

        BulkResult result = CreateThenUpdateOnConflictHelper.bulkCreateThenUpdateOnConflict(engine,
                BulkRequest.builder()
                        .defaultIndex("test_index")
                        .itemList(Arrays.asList(
                                BulkItem.builder().document(new Object()).id("id-1").build(),
                                BulkItem.builder().document(new Object()).id("id-2").build()))
                        .build(),
                (createItem, failure) -> BulkItem.builder()
                        .fieldMap(Collections.singletonMap("extraField", "v"))
                        .build());

        assertFalse(result.isSuccess(), "update 阶段仍失败时整体应失败");
        assertEquals(1, result.getFailed(), "两个冲突中一个 update 成功、一个失败");
        assertEquals(1, result.getFailureList().size(), "只保留 update 失败");
        assertEquals(1, result.getFailureList().get(0).getItemIndex(),
                "update 失败 itemIndex 应映射回原始 CREATE 下标");
        assertEquals(BulkItemType.UPDATE, result.getFailureList().get(0).getType(),
                "失败类型应为最终失败的 UPDATE");
    }

    @Test
    @DisplayName("bulk CREATE 的 409 item 转 UPDATE 后合并最终结果")
    void bulkConflictFallbackMergeResult() {
        FakeEngine engine = new FakeEngine();
        engine.bulkResults.add(BulkResult.builder()
                .success(false)
                .hasFailure(true)
                .total(3)
                .succeeded(1)
                .failed(2)
                .datasource("primary")
                .tookMs(10L)
                .batchTotal(1)
                .batchSucceeded(0)
                .batchFailed(1)
                .failureList(Arrays.asList(
                        BulkItemFailure.builder()
                                .itemIndex(1)
                                .type(BulkItemType.CREATE)
                                .id("id-2")
                                .index("test_index")
                                .status(SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_CONFLICT)
                                .errorType("version_conflict_engine_exception")
                                .build(),
                        BulkItemFailure.builder()
                                .itemIndex(2)
                                .type(BulkItemType.CREATE)
                                .id("id-3")
                                .index("test_index")
                                .status(SimpleElasticsearchPersistenceCoreConstant.HTTP_STATUS_BAD_REQUEST)
                                .errorType("mapper_parsing_exception")
                                .build()))
                .build());
        engine.bulkResults.add(BulkResult.builder()
                .success(true)
                .hasFailure(false)
                .total(1)
                .succeeded(1)
                .failed(0)
                .datasource("primary")
                .tookMs(7L)
                .batchTotal(1)
                .batchSucceeded(1)
                .batchFailed(0)
                .failureList(Collections.emptyList())
                .build());

        BulkRequest createRequest = BulkRequest.builder()
                .defaultIndex("test_index")
                .options(BulkOptions.builder().continueOnFailure(false).pipeline("create-pipeline").batchSize(100).build())
                .itemList(Arrays.asList(
                        BulkItem.builder().type(BulkItemType.INDEX).document(new Object()).id("id-1").build(),
                        BulkItem.builder().type(BulkItemType.INDEX).document(new Object()).id("id-2").routing("r-2").build(),
                        BulkItem.builder().type(BulkItemType.INDEX).document(new Object()).id("id-3").build()))
                .build();

        BulkResult result = CreateThenUpdateOnConflictHelper.bulkCreateThenUpdateOnConflict(engine, createRequest,
                (createItem, failure) -> BulkItem.builder()
                        .fieldMap(Collections.singletonMap("extraField", "v5"))
                        .build());

        assertFalse(result.isSuccess(), "保留非冲突失败后整体应失败");
        assertEquals(3, result.getTotal(), "总数应保持 create 原始 item 数");
        assertEquals(2, result.getSucceeded(), "1 个首次成功 + 1 个冲突 update 成功");
        assertEquals(1, result.getFailed(), "仅保留非冲突失败");
        assertEquals(1, result.getFailureList().size(), "失败明细只剩非冲突失败");
        assertEquals(2, result.getFailureList().get(0).getItemIndex(), "非冲突失败下标应保留原始 itemIndex");
        assertEquals(17L, result.getTookMs(), "耗时应合并两阶段");

        assertEquals(BulkItemType.CREATE, engine.bulkRequests.get(0).getItemList().get(0).getType(),
                "第一阶段应强制 CREATE");
        assertTrue(engine.bulkRequests.get(0).getOptions().getContinueOnFailure(),
                "第一阶段应强制 continueOnFailure=true 以收集全部冲突");
        assertEquals("create-pipeline", engine.bulkRequests.get(0).getOptions().getPipeline(),
                "CREATE 阶段保留 pipeline");

        BulkRequest updateRequest = engine.bulkRequests.get(1);
        assertEquals(1, updateRequest.getItemList().size(), "第二阶段只提交冲突 item");
        assertEquals(BulkItemType.UPDATE, updateRequest.getItemList().get(0).getType(), "第二阶段 item 应为 UPDATE");
        assertEquals("id-2", updateRequest.getItemList().get(0).getId(), "UPDATE id 应继承冲突 create item");
        assertEquals("r-2", updateRequest.getItemList().get(0).getRouting(), "UPDATE routing 应继承冲突 create item");
        assertEquals("test_index", updateRequest.getDefaultIndex(), "UPDATE bulk defaultIndex 应继承");
        assertTrue(updateRequest.getOptions().getContinueOnFailure(), "第二阶段也应 continueOnFailure=true");
        org.junit.jupiter.api.Assertions.assertNull(updateRequest.getOptions().getPipeline(),
                "UPDATE 阶段不应继承 CREATE pipeline");
    }

    static class FakeEngine implements PersistenceEngine {
        int createCount;
        int updateCount;
        IndexRequest lastCreateRequest;
        UpdateRequest lastUpdateRequest;
        PersistenceResult createResult;
        PersistenceResult updateResult;
        RuntimeException createException;
        final List<BulkRequest> bulkRequests = new ArrayList<>();
        final List<BulkResult> bulkResults = new ArrayList<>();

        @Override
        public <T> PersistenceResult index(T document) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> PersistenceResult index(T document, IndexOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PersistenceResult index(IndexRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> PersistenceResult create(T document) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> PersistenceResult create(T document, IndexOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PersistenceResult create(IndexRequest request) {
            createCount++;
            lastCreateRequest = request;
            if (createException != null) {
                throw createException;
            }
            return createResult;
        }

        @Override
        public PersistenceResult update(UpdateRequest request) {
            updateCount++;
            lastUpdateRequest = request;
            return updateResult;
        }

        @Override
        public PersistenceResult delete(DeleteRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<PersistenceResult> indexAsync(T document) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<PersistenceResult> indexAsync(T document, IndexOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<PersistenceResult> indexAsync(IndexRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<PersistenceResult> createAsync(T document) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<PersistenceResult> createAsync(T document, IndexOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<PersistenceResult> createAsync(IndexRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<PersistenceResult> updateAsync(UpdateRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CompletableFuture<PersistenceResult> deleteAsync(DeleteRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public BulkResult bulk(BulkRequest request) {
            bulkRequests.add(request);
            return bulkResults.remove(0);
        }

        @Override
        public CompletableFuture<BulkResult> bulkAsync(BulkRequest request) {
            return CompletableFuture.completedFuture(bulk(request));
        }

        @Override
        public <T> BulkResult bulkIndex(List<T> documentList, BulkOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<BulkResult> bulkIndexAsync(List<T> documentList, BulkOptions options) {
            throw new UnsupportedOperationException();
        }

        @Override
        public PersistenceResult createThenUpdateOnConflict(IndexRequest createRequest, UpdateRequest updateRequest) {
            return CreateThenUpdateOnConflictHelper.createThenUpdateOnConflict(this, createRequest, updateRequest);
        }

        @Override
        public CompletableFuture<PersistenceResult> createThenUpdateOnConflictAsync(IndexRequest createRequest,
                                                                                    UpdateRequest updateRequest) {
            return CompletableFuture.completedFuture(createThenUpdateOnConflict(createRequest, updateRequest));
        }

        @Override
        public BulkResult bulkCreateThenUpdateOnConflict(BulkRequest createRequest, ConflictUpdateResolver resolver) {
            return CreateThenUpdateOnConflictHelper.bulkCreateThenUpdateOnConflict(this, createRequest, resolver);
        }

        @Override
        public CompletableFuture<BulkResult> bulkCreateThenUpdateOnConflictAsync(BulkRequest createRequest,
                                                                                 ConflictUpdateResolver resolver) {
            return CompletableFuture.completedFuture(bulkCreateThenUpdateOnConflict(createRequest, resolver));
        }

        @Override
        public ByQueryTaskResult updateByQuery(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateByQueryRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByQueryTaskResult deleteByQuery(io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.DeleteByQueryRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ByQueryTaskResult getTask(String datasource, String taskId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> TypedPersistence<T> forEntity(Class<T> entityClass) {
            throw new UnsupportedOperationException();
        }
    }
}
