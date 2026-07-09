package io.github.surezzzzzz.sdk.elasticsearch.search.test.helper;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;

import java.util.Map;

/**
 * 测试索引辅助工具。
 *
 * <p>保留历史入口，实际委托给 {@link EsApiHelper}。</p>
 *
 * @author surezzzzzz
 */
public class TestIndexHelper {

    private TestIndexHelper() {
    }

    /**
     * 创建索引（版本自适应）。
     *
     * @param registry   路由注册表
     * @param datasource 数据源 key
     * @param indexName  索引名称
     * @param properties properties 定义
     */
    public static void createIndex(SimpleElasticsearchRouteRegistry registry,
                                   String datasource,
                                   String indexName,
                                   Map<String, Object> properties) {
        if (EsApiHelper.indexExists(registry, datasource, indexName)) {
            return;
        }
        EsApiHelper.createIndex(registry, datasource, indexName, properties);
    }
}
