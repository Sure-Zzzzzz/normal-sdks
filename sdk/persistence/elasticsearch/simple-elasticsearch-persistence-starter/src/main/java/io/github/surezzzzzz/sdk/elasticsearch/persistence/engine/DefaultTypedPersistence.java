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

    @Override
    public PersistenceResult index(T document) {
        return index(document, null);
    }

    @Override
    public PersistenceResult index(T document, IndexOptions options) {
        validate(document);
        IndexRequest request = buildIndexRequest(document, options);
        return delegate.index(request);
    }

    @Override
    public PersistenceResult create(T document) {
        IndexOptions options = IndexOptions.builder().operationType(IndexOperationType.CREATE).build();
        validate(document);
        return delegate.create(buildIndexRequest(document, options));
    }

    @Override
    public CompletableFuture<PersistenceResult> indexAsync(T document) {
        validate(document);
        return delegate.indexAsync(buildIndexRequest(document, null));
    }

    @Override
    public BulkResult bulkIndex(List<T> documentList, BulkOptions options) {
        List<BulkItem> itemList = new ArrayList<>();
        if (documentList != null) {
            for (T document : documentList) {
                validate(document);
                itemList.add(BulkItem.builder()
                        .type(BulkItemType.INDEX)
                        .document(document)
                        .index(resolveIndex(document))
                        .id(resolveId(document))
                        .build());
            }
        }
        return delegate.bulk(BulkRequest.builder().itemList(itemList).options(options).build());
    }

    @Override
    public TypedPersistence<T> withValidator(EntityPersistenceValidator<? super T> validator) {
        List<EntityPersistenceValidator<? super T>> copy = new ArrayList<>(validatorList);
        copy.add(validator);
        return new DefaultTypedPersistence<>(delegate, entityClass, indexResolver, idResolver, copy);
    }

    @Override
    public TypedPersistence<T> withIndexResolver(Function<T, String> resolver) {
        return new DefaultTypedPersistence<>(delegate, entityClass, resolver, idResolver, validatorList);
    }

    @Override
    public TypedPersistence<T> withIdResolver(Function<T, String> resolver) {
        return new DefaultTypedPersistence<>(delegate, entityClass, indexResolver, resolver, validatorList);
    }

    private IndexRequest buildIndexRequest(T document, IndexOptions options) {
        return IndexRequest.builder()
                .document(document)
                .index(resolveIndex(document))
                .id(resolveId(document))
                .options(options)
                .build();
    }

    private String resolveIndex(T document) {
        return indexResolver == null ? null : indexResolver.apply(document);
    }

    private String resolveId(T document) {
        return idResolver == null ? null : idResolver.apply(document);
    }

    private void validate(T document) {
        for (EntityPersistenceValidator<? super T> validator : validatorList) {
            validator.validate(document);
        }
    }
}
