package io.github.surezzzzzz.sdk.elasticsearch.search.support;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;

/**
 * Elasticsearch 版本兼容性工具类（门面）
 * 处理 ES 6.x / 7.x+ API 差异，反射操作委托给 {@link XContentReflectionHelper}
 *
 * @author surezzzzzz
 */
@Slf4j
public class ElasticsearchCompatibilityHelper {

    /**
     * ES 6.x 聚合响应异常
     * 包含原始 JSON，供调用方手动解析
     */
    public static class Es6xAggregationResponseException extends IOException {
        private final String responseJson;

        public Es6xAggregationResponseException(String responseJson) {
            super("ES 6.x aggregation response cannot be parsed by ES 7.x client, raw JSON included");
            this.responseJson = responseJson;
        }

        public String getResponseJson() {
            return responseJson;
        }
    }

    /**
     * 提取 SearchHits 中的 totalHits 数值，兼容 ES 6.x（返回 long）和 ES 7.x+（返回 TotalHits 对象）
     *
     * @param hits SearchHits
     * @return total hits 数量
     */
    public static long extractTotalHits(org.elasticsearch.search.SearchHits hits) {
        Object totalHitsObj = hits.getTotalHits();
        if (totalHitsObj instanceof Long) {
            // ES 6.x: getTotalHits() 返回 long（自动装箱）
            return (Long) totalHitsObj;
        }
        // ES 7.x+: getTotalHits() 返回 TotalHits 对象，通过反射取 value 字段
        try {
            Method valueField = totalHitsObj.getClass().getMethod("value");
            return (Long) valueField.invoke(totalHitsObj);
        } catch (Exception e) {
            log.warn("Failed to extract totalHits via reflection, falling back to direct field access: {}", e.getMessage());
            // 最终兜底：直接访问 value 字段
            try {
                java.lang.reflect.Field field = totalHitsObj.getClass().getField("value");
                return (Long) field.get(totalHitsObj);
            } catch (Exception ex) {
                log.warn("Failed to extract totalHits via field access: {}", ex.getMessage());
                return 0L;
            }
        }
    }

