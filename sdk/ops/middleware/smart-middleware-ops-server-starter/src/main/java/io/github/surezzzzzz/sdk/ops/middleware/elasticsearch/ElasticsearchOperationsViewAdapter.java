package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch;

/**
 * Elasticsearch Route 只读运维视图适配口。
 *
 * @author surezzzzzz
 */
public interface ElasticsearchOperationsViewAdapter {

    /**
     * 获取指定数据源的集群版本能力摘要。
     *
     * @param datasourceKey 数据源标识
     * @return 安全摘要
     */
    ElasticsearchSummaryResponse getSummary(String datasourceKey);

    /**
     * 获取指定数据源的可查询索引目录。
     *
     * @param datasourceKey 数据源标识
     * @return 受限索引目录
     */
    ElasticsearchIndexListResponse listIndices(String datasourceKey);

    /**
     * 使用 Route 所有的高阶客户端执行受限 JSON DSL 查询。
     *
     * @param request 查询请求
     * @return 当前响应命中
     */
    ElasticsearchDocumentQueryResponse queryDocuments(ElasticsearchDocumentQueryRequest request);
}
