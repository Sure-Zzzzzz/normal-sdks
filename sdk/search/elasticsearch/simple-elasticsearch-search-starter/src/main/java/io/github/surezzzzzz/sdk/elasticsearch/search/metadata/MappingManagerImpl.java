package io.github.surezzzzzz.sdk.elasticsearch.search.metadata;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.ElasticsearchApiConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.FieldType;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SensitiveStrategy;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.MappingException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mapping 管理器实现
 *
 * <p><b>版本兼容性说明：</b>
 * <ul>
 *   <li>使用 simple-elasticsearch-route-starter 提供的 SimpleElasticsearchRouteRegistry</li>
 *   <li>根据索引名称通过 RouteResolver 路由到对应数据源</li>
 *   <li>获取该数据源版本自适应的 RestHighLevelClient，避免版本兼容性问题</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
public class MappingManagerImpl implements MappingManager {

    @Autowired
    private SimpleElasticsearchSearchProperties properties;

    @Autowired
    private SimpleElasticsearchRouteRegistry registry;

    @Autowired
    private RouteResolver routeResolver;

    /**
     * 缓存：alias -> IndexMetadata
     */
    private final Map<String, IndexMetadata> metadataCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("MappingManager initializing...");

        // 如果配置了非懒加载，启动时加载所有索引的 mapping
        for (SimpleElasticsearchSearchProperties.IndexConfig indexConfig : properties.getIndices()) {
            // 获取标识符（有alias用alias，没有用name）
            String identifier = (indexConfig.getAlias() != null && !indexConfig.getAlias().isEmpty())
                    ? indexConfig.getAlias()
                    : indexConfig.getName();

            if (!indexConfig.isLazyLoad()) {
                try {
                    loadMetadata(indexConfig, null);
                    log.info("✓ Loaded mapping for index: {}", identifier);
                } catch (Exception e) {
                    log.error("Failed to load mapping for index: {}", identifier, e);
                }
            } else {
                log.info("Index [{}] configured as lazy-load, will load on first access", identifier);
            }
        }

