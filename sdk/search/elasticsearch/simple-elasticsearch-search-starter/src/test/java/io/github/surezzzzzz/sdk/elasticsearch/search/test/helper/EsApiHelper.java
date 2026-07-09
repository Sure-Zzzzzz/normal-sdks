package io.github.surezzzzzz.sdk.elasticsearch.search.test.helper;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchLowLevelRequestHelper;
import org.elasticsearch.client.RestClient;

import java.util.Map;

/**
 * ES 测试 API 辅助工具。
 *
 * <p>只保留 search 测试侧 registry/datasource 适配，ES API 细节委托 route helper。</p>
 *
 * @author surezzzzzz
 */
public class EsApiHelper {

    private EsApiHelper() {
    }

    /**
     * 检查索引是否存在。
     *
     * @param registry   路由注册表
     * @param datasource 数据源
     * @param indexName  索引名
     * @return true=存在
     */
    public static boolean indexExists(SimpleElasticsearchRouteRegistry registry,
                                      String datasource,
                                      String indexName) {
        return ElasticsearchLowLevelRequestHelper.indexExists(lowLevelClient(registry, datasource), indexName);
    }

    /**
     * 删除索引。404 视为已删除。
     *
     * @param registry   路由注册表
     * @param datasource 数据源
     * @param indexName  索引名，支持通配符
     */
    public static void deleteIndex(SimpleElasticsearchRouteRegistry registry,
                                   String datasource,
                                   String indexName) {
        ElasticsearchLowLevelRequestHelper.deleteIndex(lowLevelClient(registry, datasource), indexName);
    }

    /**
     * 创建索引（传入完整 mapping JSON，自动适配 ES 6.x / 7.x mapping 格式）。
     *
     * @param registry    路由注册表
     * @param datasource  数据源
     * @param indexName   索引名
     * @param mappingJson ES 7.x 风格 mapping：{"properties": {...}}
     */
    public static void createIndex(SimpleElasticsearchRouteRegistry registry,
                                   String datasource,
                                   String indexName,
                                   String mappingJson) {
        ElasticsearchLowLevelRequestHelper.createIndex(lowLevelClient(registry, datasource), indexName, mappingJson,
                registry.getClusterInfo(datasource));
    }

    /**
     * 创建索引（传入 properties map）。
     *
     * @param registry   路由注册表
     * @param datasource 数据源
     * @param indexName  索引名
     * @param properties properties 定义
     */
    public static void createIndex(SimpleElasticsearchRouteRegistry registry,
                                   String datasource,
                                   String indexName,
                                   Map<String, Object> properties) {
        ElasticsearchLowLevelRequestHelper.createIndex(lowLevelClient(registry, datasource), indexName, properties,
                registry.getClusterInfo(datasource));
    }

    /**
     * 删除旧索引后创建新索引。
     *
     * @param registry    路由注册表
     * @param datasource  数据源
     * @param indexName   索引名
     * @param mappingJson mapping JSON
     */
    public static void recreateIndex(SimpleElasticsearchRouteRegistry registry,
                                     String datasource,
                                     String indexName,
                                     String mappingJson) {
        ElasticsearchLowLevelRequestHelper.recreateIndex(lowLevelClient(registry, datasource), indexName, mappingJson,
                registry.getClusterInfo(datasource));
    }

    /**
     * 写入文档并立即 refresh。
     *
     * @param registry   路由注册表
     * @param datasource 数据源
     * @param indexName  索引名
     * @param id         文档 ID
     * @param source     文档内容
     */
    public static void indexDoc(SimpleElasticsearchRouteRegistry registry,
                                String datasource,
                                String indexName,
                                String id,
                                Map<String, Object> source) {
        ElasticsearchLowLevelRequestHelper.indexDoc(lowLevelClient(registry, datasource), indexName, id, source);
    }

    /**
     * 删除文档。404 视为已删除。
     *
     * @param registry   路由注册表
     * @param datasource 数据源
     * @param indexName  索引名
     * @param id         文档 ID
     */
    public static void deleteDoc(SimpleElasticsearchRouteRegistry registry,
                                 String datasource,
                                 String indexName,
                                 String id) {
        ElasticsearchLowLevelRequestHelper.deleteDoc(lowLevelClient(registry, datasource), indexName, id);
    }

    /**
     * 刷新索引。
     *
     * @param registry   路由注册表
     * @param datasource 数据源
     * @param indexName  索引名，支持通配符
     */
    public static void refresh(SimpleElasticsearchRouteRegistry registry,
                               String datasource,
                               String indexName) {
        ElasticsearchLowLevelRequestHelper.refresh(lowLevelClient(registry, datasource), indexName);
    }

    private static RestClient lowLevelClient(SimpleElasticsearchRouteRegistry registry, String datasource) {
        return registry.getLowLevelClient(datasource);
    }

}