    /**
     * 执行查询（版本兼容）
     * ES 6.x 使用低级 API 绕过 ignore_throttled 等参数兼容性问题
     *
     * @param client        RestHighLevelClient
     * @param datasourceKey 数据源标识
     * @param searchRequest 搜索请求
     * @param registry      数据源注册中心
     * @return SearchResponse
     * @throws IOException IO异常
     */
    public static SearchResponse executeSearch(RestHighLevelClient client, String datasourceKey,
                                               SearchRequest searchRequest,
                                               SimpleElasticsearchRouteRegistry registry) throws IOException {
        ClusterInfo clusterInfo = registry.getClusterInfo(datasourceKey);
        boolean isEs6x = false;
        boolean versionUnknown = false;

        if (clusterInfo != null && clusterInfo.getEffectiveVersion() != null) {
            int majorVersion = clusterInfo.getEffectiveVersion().getMajor();
            isEs6x = (majorVersion == 6);
            log.debug("Detected ES major version: {} for datasource [{}]", majorVersion, datasourceKey);
        } else {
            versionUnknown = true;
            log.debug("Datasource [{}] version not yet detected, will try high-level API first", datasourceKey);
        }

        if (isEs6x) {
            log.debug("Using low-level API for ES 6.x compatibility");
            return executeSearchViaLowLevelApi(client, searchRequest);
        } else {
            try {
                return client.search(searchRequest, RequestOptions.DEFAULT);
            } catch (org.elasticsearch.ElasticsearchStatusException e) {
                if (versionUnknown && e.getMessage() != null &&
                        e.getMessage().contains(SimpleElasticsearchSearchConstant.ES_ERROR_UNRECOGNIZED_PARAMETER)) {
                    log.warn("High-level API failed with ES 6.x compatibility issue, falling back to low-level API for datasource [{}]", datasourceKey);
                    return executeSearchViaLowLevelApi(client, searchRequest);
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * 通用 XContent 响应解析方法（支持多种响应类型）
     *
     * @param response      HTTP 响应
     * @param responseClass 目标响应类型
     * @param <T>           响应类型泛型
     * @return 解析后的响应对象
     * @throws IOException IO异常
     */
    public static <T> T parseResponse(org.elasticsearch.client.Response response,
                                      Class<T> responseClass) throws IOException {
        String xContentPackage = XContentReflectionHelper.detectXContentPackage();
        return XContentReflectionHelper.parseResponse(response.getEntity().getContent(), responseClass, xContentPackage);
    }

    /**
     * 执行计数查询（版本兼容）
     * <p>
     * ES 6.x / 7.x+ 均使用低级 API 透传，ES _count API 无 6.x/7.x 兼容差异。
     *
     * @param client                   RestHighLevelClient
     * @param datasourceKey            数据源标识
     * @param indices                  目标索引
     * @param queryJson                query DSL JSON 字符串
     * @param ignoreUnavailableIndices 是否忽略不可用索引
     * @return 匹配文档数
     * @throws IOException IO异常
     * @since 1.6.6
     */
    public static long executeCount(RestHighLevelClient client, String datasourceKey,
                                    String[] indices, String queryJson,
                                    boolean ignoreUnavailableIndices) throws IOException {
        String indexPath = String.join(",", indices);
        String endpoint = SimpleElasticsearchRouteConstant.ENDPOINT_ROOT + indexPath + SimpleElasticsearchSearchConstant.ES_API_COUNT;

        org.elasticsearch.client.Request request = new org.elasticsearch.client.Request(
                SimpleElasticsearchRouteConstant.HTTP_METHOD_POST, endpoint);
        if (ignoreUnavailableIndices) {
            request.addParameter(SimpleElasticsearchSearchConstant.ES_PARAM_IGNORE_UNAVAILABLE,
                    SimpleElasticsearchSearchConstant.ES_PARAM_VALUE_TRUE);
        }
        request.setJsonEntity(queryJson != null ? queryJson : SimpleElasticsearchSearchConstant.ES_COUNT_EMPTY_QUERY);

        org.elasticsearch.client.Response response = client.getLowLevelClient().performRequest(request);
        return parseCountResponse(response);
    }

    /**
     * 解析 _count 响应
     */
    private static long parseCountResponse(org.elasticsearch.client.Response response) throws IOException {
        String xContentPackage = XContentReflectionHelper.detectXContentPackage();
        // _count 响应格式: {"count": 12345, "_shards": {...}}
        // 直接解析 JSON 取 count 字段
        return XContentReflectionHelper.parseCountResponse(response.getEntity().getContent(), xContentPackage);
    }

    private static SearchResponse executeSearchViaLowLevelApi(RestHighLevelClient highLevelClient,
                                                              SearchRequest searchRequest) throws IOException {
        org.elasticsearch.client.RestClient lowLevelClient = highLevelClient.getLowLevelClient();

        String indices = String.join(",", searchRequest.indices());
        String endpoint = SimpleElasticsearchRouteConstant.ENDPOINT_ROOT + indices + SimpleElasticsearchRouteConstant.ENDPOINT_SEARCH;

        // scroll 参数追加到 URL（ES 6.x 低级 API 需要通过 query param 传递）
        if (searchRequest.scroll() != null && searchRequest.scroll().keepAlive() != null) {
            endpoint = endpoint + SimpleElasticsearchSearchConstant.ES_SCROLL_QUERY_PARAM
                    + searchRequest.scroll().keepAlive().getStringRep();
        }

        org.elasticsearch.client.Request request = new org.elasticsearch.client.Request(
                SimpleElasticsearchRouteConstant.HTTP_METHOD_POST, endpoint);

        if (searchRequest.source() != null) {
            String dsl = DslCompatibilityHelper.removeEs7OnlyCompositeFields(searchRequest.source().toString());
            request.setJsonEntity(dsl);
        }

        org.elasticsearch.client.Response response = lowLevelClient.performRequest(request);

        byte[] responseBytes;
        try (InputStream inputStream = response.getEntity().getContent();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] data = new byte[8192];
            int nRead;
            while ((nRead = inputStream.read(data)) != -1) {
                buffer.write(data, 0, nRead);
            }
            responseBytes = buffer.toByteArray();
        }

        String responseJson = new String(responseBytes, StandardCharsets.UTF_8);
        if (responseJson.contains("\"" + SimpleElasticsearchSearchConstant.ES_JSON_AGGREGATIONS + "\"")) {
            log.debug("Detected aggregations in ES 6.x response, will use manual JSON parsing");
            throw new Es6xAggregationResponseException(responseJson);
        }

        String xContentPackage = XContentReflectionHelper.detectXContentPackage();
        return XContentReflectionHelper.parseSearchResponse(responseBytes, xContentPackage);
    }
}
