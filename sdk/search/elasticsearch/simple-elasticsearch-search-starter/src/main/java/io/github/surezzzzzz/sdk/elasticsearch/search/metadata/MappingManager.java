package io.github.surezzzzzz.sdk.elasticsearch.search.metadata;

import io.github.surezzzzzz.sdk.elasticsearch.route.constant.SimpleElasticsearchRouteConstant;
import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.elasticsearch.route.resolver.RouteResolver;
import io.github.surezzzzzz.sdk.elasticsearch.search.annotation.SimpleElasticsearchSearchComponent;
import io.github.surezzzzzz.sdk.elasticsearch.search.configuration.SimpleElasticsearchSearchProperties;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SimpleElasticsearchSearchConstant;
import io.github.surezzzzzz.sdk.elasticsearch.search.exception.MappingException;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.FieldMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.IndexMetadata;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.parser.FieldMetadataParser;
import io.github.surezzzzzz.sdk.elasticsearch.search.support.ElasticsearchCompatibilityHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.cluster.metadata.MappingMetadata;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mapping 管理器
 * 负责索引 mapping 的加载、缓存和刷新
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleElasticsearchSearchComponent
@RequiredArgsConstructor
public class MappingManager {

    private final SimpleElasticsearchSearchProperties properties;
    private final SimpleElasticsearchRouteRegistry registry;
    private final RouteResolver routeResolver;
    private final FieldMetadataParser fieldMetadataParser;

