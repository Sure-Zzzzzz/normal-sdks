package io.github.surezzzzzz.sdk.ops.middleware.catalog;

import io.github.surezzzzzz.sdk.elasticsearch.route.registry.SimpleElasticsearchRouteRegistry;
import io.github.surezzzzzz.sdk.kafka.route.registry.SimpleKafkaRouteRegistry;
import io.github.surezzzzzz.sdk.mysql.route.registry.SimpleMysqlRouteRegistry;
import io.github.surezzzzzz.sdk.ops.middleware.configuration.SmartMiddlewareOpsServerProperties;
import io.github.surezzzzzz.sdk.ops.middleware.service.AbstractMiddlewareOpsExecutor;
import io.github.surezzzzzz.sdk.ops.middleware.service.MiddlewareType;
import io.github.surezzzzzz.sdk.redis.route.registry.SimpleRedisRouteRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.*;

/**
 * 从 Route 已初始化 Registry 构建安全数据源目录的执行器。
 *
 * @author surezzzzzz
 */
@Slf4j
public class DatasourceCatalogExecutor extends AbstractMiddlewareOpsExecutor<DatasourceCatalogRequest, DatasourceCatalogResponse>
        implements DatasourceTagResolver {

    private final List<DatasourceCatalogResponse.Item> snapshot;
    private final Map<MiddlewareType, Map<String, String>> tags;

    /**
     * 创建不含展示标签的启动期数据源目录执行器。
     */
    public DatasourceCatalogExecutor(SimpleElasticsearchRouteRegistry elasticsearchRegistry,
                                     SimpleRedisRouteRegistry redisRegistry,
                                     SimpleKafkaRouteRegistry kafkaRegistry) {
        this(elasticsearchRegistry, redisRegistry, kafkaRegistry, new SmartMiddlewareOpsServerProperties.DatasourceTags());
    }

    /**
     * 创建启动期数据源目录执行器。
     */
    public DatasourceCatalogExecutor(SimpleElasticsearchRouteRegistry elasticsearchRegistry,
                                     SimpleRedisRouteRegistry redisRegistry,
                                     SimpleKafkaRouteRegistry kafkaRegistry,
                                     SmartMiddlewareOpsServerProperties.DatasourceTags datasourceTags) {
        this(elasticsearchRegistry, redisRegistry, kafkaRegistry, null, datasourceTags);
    }

    /**
     * 创建包含 MySQL Route 的启动期数据源目录执行器。
     */
    public DatasourceCatalogExecutor(SimpleElasticsearchRouteRegistry elasticsearchRegistry,
                                     SimpleRedisRouteRegistry redisRegistry,
                                     SimpleKafkaRouteRegistry kafkaRegistry,
                                     SimpleMysqlRouteRegistry mysqlRegistry,
                                     SmartMiddlewareOpsServerProperties.DatasourceTags datasourceTags) {
        super(DatasourceCatalogRequest.class);
        Map<MiddlewareType, Map<String, String>> configuredTags = configuredTags(datasourceTags);
        List<DatasourceCatalogResponse.Item> items = new ArrayList<>();
        Map<MiddlewareType, Map<String, String>> snapshotTags = new EnumMap<>(MiddlewareType.class);
        if (elasticsearchRegistry != null) {
            add(items, snapshotTags, MiddlewareType.ELASTICSEARCH,
                    new ArrayList<String>(elasticsearchRegistry.getTemplates().keySet()), configuredTags);
        }
        if (redisRegistry != null) {
            add(items, snapshotTags, MiddlewareType.REDIS, new ArrayList<String>(redisRegistry.getDatasourceKeys()),
                    configuredTags);
        }
        if (kafkaRegistry != null) {
            add(items, snapshotTags, MiddlewareType.KAFKA, new ArrayList<String>(kafkaRegistry.getDatasourceKeys()),
                    configuredTags);
        }
        if (mysqlRegistry != null) {
            add(items, snapshotTags, MiddlewareType.MYSQL, new ArrayList<String>(mysqlRegistry.getDatasources()),
                    configuredTags);
        }
        Collections.sort(items, new Comparator<DatasourceCatalogResponse.Item>() {
            @Override
            public int compare(DatasourceCatalogResponse.Item left, DatasourceCatalogResponse.Item right) {
                int type = left.getMiddlewareType().getCode().compareTo(right.getMiddlewareType().getCode());
                return type == 0 ? left.getDatasourceKey().compareTo(right.getDatasourceKey()) : type;
            }
        });
        this.snapshot = Collections.unmodifiableList(items);
        this.tags = immutable(snapshotTags);
        warnUnknownTags(configuredTags, this.tags);
    }

    @Override
    public DatasourceCatalogResponse execute(DatasourceCatalogRequest request) {
        List<DatasourceCatalogResponse.Item> items = new ArrayList<>();
        for (DatasourceCatalogResponse.Item item : snapshot) {
            if (item.getMiddlewareType() == request.getMiddlewareType()) {
                items.add(item);
            }
        }
        return DatasourceCatalogResponse.builder().items(Collections.unmodifiableList(items)).build();
    }

    @Override
    public String resolve(MiddlewareType middlewareType, String datasourceKey) {
        if (middlewareType == null || datasourceKey == null) {
            return null;
        }
        Map<String, String> values = tags.get(middlewareType);
        return values == null ? null : values.get(datasourceKey);
    }

    private Map<MiddlewareType, Map<String, String>> configuredTags(
            SmartMiddlewareOpsServerProperties.DatasourceTags datasourceTags) {
        SmartMiddlewareOpsServerProperties.DatasourceTags source = datasourceTags == null
                ? new SmartMiddlewareOpsServerProperties.DatasourceTags() : datasourceTags;
        Map<MiddlewareType, Map<String, String>> result = new EnumMap<>(MiddlewareType.class);
        result.put(MiddlewareType.ELASTICSEARCH, source.getElasticsearch());
        result.put(MiddlewareType.REDIS, source.getRedis());
        result.put(MiddlewareType.KAFKA, source.getKafka());
        result.put(MiddlewareType.MYSQL, source.getMysql());
        return result;
    }

    private void add(List<DatasourceCatalogResponse.Item> items, Map<MiddlewareType, Map<String, String>> snapshotTags,
                     MiddlewareType type, List<String> datasourceKeys,
                     Map<MiddlewareType, Map<String, String>> configuredTags) {
        Map<String, String> tagsForType = configuredTags.get(type);
        Map<String, String> resolvedTags = new HashMap<>();
        for (String datasourceKey : datasourceKeys) {
            String clusterTag = tagsForType == null ? null : tagsForType.get(datasourceKey);
            items.add(DatasourceCatalogResponse.Item.builder().middlewareType(type).datasourceKey(datasourceKey)
                    .clusterTag(clusterTag).build());
            resolvedTags.put(datasourceKey, clusterTag);
        }
        snapshotTags.put(type, resolvedTags);
    }

    private void warnUnknownTags(Map<MiddlewareType, Map<String, String>> configuredTags,
                                 Map<MiddlewareType, Map<String, String>> snapshotTags) {
        for (Map.Entry<MiddlewareType, Map<String, String>> entry : configuredTags.entrySet()) {
            Map<String, String> configured = entry.getValue();
            Map<String, String> snapshotValues = snapshotTags.get(entry.getKey());
            if (configured == null) {
                continue;
            }
            for (String datasourceKey : configured.keySet()) {
                if (snapshotValues == null || !snapshotValues.containsKey(datasourceKey)) {
                    log.warn("数据源展示标签未匹配已初始化 Route 数据源，middlewareType={}，datasourceKey={}",
                            entry.getKey(), datasourceKey);
                }
            }
        }
    }

    private Map<MiddlewareType, Map<String, String>> immutable(Map<MiddlewareType, Map<String, String>> source) {
        Map<MiddlewareType, Map<String, String>> result = new EnumMap<>(MiddlewareType.class);
        for (Map.Entry<MiddlewareType, Map<String, String>> entry : source.entrySet()) {
            result.put(entry.getKey(), Collections.unmodifiableMap(new HashMap<>(entry.getValue())));
        }
        return Collections.unmodifiableMap(result);
    }
}
