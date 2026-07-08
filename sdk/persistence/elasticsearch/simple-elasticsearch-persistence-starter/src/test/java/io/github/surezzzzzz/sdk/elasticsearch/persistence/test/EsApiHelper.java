package io.github.surezzzzzz.sdk.elasticsearch.persistence.test;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.util.EntityUtils;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;
import org.elasticsearch.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 测试辅助类：通过 RestClient 提供版本无关的 ES 操作（indexExists/createIndex/deleteIndex/refresh/doc 查回）。
 *
 * @author surezzzzzz
 */
@Slf4j
public final class EsApiHelper {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private EsApiHelper() {
    }

    public static boolean indexExists(RestClient client, String indexName) {
        try {
            client.performRequest(new Request("HEAD", "/" + indexName));
            return true;
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return false;
            }
            throw new RuntimeException("Failed to check index existence: " + indexName, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to check index existence: " + indexName, e);
        }
    }

    public static void createIndex(RestClient client, String indexName) {
        try {
            client.performRequest(new Request("PUT", "/" + indexName));
            log.info("创建索引 {}", indexName);
        } catch (Exception e) {
            throw new RuntimeException("Failed to create index: " + indexName, e);
        }
    }

    public static void deleteIndex(RestClient client, String indexName) {
        try {
            client.performRequest(new Request("DELETE", "/" + indexName));
            log.info("删除索引 {}", indexName);
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return;
            }
            throw new RuntimeException("Failed to delete index: " + indexName, e);
        } catch (Exception e) {
            throw new RuntimeException("Failed to delete index: " + indexName, e);
        }
    }

    public static void refreshIndex(RestClient client, String indexName) {
        try {
            client.performRequest(new Request("POST", "/" + indexName + "/_refresh"));
        } catch (Exception e) {
            throw new RuntimeException("Failed to refresh index: " + indexName, e);
        }
    }

    public static boolean docExists(RestClient client, String indexName, String id) throws Exception {
        Response response = client.performRequest(new Request("HEAD", "/" + indexName + "/_doc/" + id));
        boolean exists = response.getStatusLine().getStatusCode() == 200;
        log.info("HEAD {}/_doc/{} exists={}", indexName, id, exists);
        return exists;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> getDoc(RestClient client, String indexName, String id) throws Exception {
        Response response = client.performRequest(new Request("GET", "/" + indexName + "/_doc/" + id));
        int status = response.getStatusLine().getStatusCode();
        String body = EntityUtils.toString(response.getEntity(), StandardCharsets.UTF_8);
        log.info("GET {}/_doc/{} status={}, body={}", indexName, id, status, body);
        if (status == 404) {
            return null;
        }
        Map<String, Object> map = MAPPER.readValue(body, Map.class);
        return (Map<String, Object>) map.get("_source");
    }
}
