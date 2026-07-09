package io.github.surezzzzzz.sdk.elasticsearch.search.test.helper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.ResponseException;

import java.io.IOException;
import java.time.temporal.TemporalAccessor;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ES 测试 API 辅助工具。
 *
 * <p>测试侧统一走 low-level RestClient，避免依赖 ES 7.x 专属的
 * org.elasticsearch.client.indices.* / org.elasticsearch.xcontent.* 包路径。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
public class EsApiHelper {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

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
        try {
            Response response = registry.getLowLevelClient(datasource).performRequest(new Request("HEAD", "/" + indexName));
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 200) {
                return true;
            }
            if (statusCode == 404) {
                return false;
            }
            throw new EsApiTestException("检查索引是否存在失败：" + indexName
                    + ", status=" + statusCode);
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return false;
            }
            throw new EsApiTestException("检查索引是否存在失败：" + indexName, e);
        } catch (IOException e) {
            throw new EsApiTestException("检查索引是否存在失败：" + indexName, e);
        }
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
        try {
            registry.getLowLevelClient(datasource).performRequest(new Request("DELETE", "/" + indexName));
            log.debug("已删除索引 [{}]，数据源 [{}]", indexName, datasource);
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return;
            }
            throw new EsApiTestException("删除索引失败：" + indexName, e);
        } catch (IOException e) {
            throw new EsApiTestException("删除索引失败：" + indexName, e);
        }
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
        String actualMapping = adaptMapping(registry, datasource, mappingJson);
        Request request = new Request("PUT", "/" + indexName);
        request.setJsonEntity(actualMapping);
        try {
            registry.getLowLevelClient(datasource).performRequest(request);
            log.info("✓ 已创建索引 [{}]，数据源 [{}]", indexName, datasource);
        } catch (IOException e) {
            throw new EsApiTestException("创建索引失败：" + indexName, e);
        }
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
        try {
            String mappingJson = "{\"properties\":" + OBJECT_MAPPER.writeValueAsString(properties) + "}";
            createIndex(registry, datasource, indexName, mappingJson);
        } catch (IOException e) {
            throw new EsApiTestException("序列化 mapping properties 失败：" + indexName, e);
        }
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
        if (indexExists(registry, datasource, indexName)) {
            deleteIndex(registry, datasource, indexName);
        }
        createIndex(registry, datasource, indexName, mappingJson);
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
        Request request = new Request("PUT", "/" + indexName + "/_doc/" + id);
        request.addParameter("refresh", "true");
        try {
            request.setJsonEntity(OBJECT_MAPPER.writeValueAsString(normalizeSource(source)));
            registry.getLowLevelClient(datasource).performRequest(request);
        } catch (IOException e) {
            throw new EsApiTestException("写入文档失败：" + indexName + "/" + id, e);
        }
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
        Request request = new Request("DELETE", "/" + indexName + "/_doc/" + id);
        request.addParameter("refresh", "true");
        try {
            registry.getLowLevelClient(datasource).performRequest(request);
        } catch (ResponseException e) {
            if (e.getResponse().getStatusLine().getStatusCode() == 404) {
                return;
            }
            throw new EsApiTestException("删除文档失败：" + indexName + "/" + id, e);
        } catch (IOException e) {
            throw new EsApiTestException("删除文档失败：" + indexName + "/" + id, e);
        }
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
        try {
            registry.getLowLevelClient(datasource).performRequest(new Request("POST", "/" + indexName + "/_refresh"));
        } catch (IOException e) {
            throw new EsApiTestException("刷新索引失败：" + indexName, e);
        }
    }

    private static Map<String, Object> normalizeSource(Map<String, Object> source) {
        Map<String, Object> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            Object value = entry.getValue();
            if (value instanceof TemporalAccessor) {
                normalized.put(entry.getKey(), value.toString());
            } else {
                normalized.put(entry.getKey(), value);
            }
        }
        return normalized;
    }

    private static String adaptMapping(SimpleElasticsearchRouteRegistry registry,
                                       String datasource,
                                       String mappingJson) {
        ClusterInfo clusterInfo = registry.getClusterInfo(datasource);
        if (clusterInfo != null && clusterInfo.getEffectiveVersion() != null
                && clusterInfo.getEffectiveVersion().getMajor() == 6) {
            return "{\"mappings\":{\"_doc\":" + mappingJson + "}}";
        }
        return "{\"mappings\":" + mappingJson + "}";
    }
}
