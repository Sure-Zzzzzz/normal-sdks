package io.github.surezzzzzz.sdk.elasticsearch.search.test.helper;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.GetIndexRequest;
import org.elasticsearch.xcontent.XContentType;

import java.io.IOException;
import java.util.Map;

/**
 * 测试索引辅助工具
 *
 * <p>用于在测试中创建索引，自动处理 ES 6.x 和 7.x 的 mapping 格式差异</p>
 *
 * <p><b>版本差异：</b>
 * <ul>
 *   <li>ES 6.x: mapping 需要包装在 type 中 (例如 _doc)</li>
 *   <li>ES 7.x+: mapping 直接使用 properties</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
public class TestIndexHelper {

    /**
     * 创建索引（版本自适应）
     *
     * @param registry   路由注册表
     * @param datasource 数据源key
     * @param indexName  索引名称
     * @param properties properties定义（统一格式的Map）
     * @throws IOException IO异常
     */
    public static void createIndex(
            SimpleElasticsearchRouteRegistry registry,
            String datasource,
            String indexName,
            Map<String, Object> properties
    ) throws IOException {
        RestHighLevelClient client = registry.getHighLevelClient(datasource);

        // 检查索引是否存在，存在则跳过
        if (client.indices().exists(new GetIndexRequest(indexName), RequestOptions.DEFAULT)) {
            log.debug("Index [{}] already exists, skip creation", indexName);
            return;
        }

        // 获取版本信息
        ClusterInfo clusterInfo = registry.getClusterInfo(datasource);
        boolean isEs6x = false;

        if (clusterInfo != null && clusterInfo.getEffectiveVersion() != null) {
            int majorVersion = clusterInfo.getEffectiveVersion().getMajor();
            isEs6x = (majorVersion == 6);
            log.debug("Detected ES version: {} for datasource [{}]", majorVersion, datasource);
        }

        // 根据版本构建mapping JSON
        String mappingJson;
        if (isEs6x) {
            // ES 6.x: 需要包装在 type 中
            mappingJson = buildEs6Mapping(properties);
            log.debug("Using ES 6.x mapping format for index [{}]", indexName);
        } else {
            // ES 7.x+: 直接使用 properties
            mappingJson = buildEs7Mapping(properties);
            log.debug("Using ES 7.x+ mapping format for index [{}]", indexName);
        }

        // 创建索引
        CreateIndexRequest request = new CreateIndexRequest(indexName);
        request.mapping(mappingJson, XContentType.JSON);

        client.indices().create(request, RequestOptions.DEFAULT);
        log.info("✓ Created index [{}] on datasource [{}]", indexName, datasource);
    }

    /**
     * 构建 ES 6.x 格式的 mapping JSON
     * 格式：{"_doc": {"properties": {...}}}
     */
    private static String buildEs6Mapping(Map<String, Object> properties) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"_doc\": {");
        json.append("\"properties\": ");
        json.append(convertPropertiesToJson(properties));
        json.append("}");
        json.append("}");
        return json.toString();
    }

    /**
     * 构建 ES 7.x+ 格式的 mapping JSON
     * 格式：{"properties": {...}}
     */
    private static String buildEs7Mapping(Map<String, Object> properties) {
        StringBuilder json = new StringBuilder();
        json.append("{");
        json.append("\"properties\": ");
        json.append(convertPropertiesToJson(properties));
        json.append("}");
        return json.toString();
    }

    /**
     * 将 Map 格式的 properties 转换为 JSON 字符串
     * 这里简化处理，假设properties已经是符合格式的Map
     */
    @SuppressWarnings("unchecked")
    private static String convertPropertiesToJson(Map<String, Object> properties) {
        StringBuilder json = new StringBuilder();
        json.append("{");

        boolean first = true;
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            if (!first) {
                json.append(",");
            }
            first = false;

            json.append("\"").append(entry.getKey()).append("\": ");

            Object value = entry.getValue();
            if (value instanceof Map) {
                // 字段定义，如 {"type": "keyword"}
                Map<String, Object> fieldDef = (Map<String, Object>) value;
                json.append("{");
                boolean firstField = true;
                for (Map.Entry<String, Object> fieldEntry : fieldDef.entrySet()) {
                    if (!firstField) {
                        json.append(",");
                    }
                    firstField = false;
                    json.append("\"").append(fieldEntry.getKey()).append("\": ");
                    json.append("\"").append(fieldEntry.getValue()).append("\"");
                }
                json.append("}");
            }
        }

        json.append("}");
        return json.toString();
    }
}
