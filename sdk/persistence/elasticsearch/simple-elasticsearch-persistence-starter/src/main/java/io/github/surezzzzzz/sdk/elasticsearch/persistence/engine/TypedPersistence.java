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

    CompletableFuture<PersistenceResult> indexAsync(T document);

    BulkResult bulkIndex(List<T> documentList, BulkOptions options);

    TypedPersistence<T> withValidator(EntityPersistenceValidator<? super T> validator);

    TypedPersistence<T> withIndexResolver(Function<T, String> indexResolver);

    TypedPersistence<T> withIdResolver(Function<T, String> idResolver);
}
