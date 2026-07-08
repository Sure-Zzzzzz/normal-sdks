package io.github.surezzzzzz.sdk.elasticsearch.persistence.engine;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.IndexOperationType;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.BulkOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkItem;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.IndexRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.validator.EntityPersistenceValidator;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Default Typed Persistence
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
     * 1.0.1 兼容构造函数，新字段默认 null。
     */
    public DefaultTypedPersistence(PersistenceEngine delegate, Class<T> entityClass,
                                   Function<T, String> indexResolver, Function<T, String> idResolver,
                                   List<EntityPersistenceValidator<? super T>> validatorList) {
        this(delegate, entityClass, indexResolver, idResolver, validatorList, null, null, null);
    }

    @Override
    public PersistenceResult index(T document) {
        return index(document, null);
    }

    @Override
    public PersistenceResult index(T document, IndexOptions options) {
        validate(document);
        IndexRequest request = buildIndexRequest(document, options, IndexOperationType.INDEX);
        return delegate.index(request);
    }

    @Override
    public PersistenceResult create(T document) {
        return create(document, null);
    }

    @Override
    public PersistenceResult create(T document, IndexOptions options) {
        validate(document);
        return delegate.create(buildIndexRequest(document, options, IndexOperationType.CREATE));
    }

    @Override
    public CompletableFuture<PersistenceResult> indexAsync(T document) {
        validate(document);
        return delegate.indexAsync(buildIndexRequest(document, null, IndexOperationType.INDEX));
    }

    @Override
    public CompletableFuture<PersistenceResult> indexAsync(T document, IndexOptions options) {
        validate(document);
        return delegate.indexAsync(buildIndexRequest(document, options, IndexOperationType.INDEX));
    }

    @Override
    public CompletableFuture<PersistenceResult> createAsync(T document) {
        validate(document);
        return delegate.createAsync(buildIndexRequest(document, null, IndexOperationType.CREATE));
    }

    @Override
    public CompletableFuture<PersistenceResult> createAsync(T document, IndexOptions options) {
        validate(document);
        return delegate.createAsync(buildIndexRequest(document, options, IndexOperationType.CREATE));
    }

    @Override
    public BulkResult bulkIndex(List<T> documentList) {
        return bulkIndex(documentList, null);
    }

    @Override
    public BulkResult bulkIndex(List<T> documentList, BulkOptions options) {
        return delegate.bulk(buildBulkRequest(documentList, options, BulkItemType.INDEX));
    }

    @Override
    public CompletableFuture<BulkResult> bulkIndexAsync(List<T> documentList) {
        return bulkIndexAsync(documentList, null);
    }

    @Override
    public CompletableFuture<BulkResult> bulkIndexAsync(List<T> documentList, BulkOptions options) {
        return delegate.bulkAsync(buildBulkRequest(documentList, options, BulkItemType.INDEX));
    }

    @Override
    public BulkResult bulkCreate(List<T> documentList) {
        return bulkCreate(documentList, null);
    }

    @Override
    public BulkResult bulkCreate(List<T> documentList, BulkOptions options) {
        return delegate.bulk(buildBulkRequest(documentList, options, BulkItemType.CREATE));
    }

    @Override
    public CompletableFuture<BulkResult> bulkCreateAsync(List<T> documentList) {
        return bulkCreateAsync(documentList, null);
    }

    @Override
    public CompletableFuture<BulkResult> bulkCreateAsync(List<T> documentList, BulkOptions options) {
        return delegate.bulkAsync(buildBulkRequest(documentList, options, BulkItemType.CREATE));
    }

    @Override
    public TypedPersistence<T> withValidator(EntityPersistenceValidator<? super T> validator) {
        List<EntityPersistenceValidator<? super T>> copy = new ArrayList<>(validatorList);
        copy.add(validator);
        return new DefaultTypedPersistence<>(delegate, entityClass, indexResolver, idResolver, copy,
                routingResolver, defaultIndexOptions, defaultBulkOptions);
    }

    @Override
    public TypedPersistence<T> withIndexResolver(Function<T, String> resolver) {
        return new DefaultTypedPersistence<>(delegate, entityClass, resolver, idResolver, validatorList,
                routingResolver, defaultIndexOptions, defaultBulkOptions);
    }

    @Override
    public TypedPersistence<T> withIdResolver(Function<T, String> resolver) {
        return new DefaultTypedPersistence<>(delegate, entityClass, indexResolver, resolver, validatorList,
                routingResolver, defaultIndexOptions, defaultBulkOptions);
    }

    @Override
    public TypedPersistence<T> withRoutingResolver(Function<T, String> resolver) {
        return new DefaultTypedPersistence<>(delegate, entityClass, indexResolver, idResolver, validatorList,
                resolver, defaultIndexOptions, defaultBulkOptions);
    }

    @Override
    public TypedPersistence<T> withDefaultIndexOptions(IndexOptions options) {
        return new DefaultTypedPersistence<>(delegate, entityClass, indexResolver, idResolver, validatorList,
                routingResolver, options, defaultBulkOptions);
    }

    @Override
    public TypedPersistence<T> withDefaultBulkOptions(BulkOptions options) {
        return new DefaultTypedPersistence<>(delegate, entityClass, indexResolver, idResolver, validatorList,
                routingResolver, defaultIndexOptions, options);
    }

    private IndexRequest buildIndexRequest(T document, IndexOptions options, IndexOperationType operationType) {
        IndexOptions merged = mergeIndexOptions(options, operationType);
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
        IndexOptions.IndexOptionsBuilder builder = IndexOptions.builder();
        IndexOptions base = defaultIndexOptions;
        if (base != null) {
            builder.pipeline(base.getPipeline())
                    .refresh(base.getRefresh())
                    .routing(base.getRouting())
                    .timeoutMs(base.getTimeoutMs())
                    .refreshPolicy(base.getRefreshPolicy());
        }
        if (options != null) {
            if (options.getPipeline() != null) {
                builder.pipeline(options.getPipeline());
            }
            if (options.getRefresh() != null) {
                builder.refresh(options.getRefresh());
            }
            if (options.getRouting() != null) {
                builder.routing(options.getRouting());
            }
            if (options.getTimeoutMs() != null) {
                builder.timeoutMs(options.getTimeoutMs());
            }
            if (options.getRefreshPolicy() != null) {
                builder.refreshPolicy(options.getRefreshPolicy());
            }
        }
        builder.operationType(operationType);
        return builder.build();
    }

    private BulkOptions mergeBulkOptions(BulkOptions options) {
        BulkOptions.BulkOptionsBuilder builder = BulkOptions.builder();
        BulkOptions base = defaultBulkOptions;
        if (base != null) {
            builder.batchSize(base.getBatchSize())
                    .continueOnFailure(base.getContinueOnFailure())
                    .pipeline(base.getPipeline())
                    .refresh(base.getRefresh())
                    .routing(base.getRouting())
                    .timeoutMs(base.getTimeoutMs())
                    .refreshPolicy(base.getRefreshPolicy());
        }
        if (options != null) {
            if (options.getBatchSize() != null) {
                builder.batchSize(options.getBatchSize());
            }
            if (options.getContinueOnFailure() != null) {
                builder.continueOnFailure(options.getContinueOnFailure());
            }
            if (options.getPipeline() != null) {
                builder.pipeline(options.getPipeline());
            }
            if (options.getRefresh() != null) {
                builder.refresh(options.getRefresh());
            }
            if (options.getRouting() != null) {
                builder.routing(options.getRouting());
            }
            if (options.getTimeoutMs() != null) {
                builder.timeoutMs(options.getTimeoutMs());
            }
            if (options.getRefreshPolicy() != null) {
                builder.refreshPolicy(options.getRefreshPolicy());
            }
        }
        return builder.build();
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

    private void validate(T document) {
        for (EntityPersistenceValidator<? super T> validator : validatorList) {
            validator.validate(document);
        }
    }
}