    /**
     * 缓存：alias -> IndexMetadata
     */
    private final Map<String, IndexMetadata> metadataCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        log.info("MappingManager initializing...");
        for (SimpleElasticsearchSearchProperties.IndexConfig indexConfig : properties.getIndices()) {
            String identifier = (indexConfig.getAlias() != null && !indexConfig.getAlias().isEmpty())
                    ? indexConfig.getAlias() : indexConfig.getName();
            if (!indexConfig.isLazyLoad()) {
                try {
                    loadMetadata(indexConfig, null);
                    log.info("Loaded mapping for index: {}", identifier);
                } catch (Exception e) {
                    log.error("Failed to load mapping for index: {}", identifier, e);
                }
            } else {
                log.info("Index [{}] configured as lazy-load, will load on first access", identifier);
            }
        }
        log.info("MappingManager initialized, cached {} indices", metadataCache.size());
    }

    @Scheduled(fixedDelayString = "#{${io.github.surezzzzzz.sdk.elasticsearch.search.mapping-refresh.enabled:false} ? " +
            "${io.github.surezzzzzz.sdk.elasticsearch.search.mapping-refresh.interval-seconds:300} * 1000 : Long.MAX_VALUE}")
    public void scheduledRefresh() {
        if (properties.getMappingRefresh().isEnabled()) {
            log.debug("Scheduled mapping refresh triggered");
            refreshAllMetadata();
        }
    }

    /**
     * 获取索引元数据
     *
     * @param indexAlias 索引别名
     * @return 索引元数据
     */
    public IndexMetadata getMetadata(String indexAlias) {
        return getMetadata(indexAlias, null);
    }

    /**
     * 获取索引元数据（支持指定具体索引）
     *
     * @param indexAlias        索引别名
     * @param specificIndexName 具体索引名称（可选，用于通配符索引）
     * @return 索引元数据
     */
    public IndexMetadata getMetadata(String indexAlias, String specificIndexName) {
        if (specificIndexName == null) {
            IndexMetadata metadata = metadataCache.get(indexAlias);
            if (metadata != null) {
                return metadata;
            }
        }

        SimpleElasticsearchSearchProperties.IndexConfig indexConfig = findIndexConfig(indexAlias);
        if (indexConfig == null) {
            throw new MappingException(ErrorCode.INDEX_NOT_CONFIGURED,
                    String.format(ErrorMessage.INDEX_NOT_CONFIGURED, indexAlias));
        }

        try {
            return loadMetadata(indexConfig, specificIndexName);
        } catch (Exception e) {
            throw new MappingException(ErrorCode.LOAD_MAPPING_FAILED,
                    String.format(ErrorMessage.LOAD_MAPPING_FAILED, indexAlias), e);
        }
    }

    /**
     * 刷新指定索引的 mapping
     *
     * @param indexAlias 索引别名
     */
    public void refreshMetadata(String indexAlias) {
        SimpleElasticsearchSearchProperties.IndexConfig indexConfig = findIndexConfig(indexAlias);
        if (indexConfig == null) {
            throw new MappingException(ErrorCode.INDEX_NOT_CONFIGURED,
                    String.format(ErrorMessage.INDEX_NOT_CONFIGURED, indexAlias));
        }
        try {
            loadMetadata(indexConfig, null);
            log.info("Refreshed mapping for index: {}", indexAlias);
        } catch (Exception e) {
            log.error("Failed to refresh mapping for index: {}", indexAlias, e);
            throw new MappingException(ErrorCode.REFRESH_MAPPING_FAILED,
                    String.format(ErrorMessage.REFRESH_MAPPING_FAILED, indexAlias), e);
        }
    }

    /**
     * 刷新所有索引的 mapping
     */
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
        log.info("Mapping refresh completed: {} success, {} failed", successCount, failCount);
    }

    /**
     * 获取所有已配置的索引
     *
     * @return 索引配置列表
     */
    public List<SimpleElasticsearchSearchProperties.IndexConfig> getAllIndices() {
        return properties.getIndices();
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        metadataCache.clear();
        log.info("Mapping cache cleared");
    }

    private IndexMetadata loadMetadata(SimpleElasticsearchSearchProperties.IndexConfig indexConfig,
                                       String specificIndexName) throws IOException {
        String indexName = indexConfig.getName();
        String alias = indexConfig.getAlias();
        String cacheKey = (alias != null && !alias.isEmpty()) ? alias : indexName;

        log.debug("Loading mapping for index: {} ({})", cacheKey, indexName);

        String datasourceKey = routeResolver.resolveDataSource(indexName);
        log.debug("Index [{}] routed to datasource [{}]", indexName, datasourceKey);

        RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);
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

        GetMappingsRequest request = new GetMappingsRequest().indices(indexName);
        if (properties.getQueryLimits().isIgnoreUnavailableIndices()) {
            request.indicesOptions(org.elasticsearch.action.support.IndicesOptions.lenientExpandOpen());
        }

        GetMappingsResponse response;
        if (isEs6x) {
            log.debug("Using low-level API for ES 6.x compatibility");
            response = getMappingViaLowLevelApi(client, indexName,
                    properties.getQueryLimits().isIgnoreUnavailableIndices());
        } else {
            try {
                response = client.indices().getMapping(request, RequestOptions.DEFAULT);
            } catch (org.elasticsearch.ElasticsearchStatusException e) {
                if (versionUnknown && e.getMessage() != null &&
                        (e.getMessage().contains(SimpleElasticsearchSearchConstant.ES_PARAM_INCLUDE_TYPE_NAME) ||
                                e.getMessage().contains(SimpleElasticsearchSearchConstant.ES_PARAM_MASTER_TIMEOUT))) {
                    log.warn("High-level API failed with ES 6.x compatibility issue, falling back to low-level API for datasource [{}]", datasourceKey);
                    response = getMappingViaLowLevelApi(client, indexName,
                            properties.getQueryLimits().isIgnoreUnavailableIndices());
                } else {
                    throw e;
                }
            }
        }

        Map<String, MappingMetadata> mappings = new ConcurrentHashMap<>();
        response.mappings().forEach(indexEntry ->
                indexEntry.value.forEach(typeEntry -> mappings.put(indexEntry.key, typeEntry.value)));

        if (mappings.isEmpty()) {
            if (properties.getQueryLimits().isIgnoreUnavailableIndices()) {
                log.debug("No mappings found for index [{}], returning empty metadata", indexName);
                return IndexMetadata.builder()
                        .alias(alias).indexName(indexName)
                        .dateSplit(indexConfig.isDateSplit()).datePattern(indexConfig.getDatePattern())
                        .dateField(indexConfig.getDateField())
                        .actualIndices(new ArrayList<>()).fields(new ArrayList<>())
                        .cachedAt(System.currentTimeMillis()).build();
            } else {
                throw new MappingException(ErrorCode.INDEX_MAPPING_NOT_FOUND,
                        String.format(ErrorMessage.INDEX_MAPPING_NOT_FOUND, indexName));
            }
        }

        List<String> actualIndices = new ArrayList<>(mappings.keySet());

        String targetIndex;
        if (specificIndexName != null && !specificIndexName.isEmpty()) {
            if (!mappings.containsKey(specificIndexName)) {
                throw new MappingException(ErrorCode.SPECIFIC_INDEX_NOT_FOUND,
                        String.format(ErrorMessage.SPECIFIC_INDEX_NOT_FOUND, specificIndexName,
                                String.join(",", actualIndices)));
            }
            targetIndex = specificIndexName;
        } else {
            targetIndex = actualIndices.get(0);
        }
        log.debug("Loading field metadata from index: {}", targetIndex);

        MappingMetadata mappingMetadata = mappings.get(targetIndex);
        Map<String, Object> sourceAsMap = mappingMetadata.getSourceAsMap();

        List<FieldMetadata> fields = new ArrayList<>();
        if (sourceAsMap.containsKey(SimpleElasticsearchSearchConstant.ES_MAPPING_PROPERTIES)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> propertiesMap = (Map<String, Object>) sourceAsMap.get(
                    SimpleElasticsearchSearchConstant.ES_MAPPING_PROPERTIES);
            fields = fieldMetadataParser.parse(propertiesMap, "", indexConfig);
        }

        IndexMetadata metadata = IndexMetadata.builder()
                .alias(alias).indexName(indexName)
                .dateSplit(indexConfig.isDateSplit()).datePattern(indexConfig.getDatePattern())
                .dateField(indexConfig.getDateField())
                .actualIndices(actualIndices).fields(fields)
                .cachedAt(System.currentTimeMillis()).build();
        metadata.buildFieldMap();

        if (specificIndexName == null && indexConfig.isCacheMapping()) {
            metadataCache.put(cacheKey, metadata);
        }

        log.debug("Loaded {} fields for index: {} (from {})", fields.size(), cacheKey, targetIndex);
        return metadata;
    }

    private GetMappingsResponse getMappingViaLowLevelApi(RestHighLevelClient highLevelClient,
                                                         String indexName,
                                                         boolean ignoreUnavailable) throws IOException {
        org.elasticsearch.client.RestClient lowLevelClient = highLevelClient.getLowLevelClient();
        String endpoint = SimpleElasticsearchRouteConstant.ENDPOINT_ROOT + indexName + SimpleElasticsearchRouteConstant.ENDPOINT_MAPPING;
        org.elasticsearch.client.Request request = new org.elasticsearch.client.Request(
                SimpleElasticsearchRouteConstant.HTTP_METHOD_GET, endpoint);
        if (ignoreUnavailable) {
            request.addParameter(SimpleElasticsearchSearchConstant.ES_PARAM_IGNORE_UNAVAILABLE,
                    SimpleElasticsearchSearchConstant.ES_PARAM_VALUE_TRUE);
            request.addParameter(SimpleElasticsearchSearchConstant.ES_PARAM_ALLOW_NO_INDICES,
                    SimpleElasticsearchSearchConstant.ES_PARAM_VALUE_TRUE);
        }
        org.elasticsearch.client.Response response = lowLevelClient.performRequest(request);
        return ElasticsearchCompatibilityHelper.parseResponse(response, GetMappingsResponse.class);
    }

    private SimpleElasticsearchSearchProperties.IndexConfig findIndexConfig(String identifier) {
        for (SimpleElasticsearchSearchProperties.IndexConfig config : properties.getIndices()) {
            if (config.getAlias() != null && config.getAlias().equals(identifier)) {
                return config;
            }
            if (config.getName().equals(identifier)) {
                return config;
            }
        }
        return null;
    }
}
