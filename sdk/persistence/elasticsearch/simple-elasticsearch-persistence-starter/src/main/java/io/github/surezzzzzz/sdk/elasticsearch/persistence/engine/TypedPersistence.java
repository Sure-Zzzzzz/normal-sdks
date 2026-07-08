package io.github.surezzzzzz.sdk.elasticsearch.persistence.engine;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.BulkOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.validator.EntityPersistenceValidator;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * Typed Persistence
 *
 * @author surezzzzzz
 */
public interface TypedPersistence<T> {

    PersistenceResult index(T document);

    PersistenceResult index(T document, IndexOptions options);

    PersistenceResult create(T document);

    default PersistenceResult create(T document, IndexOptions options) {
        return index(document, options);
    }

    CompletableFuture<PersistenceResult> indexAsync(T document);

    default CompletableFuture<PersistenceResult> indexAsync(T document, IndexOptions options) {
        return indexAsync(document);
    }

    default CompletableFuture<PersistenceResult> createAsync(T document) {
        return indexAsync(document);
    }

    default CompletableFuture<PersistenceResult> createAsync(T document, IndexOptions options) {
        return indexAsync(document, options);
    }

    BulkResult bulkIndex(List<T> documentList);

    default BulkResult bulkIndex(List<T> documentList, BulkOptions options) {
        return bulkIndex(documentList);
    }

    default CompletableFuture<BulkResult> bulkIndexAsync(List<T> documentList) {
        throw new UnsupportedOperationException("bulkIndexAsync 未实现");
    }

    default CompletableFuture<BulkResult> bulkIndexAsync(List<T> documentList, BulkOptions options) {
        return bulkIndexAsync(documentList);
    }

    default BulkResult bulkCreate(List<T> documentList) {
        return bulkIndex(documentList);
    }

    default BulkResult bulkCreate(List<T> documentList, BulkOptions options) {
        return bulkIndex(documentList, options);
    }

    default CompletableFuture<BulkResult> bulkCreateAsync(List<T> documentList) {
        return bulkIndexAsync(documentList);
    }

    default CompletableFuture<BulkResult> bulkCreateAsync(List<T> documentList, BulkOptions options) {
        return bulkIndexAsync(documentList, options);
    }

    TypedPersistence<T> withValidator(EntityPersistenceValidator<? super T> validator);

    TypedPersistence<T> withIndexResolver(Function<T, String> indexResolver);

    TypedPersistence<T> withIdResolver(Function<T, String> idResolver);

    default TypedPersistence<T> withRoutingResolver(Function<T, String> routingResolver) {
        return this;
    }

    default TypedPersistence<T> withDefaultIndexOptions(IndexOptions options) {
        return this;
    }

    default TypedPersistence<T> withDefaultBulkOptions(BulkOptions options) {
        return this;
    }
}
