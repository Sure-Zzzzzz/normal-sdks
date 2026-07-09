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
                // ES 6.x 的 getTotalHits() 返回 long（自动装箱）
            return (Long) totalHitsObj;
        }
        // ES 7.x+ 的 getTotalHits() 返回 TotalHits 对象，通过反射取 value 字段
        try {
            Method valueField = totalHitsObj.getClass().getMethod("value");
            return (Long) valueField.invoke(totalHitsObj);
        } catch (Exception e) {
            log.warn("通过反射提取 totalHits 失败，改用字段兜底：{}", e.getMessage());
            // 最终兜底：直接访问 value 字段
            try {
                java.lang.reflect.Field field = totalHitsObj.getClass().getField("value");
                return (Long) field.get(totalHitsObj);
            } catch (Exception ex) {
                log.warn("通过字段兜底提取 totalHits 失败：{}", ex.getMessage());
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
            log.debug("检测到 ES 主版本：{}，数据源：{}", majorVersion, datasourceKey);
        } else {
            versionUnknown = true;
            log.debug("数据源 [{}] 版本尚未探测，优先尝试高级 API", datasourceKey);
        }

        if (isEs6x) {
            log.debug("使用低级 API 兼容 ES 6.x");
            return executeSearchViaLowLevelApi(client, searchRequest);
        } else {
            try {
                return client.search(searchRequest, RequestOptions.DEFAULT);
            } catch (org.elasticsearch.ElasticsearchStatusException e) {
                if (versionUnknown && e.getMessage() != null &&
                        e.getMessage().contains(SimpleElasticsearchSearchConstant.ES_ERROR_UNRECOGNIZED_PARAMETER)) {
                    log.warn("高级 API 遇到 ES 6.x 兼容问题，降级为低级 API 执行，数据源：{}", datasourceKey);
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

        // scroll 参数追加到 URL（ES 6.x 低级 API 需要通过查询参数传递）
        if (searchRequest.scroll() != null && searchRequest.scroll().keepAlive() != null) {
            endpoint = endpoint + SimpleElasticsearchSearchConstant.ES_SCROLL_QUERY_PARAM
                    + searchRequest.scroll().keepAlive().getStringRep();
        }

        org.elasticsearch.client.Request request = new org.elasticsearch.client.Request(
                SimpleElasticsearchRouteConstant.HTTP_METHOD_POST, endpoint);

        // 低级 API 不会自动带 IndicesOptions，需手动转成 query param，否则 ignore_unavailable 等配置丢失
        applyIndicesOptions(request, searchRequest.indicesOptions());

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
            log.debug("检测到 ES 6.x 响应中包含聚合结果，改用手动 JSON 解析");
            throw new Es6xAggregationResponseException(responseJson);
        }

        String xContentPackage = XContentReflectionHelper.detectXContentPackage();
        return XContentReflectionHelper.parseSearchResponse(responseBytes, xContentPackage);
    }

    /**
     * 将 SearchRequest.indicesOptions() 转成低级 API 的 query param。
     *
     * <p>低级 RestClient 不会自动序列化 IndicesOptions，必须手动转成
     * ignore_unavailable / allow_no_indices / expand_wildcards 三个查询参数，
     * 否则 ES 6.x 服务端对不存在的具体索引返回 404。</p>
     */
    private static void applyIndicesOptions(org.elasticsearch.client.Request request,
                                            org.elasticsearch.action.support.IndicesOptions options) {
        if (options == null) {
            return;
        }
        if (options.ignoreUnavailable()) {
            request.addParameter(SimpleElasticsearchSearchConstant.ES_PARAM_IGNORE_UNAVAILABLE,
                    SimpleElasticsearchSearchConstant.ES_PARAM_VALUE_TRUE);
        }
        if (options.allowNoIndices()) {
            request.addParameter(SimpleElasticsearchSearchConstant.ES_PARAM_ALLOW_NO_INDICES,
                    SimpleElasticsearchSearchConstant.ES_PARAM_VALUE_TRUE);
        }
        // 通配符展开范围：open / closed 分别对应开启和关闭状态索引，lenientExpandOpen 只展开 open
        StringBuilder expand = new StringBuilder();
        if (options.expandWildcardsOpen()) {
            expand.append(SimpleElasticsearchSearchConstant.ES_WILDCARD_STATE_OPEN);
        }
        if (options.expandWildcardsClosed()) {
            if (expand.length() > 0) {
                expand.append(SimpleElasticsearchSearchConstant.COMMA);
            }
            expand.append(SimpleElasticsearchSearchConstant.ES_WILDCARD_STATE_CLOSED);
        }
        if (expand.length() > 0) {
            request.addParameter(SimpleElasticsearchSearchConstant.ES_PARAM_EXPAND_WILDCARDS, expand.toString());
        }
    }
}
