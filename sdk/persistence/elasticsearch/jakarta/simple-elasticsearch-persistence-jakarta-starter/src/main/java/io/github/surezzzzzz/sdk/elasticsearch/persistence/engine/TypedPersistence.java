package io.github.surezzzzzz.sdk.elasticsearch.persistence.engine;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.BulkOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.BulkItem;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.UpdateRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.validator.EntityPersistenceValidator;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

/**
 * 强类型持久化契约；CREATE 与异步选项不能由默认方法降级或忽略
 *
 * @author surezzzzzz
 */
public interface TypedPersistence<T> {

    /**
     * 写入或覆盖实体文档，显式选项按完整选择使用。
     */
    PersistenceResult index(T document);

    /**
     * 写入或覆盖实体文档，显式选项按完整选择使用。
     */
    PersistenceResult index(T document, IndexOptions options);

    /**
     * 仅新建文档，已有 ID 返回冲突。
     */
    PersistenceResult create(T document);

    /**
     * 仅新建文档，已有 ID 返回冲突。
     */
    PersistenceResult create(T document, IndexOptions options);

    /**
     * 以客户端异步方式写入文档，返回最终执行结果。
     */
    CompletableFuture<PersistenceResult> indexAsync(T document);

    /**
     * 以客户端异步方式写入文档，返回最终执行结果。
     */
    CompletableFuture<PersistenceResult> indexAsync(T document, IndexOptions options);

    /**
     * 以客户端异步方式仅新建文档。
     */
    CompletableFuture<PersistenceResult> createAsync(T document);

    /**
     * 以客户端异步方式仅新建文档。
     */
    CompletableFuture<PersistenceResult> createAsync(T document, IndexOptions options);

    /**
     * 先新建，仅在 409 冲突时于同一物理目标执行补偿更新。
     */
    PersistenceResult createThenUpdateOnConflict(T document,
                                                 Function<T, UpdateRequest> updateRequestResolver);

    /**
     * 异步执行同一物理目标的冲突补偿。
     */
    CompletableFuture<PersistenceResult> createThenUpdateOnConflictAsync(T document,
                                                                         Function<T, UpdateRequest> updateRequestResolver);

    /**
     * 批量写入同类实体文档。
     */
    BulkResult bulkIndex(List<T> documentList);

    /**
     * 批量写入同类实体文档。
     */
    BulkResult bulkIndex(List<T> documentList, BulkOptions options);

    /**
     * 以客户端异步方式批量写入同类实体文档。
     */
    CompletableFuture<BulkResult> bulkIndexAsync(List<T> documentList);

    /**
     * 以客户端异步方式批量写入同类实体文档。
     */
    CompletableFuture<BulkResult> bulkIndexAsync(List<T> documentList, BulkOptions options);

    /**
     * 批量仅新建同类实体文档。
     */
    BulkResult bulkCreate(List<T> documentList);

    /**
     * 批量仅新建同类实体文档。
     */
    BulkResult bulkCreate(List<T> documentList, BulkOptions options);

    /**
     * 以客户端异步方式批量仅新建同类实体文档。
     */
    CompletableFuture<BulkResult> bulkCreateAsync(List<T> documentList);

    /**
     * 以客户端异步方式批量仅新建同类实体文档。
     */
    CompletableFuture<BulkResult> bulkCreateAsync(List<T> documentList, BulkOptions options);

    /**
     * 仅为批量 CREATE 的 409 项生成 UPDATE 补偿。
     */
    BulkResult bulkCreateThenUpdateOnConflict(List<T> documentList,
                                              Function<T, BulkItem> updateItemResolver,
                                              BulkOptions options);

    /**
     * 异步执行批量 CREATE 冲突补偿，失败保留两阶段结果。
     */
    CompletableFuture<BulkResult> bulkCreateThenUpdateOnConflictAsync(List<T> documentList,
                                                                      Function<T, BulkItem> updateItemResolver,
                                                                      BulkOptions options);

    /**
     * 返回添加实体校验器的新门面，不修改原门面。
     */
    TypedPersistence<T> withValidator(EntityPersistenceValidator<? super T> validator);

    /**
     * 返回使用指定索引解析器的新门面。
     */
    TypedPersistence<T> withIndexResolver(Function<T, String> indexResolver);

    /**
     * 返回使用指定 ID 解析器的新门面。
     */
    TypedPersistence<T> withIdResolver(Function<T, String> idResolver);

    /**
     * 返回使用指定 routing 解析器的新门面。
     */
    TypedPersistence<T> withRoutingResolver(Function<T, String> routingResolver);

    /**
     * 复制默认单写选项供无参重载使用。
     */
    TypedPersistence<T> withDefaultIndexOptions(IndexOptions options);

    /**
     * 复制默认批量选项供无参重载使用。
     */
    TypedPersistence<T> withDefaultBulkOptions(BulkOptions options);
}

