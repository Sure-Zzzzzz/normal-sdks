package io.github.surezzzzzz.sdk.elasticsearch.search.engine;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;

/**
 * 查询业务门面，与 Elasticsearch 客户端实现解耦。
 */
public interface SearchEngine {
    /**
     * 执行结构化查询，返回受字段保护的分页结果。
     */
    QueryResponse query(QueryRequest request);

    /**
     * 仅统计精确总数，不加载命中文档。
     */
    QueryResponse count(QueryRequest request);

    /**
     * 执行结构化聚合，返回统一指标或桶结果。
     */
    AggResponse aggregate(AggRequest request);

    /**
     * 认证游标后在原数据源关闭查询快照。
     */
    void closePit(String cursorToken);

    /**
     * 认证游标后在原数据源释放遍历上下文。
     */
    void clearScroll(String cursorToken);
}
