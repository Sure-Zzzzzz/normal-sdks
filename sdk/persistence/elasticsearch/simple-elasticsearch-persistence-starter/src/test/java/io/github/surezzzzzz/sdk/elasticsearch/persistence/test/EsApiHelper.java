package io.github.surezzzzzz.sdk.elasticsearch.persistence.test;

import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchLowLevelRequestHelper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RestClient;

import java.util.Map;

/**
 * 测试辅助类：保留 persistence 测试入口，ES API 细节委托 route helper。
 *
 * @author surezzzzzz
 */
@Slf4j
public final class EsApiHelper {

    private EsApiHelper() {
    }

    public static boolean indexExists(RestClient client, String indexName) {
        return ElasticsearchLowLevelRequestHelper.indexExists(client, indexName);
    }

    public static void createIndex(RestClient client, String indexName) {
        ElasticsearchLowLevelRequestHelper.createIndex(client, indexName);
        log.info("创建索引 {}", indexName);
    }

    public static void deleteIndex(RestClient client, String indexName) {
        ElasticsearchLowLevelRequestHelper.deleteIndex(client, indexName);
        log.info("删除索引 {}", indexName);
    }

    public static void refreshIndex(RestClient client, String indexName) {
        ElasticsearchLowLevelRequestHelper.refreshIndex(client, indexName);
    }

    public static boolean docExists(RestClient client, String indexName, String id) throws Exception {
        boolean exists = ElasticsearchLowLevelRequestHelper.docExists(client, indexName, id);
        log.info("HEAD {}/_doc/{} exists={}", indexName, id, exists);
        return exists;
    }

    public static Map<String, Object> getDoc(RestClient client, String indexName, String id) throws Exception {
        Map<String, Object> source = ElasticsearchLowLevelRequestHelper.getDoc(client, indexName, id);
        log.info("GET {}/_doc/{} source={}", indexName, id, source);
        return source;
    }
}
