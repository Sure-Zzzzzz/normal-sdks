package io.github.surezzzzzz.sdk.elasticsearch.persistence.engine;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.IndexOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.BulkOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkItem;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.validator.EntityPersistenceValidator;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 绑定实体类型的持久化门面
 *
 * @author surezzzzzz
 */
@RequiredArgsConstructor
public class DefaultTypedPersistence<T> implements TypedPersistence<T> {

    private final PersistenceEngine delegate;
    private final Class<T> entityClass;
    private final Function<T, String> indexResolver;
    private final Function<T, String> idResolver;
    private final List<EntityPersistenceValidator<? super T>> validatorList;
    private final Function<T, String> routingResolver;
    private final IndexOptions defaultIndexOptions;
    private final BulkOptions defaultBulkOptions;

    /**
     * 写入或覆盖实体文档，显式选项按完整选择使用。
     */
    @Override
    public PersistenceResult index(T document) {
        return index(document, defaultIndexOptions);
    }

    /**
     * 写入或覆盖实体文档，显式选项按完整选择使用。
     */
    @Override
    public PersistenceResult index(T document, IndexOptions options) {
        validate(document);
        IndexRequest request = buildIndexRequest(document, options, IndexOperationType.INDEX);
        return delegate.index(request);
    }

    /**
     * 仅新建文档，已有 ID 返回冲突。
     */
    @Override
    public PersistenceResult create(T document) {
        return create(document, defaultIndexOptions);
    }

    /**
     * 仅新建文档，已有 ID 返回冲突。
     */
    @Override
    public PersistenceResult create(T document, IndexOptions options) {
        validate(document);
        return delegate.create(buildIndexRequest(document, options, IndexOperationType.CREATE));
    }

    /**
     * 以客户端异步方式写入文档，返回最终执行结果。
     */
    @Override
    public CompletableFuture<PersistenceResult> indexAsync(T document) {
        validate(document);
        return delegate.indexAsync(buildIndexRequest(document, defaultIndexOptions, IndexOperationType.INDEX));
    }

    /**
     * 以客户端异步方式写入文档，返回最终执行结果。
     */
    @Override
    public CompletableFuture<PersistenceResult> indexAsync(T document, IndexOptions options) {
        validate(document);
        return delegate.indexAsync(buildIndexRequest(document, options, IndexOperationType.INDEX));
    }

    /**
     * 以客户端异步方式仅新建文档。
     */
    @Override
    public CompletableFuture<PersistenceResult> createAsync(T document) {
        validate(document);
        return delegate.createAsync(buildIndexRequest(document, defaultIndexOptions, IndexOperationType.CREATE));
    }

    /**
     * 以客户端异步方式仅新建文档。
     */
    @Override
    public CompletableFuture<PersistenceResult> createAsync(T document, IndexOptions options) {
        validate(document);
        return delegate.createAsync(buildIndexRequest(document, options, IndexOperationType.CREATE));
    }

