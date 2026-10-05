package io.github.surezzzzzz.sdk.elasticsearch.persistence.engine;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.IndexOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.BulkOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.ByQueryTaskResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.executor.PersistenceExecutorRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.model.request.TaskQueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.ConflictUpdateResolver;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.ElasticsearchWriteApiHelper;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;

/**
 * 默认业务持久化门面
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class DefaultPersistenceEngine implements PersistenceEngine {

    private final PersistenceExecutorRegistry executorRegistry;
    private final Executor asyncExecutor;
    private final ElasticsearchWriteApiHelper writeHelper;

    /**
     * 写入或覆盖实体文档，显式选项按完整选择使用。
     */
    @Override
    public <T> PersistenceResult index(T document) {
        return index(document, IndexOptions.builder().build());
    }

    /**
     * 写入或覆盖实体文档，显式选项按完整选择使用。
     */
    @Override
    public <T> PersistenceResult index(T document, IndexOptions options) {
        if (options == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        return index(IndexRequest.builder().document(document).options(options).build());
    }

    /**
     * 写入或覆盖实体文档，显式选项按完整选择使用。
     */
    @Override
    public PersistenceResult index(IndexRequest request) {
        return execute(indexRequest(request, request != null && request.getOptions() != null && request.getOptions().getOperationType() != null
                ? request.getOptions().getOperationType() : IndexOperationType.INDEX));
    }

    /**
     * 仅新建文档，已有 ID 返回冲突。
     */
    @Override
    public <T> PersistenceResult create(T document) {
        return create(document, IndexOptions.builder().build());
    }

    /**
     * 仅新建文档，已有 ID 返回冲突。
     */
    @Override
    public <T> PersistenceResult create(T document, IndexOptions options) {
        if (options == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        return create(IndexRequest.builder().document(document).options(options).build());
    }

    /**
     * 仅新建文档，已有 ID 返回冲突。
     */
    @Override
    public PersistenceResult create(IndexRequest request) {
        return execute(indexRequest(request, IndexOperationType.CREATE));
    }

    /**
     * 在指定物理索引上局部更新文档。
     */
    @Override
    public PersistenceResult update(UpdateRequest request) {
        return execute(request);
    }

    /**
     * 在指定物理索引上按 ID 删除文档。
     */
    @Override
    public PersistenceResult delete(DeleteRequest request) {
        return execute(request);
    }

    /**
     * 以客户端异步方式写入文档，返回最终执行结果。
     */
    @Override
    public <T> CompletableFuture<PersistenceResult> indexAsync(T document) {
        return indexAsync(document, IndexOptions.builder().build());
    }

    /**
     * 以客户端异步方式写入文档，返回最终执行结果。
     */
    @Override
    public <T> CompletableFuture<PersistenceResult> indexAsync(T document, IndexOptions options) {
        if (options == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        return indexAsync(IndexRequest.builder().document(document).options(options).build());
    }

    /**
     * 以客户端异步方式写入文档，返回最终执行结果。
     */
    @Override
    public CompletableFuture<PersistenceResult> indexAsync(IndexRequest request) {
        return executeAsync(indexRequest(request, request != null && request.getOptions() != null && request.getOptions().getOperationType() != null
                ? request.getOptions().getOperationType() : IndexOperationType.INDEX));
    }

    /**
     * 以客户端异步方式仅新建文档。
     */
    @Override
    public <T> CompletableFuture<PersistenceResult> createAsync(T document) {
        return createAsync(document, IndexOptions.builder().build());
    }

    /**
     * 以客户端异步方式仅新建文档。
     */
    @Override
    public <T> CompletableFuture<PersistenceResult> createAsync(T document, IndexOptions options) {
        if (options == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        return createAsync(IndexRequest.builder().document(document).options(options).build());
    }

    /**
     * 以客户端异步方式仅新建文档。
     */
    @Override
    public CompletableFuture<PersistenceResult> createAsync(IndexRequest request) {
        return executeAsync(indexRequest(request, IndexOperationType.CREATE));
    }

    /**
     * 以客户端异步方式局部更新文档。
     */
    @Override
    public CompletableFuture<PersistenceResult> updateAsync(UpdateRequest request) {
        return executeAsync(request);
    }

    /**
     * 以客户端异步方式删除文档。
     */
    @Override
    public CompletableFuture<PersistenceResult> deleteAsync(DeleteRequest request) {
        return executeAsync(request);
    }

    /**
     * 提交混合批量操作，返回逐项失败与分批统计。
     */
    @Override
    public BulkResult bulk(BulkRequest request) {
        return execute(request);
    }

    /**
     * 以客户端异步方式提交混合批量操作。
     */
    @Override
    public CompletableFuture<BulkResult> bulkAsync(BulkRequest request) {
        return executeAsync(request);
    }

    /**
     * 批量写入同类实体文档。
     */
    @Override
    public <T> BulkResult bulkIndex(List<T> documentList, BulkOptions options) {
        if (options == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        List<BulkItem> itemList = new ArrayList<>();
        if (documentList != null) {
            for (T document : documentList) {
                itemList.add(BulkItem.builder().type(BulkItemType.INDEX)
                        .document(document).build());
            }
        }
        return bulk(BulkRequest.builder().itemList(itemList).options(options).build());
    }

    /**
     * 以客户端异步方式批量写入同类实体文档。
     */
    @Override
    public <T> CompletableFuture<BulkResult> bulkIndexAsync(List<T> documentList, BulkOptions options) {
        if (options == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        List<BulkItem> items = new ArrayList<>();
        if (documentList == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        for (T document : documentList)
            items.add(BulkItem.builder().type(BulkItemType.INDEX).document(document).build());
        return bulkAsync(BulkRequest.builder().itemList(items).options(options).build());
    }

    /**
     * 先新建，仅在 409 冲突时于同一物理目标执行补偿更新。
     */
    @Override
    public PersistenceResult createThenUpdateOnConflict(IndexRequest createRequest, UpdateRequest updateRequest) {
        return writeHelper.prepareConflict(createRequest, updateRequest, false).get();
    }

    /**
     * 异步执行同一物理目标的冲突补偿。
     */
    @Override
    public CompletableFuture<PersistenceResult> createThenUpdateOnConflictAsync(IndexRequest createRequest,
                                                                                UpdateRequest updateRequest) {
        return schedule(writeHelper.prepareConflict(createRequest, updateRequest, true));
    }

    /**
     * 仅为批量 CREATE 的 409 项生成 UPDATE 补偿。
     */
    @Override
    public BulkResult bulkCreateThenUpdateOnConflict(BulkRequest createRequest, ConflictUpdateResolver resolver) {
        return writeHelper.prepareBulkConflict(createRequest, resolver, false).get();
    }

    /**
     * 异步执行批量 CREATE 冲突补偿，失败保留两阶段结果。
     */
    @Override
    public CompletableFuture<BulkResult> bulkCreateThenUpdateOnConflictAsync(BulkRequest createRequest,
                                                                             ConflictUpdateResolver resolver) {
        return schedule(writeHelper.prepareBulkConflict(createRequest, resolver, true));
    }

    /**
     * 按受保护的查询范围修改文档，可返回服务端任务。
     */
    @Override
    public ByQueryTaskResult updateByQuery(UpdateByQueryRequest request) {
        return execute(request);
    }

    /**
     * 按受保护的查询范围删除文档，可返回服务端任务。
     */
    @Override
    public ByQueryTaskResult deleteByQuery(DeleteByQueryRequest request) {
        return execute(request);
    }

    /**
     * 在原数据源轮询服务端任务。
     */
    @Override
    public ByQueryTaskResult getTask(String datasource, String taskId) {
        return execute(TaskQueryRequest.builder().datasource(datasource).taskId(taskId).build());
    }

    /**
     * 建立绑定实体类型的持久化门面。
     */
    @Override
    public <T> TypedPersistence<T> forEntity(Class<T> entityClass) {
        if (entityClass == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        return new DefaultTypedPersistence<>(this, entityClass, null, null, new ArrayList<>(), null,
                IndexOptions.builder().build(), BulkOptions.builder().build());
    }

    private IndexRequest indexRequest(IndexRequest value, IndexOperationType type) {
        if (value == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        return IndexRequest.builder().document(value.getDocument()).index(value.getIndex()).id(value.getId())
                .options(ElasticsearchWriteApiHelper.indexOptions(value.getOptions(), type)).build();
    }

    private <Req extends PersistenceRequest, Res> Res execute(Req request) {
        return executorRegistry.<Req, Res>find(request).prepare(request, false).get();
    }

    private <Req extends PersistenceRequest, Res> CompletableFuture<Res> executeAsync(Req request) {
        return schedule(executorRegistry.<Req, Res>find(request).prepare(request, true));
    }

    private <Res> CompletableFuture<Res> schedule(Supplier<Res> prepared) {
        try {
            return CompletableFuture.supplyAsync(prepared, asyncExecutor);
        } catch (java.util.concurrent.RejectedExecutionException error) {
            writeHelper.reportAsyncRejection();
            return CompletableFuture.failedFuture(new io.github.surezzzzzz.sdk.elasticsearch.persistence.exception.PersistenceExecutionException(
                    io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode.EXECUTION_FAILED,
                    io.github.surezzzzzz.sdk.elasticsearch.persistence.constant.ErrorMessage.EXECUTION_FAILED));
        }
    }
}
