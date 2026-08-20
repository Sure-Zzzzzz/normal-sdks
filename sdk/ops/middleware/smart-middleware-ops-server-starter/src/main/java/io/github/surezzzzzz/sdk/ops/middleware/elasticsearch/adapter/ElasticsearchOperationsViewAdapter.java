package io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.adapter;

import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.catalog.ElasticsearchIndexListResponse;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.document.ElasticsearchDocumentQueryRequest;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.document.ElasticsearchDocumentQueryResponse;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.field.ElasticsearchFieldCapabilitiesRequest;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.field.ElasticsearchFieldCapabilitiesResponse;
import io.github.surezzzzzz.sdk.ops.middleware.elasticsearch.summary.ElasticsearchSummaryResponse;

/**
 * Elasticsearch 受控运维视图适配口。
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
     * 获取精确索引的受限字段能力目录。
     *
     * @param request 字段能力目录请求
     * @return 安全字段能力目录
     */
    ElasticsearchFieldCapabilitiesResponse getFieldCapabilities(ElasticsearchFieldCapabilitiesRequest request);

    /**
     * 使用 Route 所有的高阶客户端执行受限 JSON DSL 查询。
     *
     * @param request 查询请求
     * @return 当前响应命中
     */
    ElasticsearchDocumentQueryResponse queryDocuments(ElasticsearchDocumentQueryRequest request);
}