        log.info("✓ MappingManager initialized, cached {} indices", metadataCache.size());
    }

    /**
     * 定时刷新（如果启用）
     */
    @Scheduled(fixedDelayString = "#{${io.github.surezzzzzz.sdk.elasticsearch.search.mapping-refresh.enabled:false} ? " +
            "${io.github.surezzzzzz.sdk.elasticsearch.search.mapping-refresh.interval-seconds:300} * 1000 : Long.MAX_VALUE}")
    public void scheduledRefresh() {
        if (properties.getMappingRefresh().isEnabled()) {
            log.debug("Scheduled mapping refresh triggered");
            refreshAllMetadata();
        }
    }

    @Override
    public IndexMetadata getMetadata(String indexAlias) {
        return getMetadata(indexAlias, null);
    }

    @Override
    public IndexMetadata getMetadata(String indexAlias, String specificIndexName) {
        // 1. 从缓存获取（如果没有指定具体索引）
        if (specificIndexName == null) {
            IndexMetadata metadata = metadataCache.get(indexAlias);
            if (metadata != null) {
                return metadata;
            }
        }

        // 2. 查找配置
        SimpleElasticsearchSearchProperties.IndexConfig indexConfig = findIndexConfig(indexAlias);
        if (indexConfig == null) {
            throw new MappingException(ErrorCode.INDEX_NOT_CONFIGURED,
                    String.format(ErrorMessage.INDEX_NOT_CONFIGURED, indexAlias));
        }

        // 3. 加载 mapping
        try {
            IndexMetadata metadata = loadMetadata(indexConfig, specificIndexName);
            return metadata;
        } catch (Exception e) {
            throw new MappingException(ErrorCode.LOAD_MAPPING_FAILED,
                    String.format(ErrorMessage.LOAD_MAPPING_FAILED, indexAlias), e);
        }
    }

    @Override
    public void refreshMetadata(String indexAlias) {
        SimpleElasticsearchSearchProperties.IndexConfig indexConfig = findIndexConfig(indexAlias);
        if (indexConfig == null) {
            throw new MappingException(ErrorCode.INDEX_NOT_CONFIGURED,
                    String.format(ErrorMessage.INDEX_NOT_CONFIGURED, indexAlias));
        }

        try {
            loadMetadata(indexConfig, null);
            log.info("✓ Refreshed mapping for index: {}", indexAlias);
        } catch (Exception e) {
            log.error("Failed to refresh mapping for index: {}", indexAlias, e);
            throw new MappingException(ErrorCode.REFRESH_MAPPING_FAILED,
                    String.format(ErrorMessage.REFRESH_MAPPING_FAILED, indexAlias), e);
        }
    }

    @Override
    public void refreshAllMetadata() {
        log.info("Refreshing all index mappings...");
        int successCount = 0;
        int failCount = 0;

        for (SimpleElasticsearchSearchProperties.IndexConfig indexConfig : properties.getIndices()) {
            try {
                loadMetadata(indexConfig, null);
                successCount++;
            } catch (Exception e) {
                failCount++;
                log.error("Failed to refresh mapping for index: {}", indexConfig.getAlias(), e);
            }
        }

        log.info("✓ Mapping refresh completed: {} success, {} failed", successCount, failCount);
    }

    @Override
    public List<SimpleElasticsearchSearchProperties.IndexConfig> getAllIndices() {
        return properties.getIndices();
    }

    @Override
    public void clearCache() {
        metadataCache.clear();
        log.info("✓ Mapping cache cleared");
    }

    /**
     * 加载索引的 mapping
     *
     * @param indexConfig       索引配置
     * @param specificIndexName 具体索引名称（可选，用于通配符索引）
     */
    private IndexMetadata loadMetadata(SimpleElasticsearchSearchProperties.IndexConfig indexConfig, String specificIndexName) throws IOException {
        String indexName = indexConfig.getName();
        String alias = indexConfig.getAlias();
        // 缓存使用的标识符（有alias用alias，没有用name）
        String cacheKey = (alias != null && !alias.isEmpty()) ? alias : indexName;

        log.debug("Loading mapping for index: {} ({})", cacheKey, indexName);

        // 1. 根据索引名称路由到对应数据源
        String datasourceKey = routeResolver.resolveDataSource(indexName);
        log.debug("Index [{}] routed to datasource [{}]", indexName, datasourceKey);

        // 2. 获取该数据源的版本自适应 RestHighLevelClient
        RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);

        // 3. 获取集群信息，检查版本
        ClusterInfo clusterInfo = registry.getClusterInfo(datasourceKey);
        boolean isEs6x = false;
        boolean versionUnknown = false;

        if (clusterInfo != null && clusterInfo.getEffectiveVersion() != null) {
            int majorVersion = clusterInfo.getEffectiveVersion().getMajor();
            isEs6x = (majorVersion == 6);
            log.debug("Detected ES major version: {} for datasource [{}]", majorVersion, datasourceKey);
        } else {
            versionUnknown = true;
            log.warn("Datasource [{}] version not yet detected, will try high-level API first. " +
                            "Recommend configuring 'sources.{}.server-version' to avoid compatibility issues.",
                    datasourceKey, datasourceKey);
        }

        // 4. 调用 ES API 获取 mapping（版本兼容）
        GetMappingsRequest request = new GetMappingsRequest().indices(indexName);
        GetMappingsResponse response;

        if (isEs6x) {
            // ES 6.x：使用 RestClient 低级 API，避免 include_type_name 和 master_timeout 参数
            log.debug("Using low-level API for ES 6.x compatibility");
            response = getMappingViaLowLevelApi(client, indexName);
        } else {
            // ES 7.x+ 或版本未知：尝试使用高级 API
            try {
                response = client.indices().getMapping(request, RequestOptions.DEFAULT);
            } catch (org.elasticsearch.ElasticsearchStatusException e) {
                // 如果是 ES 6.x 参数不兼容错误，fallback 到低级 API
                if (versionUnknown && e.getMessage() != null &&
                        (e.getMessage().contains("include_type_name") || e.getMessage().contains("master_timeout"))) {
                    log.warn("High-level API failed with ES 6.x compatibility issue, falling back to low-level API for datasource [{}]", datasourceKey);
                    response = getMappingViaLowLevelApi(client, indexName);
                } else {
                    throw e;
                }
            }
        }

        // 5. 解析 mapping（ImmutableOpenMap<String, ImmutableOpenMap<String, MappingMetadata>> -> Map）
        Map<String, MappingMetadata> mappings = new ConcurrentHashMap<>();
        response.mappings().forEach(indexEntry -> {
            // indexEntry.key 是索引名
            // indexEntry.value 是 ImmutableOpenMap<String, MappingMetadata>（类型 -> mapping）
            indexEntry.value.forEach(typeEntry -> {
                // typeEntry.key 是类型名（通常是 "_doc"）
                // typeEntry.value 是 MappingMetadata
                mappings.put(indexEntry.key, typeEntry.value);
            });
        });

        if (mappings.isEmpty()) {
            throw new IllegalStateException(String.format(ErrorMessage.INDEX_MAPPING_NOT_FOUND, indexName));
        }

        // 6. 构建 IndexMetadata
        IndexMetadata.IndexMetadataBuilder builder = IndexMetadata.builder()
                .alias(alias)
                .indexName(indexName)
                .dateSplit(indexConfig.isDateSplit())
                .datePattern(indexConfig.getDatePattern())
                .dateField(indexConfig.getDateField())
                .cachedAt(System.currentTimeMillis());


        // 7. 收集实际索引列表
        List<String> actualIndices = new ArrayList<>(mappings.keySet());
        builder.actualIndices(actualIndices);

        // 8. 确定使用哪个索引的 mapping
        String targetIndex;
        if (specificIndexName != null && !specificIndexName.isEmpty()) {
            // 用户指定了具体索引
            if (!mappings.containsKey(specificIndexName)) {
                throw new MappingException(ErrorCode.SPECIFIC_INDEX_NOT_FOUND,
                        String.format(ErrorMessage.SPECIFIC_INDEX_NOT_FOUND, specificIndexName, String.join(",", actualIndices)));
            }
            targetIndex = specificIndexName;
            log.debug("Using specified index: {}", targetIndex);
        } else {
            // 默认取第一个索引
            targetIndex = actualIndices.get(0);
            log.debug("Loading field metadata from index: {}", targetIndex);
        }

        // 9. 解析字段
        MappingMetadata mappingMetadata = mappings.get(targetIndex);
        Map<String, Object> sourceAsMap = mappingMetadata.getSourceAsMap();

        List<FieldMetadata> fields = new ArrayList<>();
        if (sourceAsMap.containsKey("properties")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> propertiesMap = (Map<String, Object>) sourceAsMap.get("properties");
            parseFields(propertiesMap, "", fields, indexConfig);
        }

        builder.fields(fields);

        // 10. 缓存（仅当未指定具体索引时）
        IndexMetadata metadata = builder.build();
        metadata.buildFieldMap();

        if (specificIndexName == null && indexConfig.isCacheMapping()) {
            metadataCache.put(cacheKey, metadata);
        }

        log.debug("✓ Loaded {} fields for index: {} (from {})", fields.size(), cacheKey, targetIndex);

        return metadata;
    }

    /**
     * 使用 RestClient 低级 API 获取 mapping（ES 6.x 兼容）
     * 绕过高级 API 自动添加的 include_type_name 和 master_timeout 参数
     */
    private GetMappingsResponse getMappingViaLowLevelApi(RestHighLevelClient highLevelClient, String indexName) throws IOException {
        org.elasticsearch.client.RestClient lowLevelClient = highLevelClient.getLowLevelClient();

        // 构造请求：GET /<index>/_mapping
        String endpoint = ElasticsearchApiConstant.ENDPOINT_ROOT + indexName + ElasticsearchApiConstant.ENDPOINT_MAPPING;
        org.elasticsearch.client.Request request = new org.elasticsearch.client.Request(
                ElasticsearchApiConstant.HTTP_METHOD_GET,
                endpoint
        );

        // 执行请求
        org.elasticsearch.client.Response response = lowLevelClient.performRequest(request);

        // 解析响应体为 GetMappingsResponse
        // 使用 XContentFactory 创建解析器（使用 try-with-resources 确保资源正确关闭）
        try (org.elasticsearch.xcontent.XContentParser parser = org.elasticsearch.xcontent.XContentFactory.xContent(
                org.elasticsearch.xcontent.XContentType.JSON
        ).createParser(
                org.elasticsearch.xcontent.NamedXContentRegistry.EMPTY,
                org.elasticsearch.xcontent.DeprecationHandler.THROW_UNSUPPORTED_OPERATION,
                response.getEntity().getContent()
        )) {
            return GetMappingsResponse.fromXContent(parser);
        }
    }

    /**
     * 解析字段（递归处理嵌套字段）
     */
    @SuppressWarnings("unchecked")
    private void parseFields(Map<String, Object> propertiesMap, String prefix,
                             List<FieldMetadata> fields,
                             SimpleElasticsearchSearchProperties.IndexConfig indexConfig) {
        for (Map.Entry<String, Object> entry : propertiesMap.entrySet()) {
            String fieldName = prefix.isEmpty() ? entry.getKey() : prefix + "." + entry.getKey();
            Map<String, Object> fieldDef = (Map<String, Object>) entry.getValue();

            // 获取字段类型
            String typeStr = (String) fieldDef.get("type");
            FieldType fieldType = FieldType.fromString(typeStr);

            // 检查是否为敏感字段
            SimpleElasticsearchSearchProperties.SensitiveFieldConfig sensitiveConfig =
                    findSensitiveFieldConfig(indexConfig, fieldName);

            boolean isSensitive = sensitiveConfig != null;
            boolean isForbidden = isSensitive &&
                    SensitiveStrategy.FORBIDDEN.getStrategy().equalsIgnoreCase(sensitiveConfig.getStrategy());
            boolean isMasked = isSensitive &&
                    SensitiveStrategy.MASK.getStrategy().equalsIgnoreCase(sensitiveConfig.getStrategy());

            // 构建字段元数据
            FieldMetadata fieldMetadata = FieldMetadata.builder()
                    .name(fieldName)
                    .type(fieldType)
                    .array(false) // ES 中所有字段都可能是数组
                    .searchable(!isForbidden)
                    .sortable(!isForbidden && fieldType.isSortable())
                    .aggregatable(!isForbidden && fieldType.isAggregatable())
                    .sensitive(isSensitive)
                    .masked(isMasked)
                    .format(fieldType == FieldType.DATE ? (String) fieldDef.get("format") : null)
                    .reason(isForbidden ? SimpleElasticsearchSearchConstant.SENSITIVE_FIELD_REASON : null)
                    .build();

            fields.add(fieldMetadata);

            // 递归处理嵌套字段
            if (fieldDef.containsKey("properties")) {
                Map<String, Object> nestedProperties = (Map<String, Object>) fieldDef.get("properties");
                parseFields(nestedProperties, fieldName, fields, indexConfig);
            }
        }
    }

    /**
     * 查找索引配置（支持通过alias或name查找）
     */
    private SimpleElasticsearchSearchProperties.IndexConfig findIndexConfig(String identifier) {
        for (SimpleElasticsearchSearchProperties.IndexConfig config : properties.getIndices()) {
            // 优先匹配alias（如果有）
            if (config.getAlias() != null && config.getAlias().equals(identifier)) {
                return config;
            }
            // 其次匹配name
            if (config.getName().equals(identifier)) {
                return config;
            }
        }
        return null;
    }

    /**
     * 查找敏感字段配置
     */
    private SimpleElasticsearchSearchProperties.SensitiveFieldConfig findSensitiveFieldConfig(
            SimpleElasticsearchSearchProperties.IndexConfig indexConfig, String fieldName) {
        if (indexConfig.getSensitiveFields() == null) {
            return null;
        }
        for (SimpleElasticsearchSearchProperties.SensitiveFieldConfig config : indexConfig.getSensitiveFields()) {
            if (config.getField().equals(fieldName)) {
                return config;
            }
        }
        return null;
    }
}
