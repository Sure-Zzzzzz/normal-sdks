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
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.TaskQueryRequest;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * Default Persistence Engine
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class DefaultPersistenceEngine implements PersistenceEngine {

    private final PersistenceExecutorRegistry executorRegistry;
    private final Executor asyncExecutor;

    @Override
    public <T> PersistenceResult index(T document) {
        return index(document, null);
    }

    @Override
    public <T> PersistenceResult index(T document, IndexOptions options) {
        return index(IndexRequest.builder().document(document).options(options).build());
    }

    @Override
    public PersistenceResult index(IndexRequest request) {
        applyIndexOperationType(request, IndexOperationType.INDEX);
        return execute(request);
    }

    @Override
    public <T> PersistenceResult create(T document) {
        return create(document, null);
    }

    @Override
    public <T> PersistenceResult create(T document, IndexOptions options) {
        return create(IndexRequest.builder().document(document).options(options).build());
    }

    @Override
    public PersistenceResult create(IndexRequest request) {
        applyIndexOperationType(request, IndexOperationType.CREATE);
        return execute(request);
    }

    @Override
    public PersistenceResult update(UpdateRequest request) {
        return execute(request);
    }

    @Override
    public PersistenceResult delete(DeleteRequest request) {
        return execute(request);
    }

    @Override
    public <T> CompletableFuture<PersistenceResult> indexAsync(T document) {
        return indexAsync(document, null);
    }

    @Override
    public <T> CompletableFuture<PersistenceResult> indexAsync(T document, IndexOptions options) {
        return indexAsync(IndexRequest.builder().document(document).options(options).build());
    }

    @Override
    public CompletableFuture<PersistenceResult> indexAsync(IndexRequest request) {
        return CompletableFuture.supplyAsync(() -> index(request), asyncExecutor);
    }

    @Override
    public <T> CompletableFuture<PersistenceResult> createAsync(T document) {
        return createAsync(document, null);
    }

    @Override
    public <T> CompletableFuture<PersistenceResult> createAsync(T document, IndexOptions options) {
        return createAsync(IndexRequest.builder().document(document).options(options).build());
    }

    @Override
    public CompletableFuture<PersistenceResult> createAsync(IndexRequest request) {
        return CompletableFuture.supplyAsync(() -> create(request), asyncExecutor);
    }

    @Override
    public CompletableFuture<PersistenceResult> updateAsync(UpdateRequest request) {
        return CompletableFuture.supplyAsync(() -> update(request), asyncExecutor);
    }

    @Override
    public CompletableFuture<PersistenceResult> deleteAsync(DeleteRequest request) {
        return CompletableFuture.supplyAsync(() -> delete(request), asyncExecutor);
    }

    @Override
    public BulkResult bulk(BulkRequest request) {
        return execute(request);
    }

    @Override
    public CompletableFuture<BulkResult> bulkAsync(BulkRequest request) {
        return CompletableFuture.supplyAsync(() -> bulk(request), asyncExecutor);
    }

    @Override
    public <T> BulkResult bulkIndex(List<T> documentList, BulkOptions options) {
        List<BulkItem> itemList = new ArrayList<>();
        if (documentList != null) {
            for (T document : documentList) {
                itemList.add(BulkItem.builder().type(BulkItemType.INDEX)
                        .document(document).build());
            }
        }
        return bulk(BulkRequest.builder().itemList(itemList).options(options).build());
    }

    @Override
    public <T> CompletableFuture<BulkResult> bulkIndexAsync(List<T> documentList, BulkOptions options) {
        return CompletableFuture.supplyAsync(() -> bulkIndex(documentList, options), asyncExecutor);
    }

    @Override
    public ByQueryTaskResult updateByQuery(UpdateByQueryRequest request) {
        return execute(request);
    }

    @Override
    public ByQueryTaskResult deleteByQuery(DeleteByQueryRequest request) {
        return execute(request);
    }

    @Override
    public ByQueryTaskResult getTask(String datasource, String taskId) {
        return execute(TaskQueryRequest.builder().datasource(datasource).taskId(taskId).build());
    }

    @Override
    public <T> TypedPersistence<T> forEntity(Class<T> entityClass) {
        return new DefaultTypedPersistence<>(this, entityClass, null, null, new ArrayList<>());
    }

    private void applyIndexOperationType(IndexRequest request, IndexOperationType operationType) {
        IndexOptions options = request.getOptions();
        if (options == null || options.getOperationType() == null) {
            request.setOptions(IndexOptions.builder().operationType(operationType).build());
        }
    }

    @SuppressWarnings("unchecked")
    private <Req extends PersistenceRequest, Res> Res execute(Req request) {
        return executorRegistry.<Req, Res>find(request).execute(request);
    }
}