    /**
     * 先新建，仅在 409 冲突时于同一物理目标执行补偿更新。
     */
    @Override
    public PersistenceResult createThenUpdateOnConflict(T document,
                                                        Function<T, UpdateRequest> updateRequestResolver) {
        validate(document);
        IndexRequest createRequest = buildIndexRequest(document, defaultIndexOptions, IndexOperationType.CREATE);
        if (updateRequestResolver == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        UpdateRequest updateRequest = copyUpdateRequest(updateRequestResolver.apply(document), createRequest);
        return delegate.createThenUpdateOnConflict(createRequest, updateRequest);
    }

    /**
     * 异步执行同一物理目标的冲突补偿。
     */
    @Override
    public CompletableFuture<PersistenceResult> createThenUpdateOnConflictAsync(T document,
                                                                                Function<T, UpdateRequest> updateRequestResolver) {
        validate(document);
        IndexRequest createRequest = buildIndexRequest(document, defaultIndexOptions, IndexOperationType.CREATE);
        if (updateRequestResolver == null) throw PersistenceTargetResolver.invalid();
        UpdateRequest updateRequest = copyUpdateRequest(updateRequestResolver.apply(document), createRequest);
        return delegate.createThenUpdateOnConflictAsync(createRequest, updateRequest);
    }

    /**
     * 批量写入同类实体文档。
     */
    @Override
    public BulkResult bulkIndex(List<T> documentList) {
        return bulkIndex(documentList, defaultBulkOptions);
    }

    /**
     * 批量写入同类实体文档。
     */
    @Override
    public BulkResult bulkIndex(List<T> documentList, BulkOptions options) {
        return delegate.bulk(buildBulkRequest(documentList, options, BulkItemType.INDEX));
    }

    /**
     * 以客户端异步方式批量写入同类实体文档。
     */
    @Override
    public CompletableFuture<BulkResult> bulkIndexAsync(List<T> documentList) {
        return bulkIndexAsync(documentList, defaultBulkOptions);
    }

    /**
     * 以客户端异步方式批量写入同类实体文档。
     */
    @Override
    public CompletableFuture<BulkResult> bulkIndexAsync(List<T> documentList, BulkOptions options) {
        return delegate.bulkAsync(buildBulkRequest(documentList, options, BulkItemType.INDEX));
    }

    /**
     * 批量仅新建同类实体文档。
     */
    @Override
    public BulkResult bulkCreate(List<T> documentList) {
        return bulkCreate(documentList, defaultBulkOptions);
    }

    /**
     * 批量仅新建同类实体文档。
     */
    @Override
    public BulkResult bulkCreate(List<T> documentList, BulkOptions options) {
        return delegate.bulk(buildBulkRequest(documentList, options, BulkItemType.CREATE));
    }

    /**
     * 以客户端异步方式批量仅新建同类实体文档。
     */
    @Override
    public CompletableFuture<BulkResult> bulkCreateAsync(List<T> documentList) {
        return bulkCreateAsync(documentList, defaultBulkOptions);
    }

    /**
     * 以客户端异步方式批量仅新建同类实体文档。
     */
    @Override
    public CompletableFuture<BulkResult> bulkCreateAsync(List<T> documentList, BulkOptions options) {
        return delegate.bulkAsync(buildBulkRequest(documentList, options, BulkItemType.CREATE));
    }

    /**
     * 仅为批量 CREATE 的 409 项生成 UPDATE 补偿。
     */
    @Override
    public BulkResult bulkCreateThenUpdateOnConflict(List<T> documentList,
                                                     Function<T, BulkItem> updateItemResolver,
                                                     BulkOptions options) {
        if (updateItemResolver == null || documentList == null) throw PersistenceTargetResolver.invalid();
        List<T> documents = new ArrayList<>(documentList);
        BulkRequest createRequest = buildBulkRequest(documents, options, BulkItemType.CREATE);
        return delegate.bulkCreateThenUpdateOnConflict(createRequest, (createItem, conflictFailure) -> {
            T document = resolveDocument(documents, conflictFailure.getItemIndex());
            return copyUpdateItem(updateItemResolver.apply(document), createItem);
        });
    }

    /**
     * 异步执行批量 CREATE 冲突补偿，失败保留两阶段结果。
     */
    @Override
    public CompletableFuture<BulkResult> bulkCreateThenUpdateOnConflictAsync(List<T> documentList,
                                                                             Function<T, BulkItem> updateItemResolver,
                                                                             BulkOptions options) {
        if (updateItemResolver == null || documentList == null) throw PersistenceTargetResolver.invalid();
        List<T> documents = new ArrayList<>(documentList);
        BulkRequest createRequest = buildBulkRequest(documents, options, BulkItemType.CREATE);
        return delegate.bulkCreateThenUpdateOnConflictAsync(createRequest, (createItem, conflictFailure) -> {
            T document = resolveDocument(documents, conflictFailure.getItemIndex());
            return copyUpdateItem(updateItemResolver.apply(document), createItem);
        });
    }

    /**
     * 返回添加实体校验器的新门面，不修改原门面。
     */
    @Override
    public TypedPersistence<T> withValidator(EntityPersistenceValidator<? super T> validator) {
        if (validator == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        List<EntityPersistenceValidator<? super T>> copy = new ArrayList<>(validatorList);
        copy.add(validator);
        return new DefaultTypedPersistence<>(delegate, entityClass, indexResolver, idResolver, copy,
                routingResolver, defaultIndexOptions, defaultBulkOptions);
    }

    /**
     * 返回使用指定索引解析器的新门面。
     */
    @Override
    public TypedPersistence<T> withIndexResolver(Function<T, String> resolver) {
        if (resolver == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        return new DefaultTypedPersistence<>(delegate, entityClass, resolver, idResolver, validatorList,
                routingResolver, defaultIndexOptions, defaultBulkOptions);
    }

    /**
     * 返回使用指定 ID 解析器的新门面。
     */
    @Override
    public TypedPersistence<T> withIdResolver(Function<T, String> resolver) {
        if (resolver == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        return new DefaultTypedPersistence<>(delegate, entityClass, indexResolver, resolver, validatorList,
                routingResolver, defaultIndexOptions, defaultBulkOptions);
    }

    /**
     * 返回使用指定 routing 解析器的新门面。
     */
    @Override
    public TypedPersistence<T> withRoutingResolver(Function<T, String> resolver) {
        if (resolver == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        return new DefaultTypedPersistence<>(delegate, entityClass, indexResolver, idResolver, validatorList,
                resolver, defaultIndexOptions, defaultBulkOptions);
    }

    /**
     * 复制默认单写选项供无参重载使用。
     */
    @Override
    public TypedPersistence<T> withDefaultIndexOptions(IndexOptions options) {
        if (options == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        return new DefaultTypedPersistence<>(delegate, entityClass, indexResolver, idResolver, validatorList,
                routingResolver, mergeIndexOptions(options, options.getOperationType()), defaultBulkOptions);
    }

    /**
     * 复制默认批量选项供无参重载使用。
     */
    @Override
    public TypedPersistence<T> withDefaultBulkOptions(BulkOptions options) {
        if (options == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        return new DefaultTypedPersistence<>(delegate, entityClass, indexResolver, idResolver, validatorList,
                routingResolver, defaultIndexOptions, mergeBulkOptions(options));
    }

    // 复制 resolver 的返回对象，routing 由协议准备阶段继承已固定的 CREATE 值。
    private UpdateRequest copyUpdateRequest(UpdateRequest value, IndexRequest createRequest) {
        if (value == null) throw PersistenceTargetResolver.invalid();
        return UpdateRequest.builder().index(isBlank(value.getIndex()) ? createRequest.getIndex() : value.getIndex())
                .id(isBlank(value.getId()) ? createRequest.getId() : value.getId()).fieldMap(value.getFieldMap())
                .scriptSource(value.getScriptSource()).scriptParamMap(value.getScriptParamMap()).options(value.getOptions()).build();
    }

    private BulkItem copyUpdateItem(BulkItem value, BulkItem createItem) {
        if (value == null) throw PersistenceTargetResolver.invalid();
        return BulkItem.builder().type(value.getType()).index(isBlank(value.getIndex()) ? createItem.getIndex() : value.getIndex())
                .id(isBlank(value.getId()) ? createItem.getId() : value.getId())
                .routing(value.getRouting() == null ? createItem.getRouting() : value.getRouting())
                .fieldMap(value.getFieldMap()).scriptSource(value.getScriptSource()).scriptParamMap(value.getScriptParamMap())
                .docAsUpsert(value.getDocAsUpsert()).detectNoop(value.getDetectNoop()).upsertDoc(value.getUpsertDoc())
                .scriptedUpsert(value.getScriptedUpsert()).retryOnConflict(value.getRetryOnConflict())
                .document(value.getDocument()).pipeline(value.getPipeline()).build();
    }

    private T resolveDocument(List<T> documentList, int itemIndex) {
        return documentList.get(itemIndex);
    }

    private IndexRequest buildIndexRequest(T document, IndexOptions options, IndexOperationType operationType) {
        if (options == null) throw PersistenceTargetResolver.invalid();
        IndexOperationType selected = operationType == IndexOperationType.CREATE ? IndexOperationType.CREATE
                : options.getOperationType() == null ? IndexOperationType.INDEX : options.getOperationType();
        IndexOptions merged = mergeIndexOptions(options, selected);
        return IndexRequest.builder()
                .document(document)
                .index(resolveIndex(document))
                .id(resolveId(document))
                .options(applyRouting(merged, document))
                .build();
    }

    private BulkRequest buildBulkRequest(List<T> documentList, BulkOptions options, BulkItemType itemType) {
        List<BulkItem> itemList = new ArrayList<>();
        if (documentList != null) {
            for (T document : documentList) {
                validate(document);
                BulkItem.BulkItemBuilder builder = BulkItem.builder()
                        .type(itemType)
                        .document(document)
                        .index(resolveIndex(document))
                        .id(resolveId(document));
                String routing = resolveRouting(document);
                if (routing != null) {
                    builder.routing(routing);
                }
                itemList.add(builder.build());
            }
        }
        return BulkRequest.builder()
                .itemList(itemList)
                .options(mergeBulkOptions(options))
                .build();
    }

    private IndexOptions mergeIndexOptions(IndexOptions options, IndexOperationType operationType) {
        if (options == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        return io.github.surezzzzzz.sdk.elasticsearch.persistence.support.ElasticsearchWriteApiHelper.indexOptions(options, operationType);
    }

    private BulkOptions mergeBulkOptions(BulkOptions options) {
        if (options == null)
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        return BulkOptions.builder().batchSize(options.getBatchSize()).continueOnFailure(options.getContinueOnFailure())
                .pipeline(options.getPipeline()).refresh(options.getRefresh()).routing(options.getRouting())
                .timeoutMs(options.getTimeoutMs()).refreshPolicy(options.getRefreshPolicy()).build();
    }

    private IndexOptions applyRouting(IndexOptions options, T document) {
        if (options != null && options.getRouting() != null) {
            return options;
        }
        String routing = resolveRouting(document);
        if (routing == null) {
            return options;
        }
        return IndexOptions.builder()
                .operationType(options == null ? null : options.getOperationType())
                .pipeline(options == null ? null : options.getPipeline())
                .refresh(options == null ? null : options.getRefresh())
                .routing(routing)
                .timeoutMs(options == null ? null : options.getTimeoutMs())
                .refreshPolicy(options == null ? null : options.getRefreshPolicy())
                .build();
    }

    private String resolveIndex(T document) {
        return indexResolver == null ? null : indexResolver.apply(document);
    }

    private String resolveId(T document) {
        return idResolver == null ? null : idResolver.apply(document);
    }

    private String resolveRouting(T document) {
        return routingResolver == null ? null : routingResolver.apply(document);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private void validate(T document) {
        if (document == null || !entityClass.isInstance(document))
            throw io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver.invalid();
        for (EntityPersistenceValidator<? super T> validator : validatorList) {
            validator.validate(document);
        }
    }
}

