package io.github.surezzzzzz.sdk.elasticsearch.search.metadata;

import io.github.surezzzzzz.sdk.elasticsearch.route.model.ClusterInfo;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchLowLevelRequestHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchResponseHelper;
import io.github.surezzzzzz.sdk.elasticsearch.route.support.ElasticsearchVersionHelper;
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
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.model.ResolvedIndexConfig;
import io.github.surezzzzzz.sdk.elasticsearch.search.metadata.parser.FieldMetadataParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsRequest;
import org.elasticsearch.action.admin.indices.mapping.get.GetMappingsResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.*;
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
            String identifier = getConfigIdentifier(indexConfig);
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
        return getMetadata(resolveIndexConfig(indexAlias));
    }

    /**
     * 获取索引元数据
     *
     * @param resolvedIndexConfig 解析后的索引配置
     * @return 索引元数据
     */
    public IndexMetadata getMetadata(ResolvedIndexConfig resolvedIndexConfig) {
        String cacheKey = resolvedIndexConfig.getConfigIdentifier();
        IndexMetadata metadata = metadataCache.get(cacheKey);
        if (metadata != null) {
            return metadata;
        }

        try {
            return loadMetadata(resolvedIndexConfig.getIndexConfig(), null);
        } catch (Exception e) {
            throw new MappingException(ErrorCode.LOAD_MAPPING_FAILED,
                    String.format(ErrorMessage.LOAD_MAPPING_FAILED, resolvedIndexConfig.getRequestIndex()), e);
        }
    }

    /**
     * 获取索引元数据（支持指定具体索引）
     *
     * @param indexAlias        索引别名
     * @param specificIndexName 具体索引名称（可选，用于通配符索引）
     * @return 索引元数据
     */
    public IndexMetadata getMetadata(String indexAlias, String specificIndexName) {
        ResolvedIndexConfig resolvedIndexConfig = findResolvedIndexConfig(indexAlias);
        if (resolvedIndexConfig == null && StringUtils.hasText(specificIndexName)) {
            resolvedIndexConfig = findResolvedIndexConfig(specificIndexName);
        }
        if (resolvedIndexConfig == null) {
            throw indexNotConfiguredException(indexAlias);
        }
        if (!StringUtils.hasText(specificIndexName)) {
            return getMetadata(resolvedIndexConfig);
        }

        try {
            return loadMetadata(resolvedIndexConfig.getIndexConfig(), specificIndexName);
        } catch (Exception e) {
            throw new MappingException(ErrorCode.LOAD_MAPPING_FAILED,
                    String.format(ErrorMessage.LOAD_MAPPING_FAILED, indexAlias), e);
        }
    }

    /**
     * 解析请求索引命中的配置
     *
     * @param requestIndex 请求索引
     * @return 解析后的索引配置
     */
    public ResolvedIndexConfig resolveIndexConfig(String requestIndex) {
        ResolvedIndexConfig resolvedIndexConfig = findResolvedIndexConfig(requestIndex);
        if (resolvedIndexConfig == null) {
            throw indexNotConfiguredException(requestIndex);
        }
        return resolvedIndexConfig;
    }

    /**
     * 查找请求索引命中的配置，不命中时返回 null
     *
     * @param requestIndex 请求索引
     * @return 解析后的索引配置
     */
    public ResolvedIndexConfig findResolvedIndexConfig(String requestIndex) {
        if (!StringUtils.hasText(requestIndex)) {
            return null;
        }

        SimpleElasticsearchSearchProperties.IndexConfig exactConfig = findExactIndexConfig(requestIndex);
        if (exactConfig != null) {
            return buildResolvedIndexConfig(requestIndex, exactConfig, false);
        }

        List<SimpleElasticsearchSearchProperties.IndexConfig> wildcardMatches = new ArrayList<>();
        for (SimpleElasticsearchSearchProperties.IndexConfig config : properties.getIndices()) {
            String configName = config.getName();
            if (StringUtils.hasText(configName) && isWildcardPattern(configName)
                    && PatternMatchUtils.simpleMatch(configName, requestIndex)) {
                wildcardMatches.add(config);
            }
        }

        if (wildcardMatches.isEmpty()) {
            return null;
        }
        if (wildcardMatches.size() > 1) {
            log.warn("查询索引 [{}] 命中多个 search.indices wildcard name 配置，按配置顺序使用第一个：{}",
                    requestIndex, formatConfiguredIdentifiers(wildcardMatches));
        }
        return buildResolvedIndexConfig(requestIndex, wildcardMatches.get(0), true);
    }

    /**
     * 解析配置标识，不命中时返回原值
     *
     * @param requestIndex 请求索引
     * @return 配置标识
     */
    public String resolveConfigIdentifierOrSelf(String requestIndex) {
        ResolvedIndexConfig resolvedIndexConfig = findResolvedIndexConfig(requestIndex);
        return resolvedIndexConfig == null ? requestIndex : resolvedIndexConfig.getConfigIdentifier();
    }

    /**
     * 查找索引配置
     *
     * @param identifier 请求索引或配置标识
     * @return 索引配置
     */
    public SimpleElasticsearchSearchProperties.IndexConfig findIndexConfig(String identifier) {
        ResolvedIndexConfig resolvedIndexConfig = findResolvedIndexConfig(identifier);
        return resolvedIndexConfig == null ? null : resolvedIndexConfig.getIndexConfig();
    }

    /**
     * 刷新指定索引的 mapping
     *
     * @param indexAlias 索引别名
     */
    public void refreshMetadata(String indexAlias) {
        ResolvedIndexConfig resolvedIndexConfig = resolveIndexConfig(indexAlias);
        try {
            loadMetadata(resolvedIndexConfig.getIndexConfig(), null);
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
                log.error("Failed to refresh mapping for index: {}", getConfigIdentifier(indexConfig), e);
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
        String cacheKey = getConfigIdentifier(indexConfig);

        log.debug("Loading mapping for index: {} ({})", cacheKey, indexName);

        String datasourceKey = routeResolver.resolveDataSource(indexName);
        log.debug("Index [{}] routed to datasource [{}]", indexName, datasourceKey);

        RestHighLevelClient client = registry.getHighLevelClient(datasourceKey);
        ClusterInfo clusterInfo = registry.getClusterInfo(datasourceKey);

        GetMappingsRequest request = new GetMappingsRequest().indices(indexName);
        org.elasticsearch.action.support.IndicesOptions indicesOptions = null;
        if (properties.getQueryLimits().isIgnoreUnavailableIndices()) {
            indicesOptions = org.elasticsearch.action.support.IndicesOptions.lenientExpandOpen();
            request.indicesOptions(indicesOptions);
        }

        GetMappingsResponse response = executeMapping(client, datasourceKey, clusterInfo, request, indexName, indicesOptions);

        Map<String, Object> mappings = new ConcurrentHashMap<>();
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
        Collections.sort(actualIndices);

        List<FieldMetadata> fields = new ArrayList<>();
        if (specificIndexName != null && !specificIndexName.isEmpty()) {
            if (!mappings.containsKey(specificIndexName)) {
                throw new MappingException(ErrorCode.SPECIFIC_INDEX_NOT_FOUND,
                        String.format(ErrorMessage.SPECIFIC_INDEX_NOT_FOUND, specificIndexName,
                                String.join(",", actualIndices)));
            }
            fields = parseSingleIndex(mappings.get(specificIndexName), indexConfig);
            log.debug("Loaded field metadata from specific index: {}", specificIndexName);
        } else {
            LinkedHashMap<String, Map<String, Object>> indexProperties = new LinkedHashMap<>();
            for (String idx : actualIndices) {
                Object mappingMetadata = mappings.get(idx);
                Map<String, Object> sourceAsMap = ElasticsearchResponseHelper.extractMappingSourceAsMap(mappingMetadata);
                if (sourceAsMap.containsKey(SimpleElasticsearchSearchConstant.ES_MAPPING_PROPERTIES)) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> propertiesMap = (Map<String, Object>) sourceAsMap.get(
                            SimpleElasticsearchSearchConstant.ES_MAPPING_PROPERTIES);
                    indexProperties.put(idx, propertiesMap);
                }
            }
            if (!indexProperties.isEmpty()) {
                fields = fieldMetadataParser.parseAndMerge(indexProperties, indexConfig);
            }
            log.debug("Merged field metadata from {} indices: {}",
                    indexProperties.size(), indexProperties.keySet());
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

        log.debug("Loaded {} fields for index: {} (merged from {} indices)",
                fields.size(), cacheKey, actualIndices.size());
        return metadata;
    }

    /**
     * 解析单个索引的字段元数据
     *
     * @param mappingMetadata 索引 mapping 元数据
     * @param indexConfig     索引配置
     * @return 字段元数据列表
     */
    private List<FieldMetadata> parseSingleIndex(Object mappingMetadata,
                                                 SimpleElasticsearchSearchProperties.IndexConfig indexConfig) {
        Map<String, Object> sourceAsMap = ElasticsearchResponseHelper.extractMappingSourceAsMap(mappingMetadata);
        if (sourceAsMap.containsKey(SimpleElasticsearchSearchConstant.ES_MAPPING_PROPERTIES)) {
            @SuppressWarnings("unchecked")
            Map<String, Object> propertiesMap = (Map<String, Object>) sourceAsMap.get(
                    SimpleElasticsearchSearchConstant.ES_MAPPING_PROPERTIES);
            return fieldMetadataParser.parse(propertiesMap, "", indexConfig);
        }
        return new ArrayList<>();
    }

    private GetMappingsResponse executeMapping(RestHighLevelClient client,
                                               String datasourceKey,
                                               ClusterInfo clusterInfo,
                                               GetMappingsRequest request,
                                               String indexName,
                                               org.elasticsearch.action.support.IndicesOptions indicesOptions) throws IOException {
        if (ElasticsearchVersionHelper.isEs6(clusterInfo)) {
            log.debug("Using low-level mapping API for ES 6.x compatibility");
            return ElasticsearchLowLevelRequestHelper.executeMapping(
                    client.getLowLevelClient(), indexName, indicesOptions, GetMappingsResponse.class);
        }
        if (ElasticsearchVersionHelper.isUnknown(clusterInfo)) {
            log.warn("Datasource [{}] version not yet detected, will try high-level API first. " +
                            "Recommend configuring 'sources.{}.server-version' to avoid compatibility issues.",
                    datasourceKey, datasourceKey);
        }
        try {
            return client.indices().getMapping(request, RequestOptions.DEFAULT);
        } catch (org.elasticsearch.ElasticsearchStatusException e) {
            if (ElasticsearchVersionHelper.isUnknown(clusterInfo)
                    && ElasticsearchResponseHelper.shouldFallbackToLowLevel(e)) {
                log.warn("High-level mapping API failed with compatibility issue, falling back to low-level API for datasource [{}]", datasourceKey);
                return ElasticsearchLowLevelRequestHelper.executeMapping(
                        client.getLowLevelClient(), indexName, indicesOptions, GetMappingsResponse.class);
            }
            throw e;
        }
    }

    private SimpleElasticsearchSearchProperties.IndexConfig findExactIndexConfig(String identifier) {
        for (SimpleElasticsearchSearchProperties.IndexConfig config : properties.getIndices()) {
            if (StringUtils.hasText(config.getAlias()) && config.getAlias().equals(identifier)) {
                return config;
            }
        }
        for (SimpleElasticsearchSearchProperties.IndexConfig config : properties.getIndices()) {
            if (config.getName().equals(identifier)) {
                return config;
            }
        }
        return null;
    }

    private ResolvedIndexConfig buildResolvedIndexConfig(String requestIndex,
                                                         SimpleElasticsearchSearchProperties.IndexConfig config,
                                                         boolean wildcardMatched) {
        return ResolvedIndexConfig.builder()
                .requestIndex(requestIndex)
                .configIndex(config.getName())
                .configIdentifier(getConfigIdentifier(config))
                .indexConfig(config)
                .wildcardMatched(wildcardMatched)
                .build();
    }

    private boolean isWildcardPattern(String value) {
        return value.contains(SimpleElasticsearchSearchConstant.WILDCARD_STAR)
                || value.contains(SimpleElasticsearchSearchConstant.WILDCARD_QUESTION);
    }

    private MappingException indexNotConfiguredException(String requestIndex) {
        return new MappingException(ErrorCode.INDEX_NOT_CONFIGURED,
                String.format(ErrorMessage.INDEX_NOT_CONFIGURED, requestIndex, formatConfiguredIdentifiers(properties.getIndices())));
    }

    private String formatConfiguredIdentifiers(List<SimpleElasticsearchSearchProperties.IndexConfig> configs) {
        List<String> identifiers = new ArrayList<>();
        for (SimpleElasticsearchSearchProperties.IndexConfig config : configs) {
            identifiers.add(formatConfigIdentifier(config));
        }
        return String.join(",", identifiers);
    }

    private String formatConfigIdentifier(SimpleElasticsearchSearchProperties.IndexConfig config) {
        if (StringUtils.hasText(config.getAlias())) {
            return config.getAlias() + "(" + config.getName() + ")";
        }
        return config.getName();
    }

    private String getConfigIdentifier(SimpleElasticsearchSearchProperties.IndexConfig config) {
        return StringUtils.hasText(config.getAlias()) ? config.getAlias() : config.getName();
    }
}
