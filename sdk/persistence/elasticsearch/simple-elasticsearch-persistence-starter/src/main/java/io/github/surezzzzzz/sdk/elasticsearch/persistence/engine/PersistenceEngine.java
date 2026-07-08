package io.github.surezzzzzz.sdk.elasticsearch.persistence.engine;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.BulkOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.*;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.BulkResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.ByQueryTaskResult;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result.PersistenceResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Persistence Engine
 *
 * <p>写侧统一入口，封装 index/update/delete/bulk/by-query，
 * 自动继承 route 的多数据源路由与写索引渲染。
 *
 * @author surezzzzzz
 */
public interface PersistenceEngine {

    // ==== 单条写入（同步） ====

    /** 按实体 @Document 索引文档（upsert 语义，opType=INDEX）。 */
    <T> PersistenceResult index(T document);

    /** 按实体索引文档，可指定操作选项（如 CREATE 仅新建）。 */
    <T> PersistenceResult index(T document, IndexOptions options);

    /** 按完整 IndexRequest 索引文档。 */
    PersistenceResult index(IndexRequest request);

    /** 按实体新建文档，已存在则失败（opType=CREATE）。 */
    <T> PersistenceResult create(T document);

    /** 按实体新建文档，可指定操作选项。 */
    <T> PersistenceResult create(T document, IndexOptions options);

    /** 按完整 IndexRequest 新建文档。 */
    PersistenceResult create(IndexRequest request);

    /** 局部更新文档（doc 字段或 script）。 */
    PersistenceResult update(UpdateRequest request);

    /** 按 ID 删除文档。 */
    PersistenceResult delete(DeleteRequest request);

    // ==== 单条写入（客户端异步，返回 CompletableFuture） ====

    /** 异步索引文档（upsert 语义）。 */
    <T> CompletableFuture<PersistenceResult> indexAsync(T document);

    /** 异步索引文档，可指定操作选项。 */
    <T> CompletableFuture<PersistenceResult> indexAsync(T document, IndexOptions options);

    /** 异步按完整 IndexRequest 索引文档。 */
    CompletableFuture<PersistenceResult> indexAsync(IndexRequest request);

    /** 异步新建文档（CREATE 语义）。 */
    <T> CompletableFuture<PersistenceResult> createAsync(T document);

    /** 异步新建文档，可指定操作选项。 */
    <T> CompletableFuture<PersistenceResult> createAsync(T document, IndexOptions options);

    /** 异步按完整 IndexRequest 新建文档。 */
    CompletableFuture<PersistenceResult> createAsync(IndexRequest request);

    /** 异步局部更新文档。 */
    CompletableFuture<PersistenceResult> updateAsync(UpdateRequest request);

    /** 异步按 ID 删除文档。 */
    CompletableFuture<PersistenceResult> deleteAsync(DeleteRequest request);

    // ==== 批量操作 ====

    /** 批量操作，BulkRequest 内可混合 index/update/delete。 */
    BulkResult bulk(BulkRequest request);

    /** 异步批量操作。 */
    CompletableFuture<BulkResult> bulkAsync(BulkRequest request);

    /** 便捷批量索引同类文档，可指定 bulk 选项。 */
    <T> BulkResult bulkIndex(List<T> documentList, BulkOptions options);

    /** 异步便捷批量索引同类文档。 */
    <T> CompletableFuture<BulkResult> bulkIndexAsync(List<T> documentList, BulkOptions options);

    // ==== 按查询批量操作 ====

    /** 按查询更新；wait-for-completion=false 时返回 taskId 可轮询。 */
    ByQueryTaskResult updateByQuery(UpdateByQueryRequest request);

    /** 按查询删除；wait-for-completion=false 时返回 taskId 可轮询。 */
    ByQueryTaskResult deleteByQuery(DeleteByQueryRequest request);

    /** 轮询服务端异步任务进度。 */
    ByQueryTaskResult getTask(String datasource, String taskId);

    // ==== Typed Facade ====

    /** 返回绑定到指定实体类型的强类型门面，省去重复传 Class。 */
    <T> TypedPersistence<T> forEntity(Class<T> entityClass);
}
