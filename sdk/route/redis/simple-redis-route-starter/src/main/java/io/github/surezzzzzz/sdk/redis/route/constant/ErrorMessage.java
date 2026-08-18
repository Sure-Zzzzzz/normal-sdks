package io.github.surezzzzzz.sdk.redis.route.constant;

/**
 * 错误消息常量
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    public static final String CONFIG_SOURCES_EMPTY = "配置项 'sources' 不能为空，至少需要配置一个 Redis 数据源";
    public static final String CONFIG_DEFAULT_SOURCE_EMPTY = "默认数据源 default-source 不能为空";
    public static final String CONFIG_DEFAULT_SOURCE_NOT_FOUND = "默认数据源 [%s] 不存在，已配置的数据源: %s";
    public static final String CONFIG_DATASOURCE_KEY_EMPTY = "Redis datasource key 不能为空";
    public static final String CONFIG_ROUTE_DATASOURCE_EMPTY = "第 %d 条路由规则 datasource 不能为空，pattern=[%s]，type=[%s]";
    public static final String CONFIG_SOURCE_MODE_INVALID = "数据源 [%s] 的 mode [%s] 无效，有效值: %s";
    public static final String CONFIG_HOST_EMPTY = "standalone 数据源 [%s] 的 host 不能为空";
    public static final String CONFIG_PORT_INVALID = "数据源 [%s] 的 port 必须在 1~65535 范围内";
    public static final String CONFIG_DATABASE_INVALID = "数据源 [%s] 的 database 不能小于 0，当前值: %d";
    public static final String CONFIG_CLUSTER_DATABASE_INVALID = "cluster 数据源 [%s] 的 database 必须为 0，当前值: %d";
    public static final String CONFIG_CLUSTER_NODES_EMPTY = "cluster 数据源 [%s] 的 nodes 不能为空";
    public static final String CONFIG_CLUSTER_MAX_REDIRECTS_INVALID = "cluster 数据源 [%s] 的 maxRedirects 不能小于 0，当前值: %d";
    public static final String CONFIG_NODE_INVALID = "数据源 [%s] 的 nodes 包含不合法 host:port 格式";
    public static final String CONFIG_ROUTE_PATTERN_EMPTY = "第 %d 条路由规则 pattern 不能为空";
    public static final String CONFIG_ROUTE_DATASOURCE_NOT_FOUND = "第 %d 条路由规则引用的数据源 [%s] 不存在，pattern=[%s]，type=[%s]，已配置的数据源: %s";
    public static final String CONFIG_ROUTE_TYPE_INVALID = "第 %d 条路由规则 type [%s] 无效，pattern=[%s]，datasource=[%s]，有效值: %s";
    public static final String CONFIG_ROUTE_REGEX_INVALID = "第 %d 条路由规则 regex 编译失败，pattern=[%s]，type=[%s]，datasource=[%s]";
    public static final String CONFIG_TIMEOUT_INVALID = "数据源 [%s] 的 timeout-ms / connect-timeout-ms / lettuce.shutdown-timeout-ms 必须大于 0";
    public static final String CONFIG_LETTUCE_REQUEST_QUEUE_SIZE_INVALID = "数据源 [%s] 的 lettuce.request-queue-size 必须大于 0，当前值: %d";
    public static final String CONFIG_CLUSTER_REFRESH_PERIOD_INVALID = "数据源 [%s] 的 lettuce.cluster-refresh-period-ms 必须大于 0，当前值: %d";
    public static final String CONFIG_LETTUCE_READ_FROM_INVALID =
            "数据源 [%s] 的 lettuce.read-from [%s] 无效，有效值: %s";
    public static final String CONFIG_LETTUCE_CLUSTER_OPTION_STANDALONE =
            "standalone 数据源 [%s] 不支持非默认 lettuce.%s";
    public static final String CONFIG_LETTUCE_POOL_EVICTION_PERIOD_INVALID =
            "数据源 [%s] 的 lettuce.pool.time-between-eviction-runs-ms 不能小于 -1，当前值: %d";
    public static final String CONFIG_LETTUCE_POOL_MAX_ACTIVE_INVALID =
            "数据源 [%s] 的 lettuce.pool.max-active 必须大于 0，当前值: %d";
    public static final String CONFIG_LETTUCE_POOL_MAX_IDLE_INVALID =
            "数据源 [%s] 的 lettuce.pool.max-idle 不能小于 0，当前值: %d";
    public static final String CONFIG_LETTUCE_POOL_MIN_IDLE_INVALID =
            "数据源 [%s] 的 lettuce.pool.min-idle 不能小于 0，当前值: %d";
    public static final String CONFIG_LETTUCE_POOL_IDLE_RANGE_INVALID =
            "数据源 [%s] 的 lettuce.pool.min-idle 不能大于 max-idle，当前值: min-idle=%d, max-idle=%d";
    public static final String CONFIG_LETTUCE_POOL_MAX_WAIT_INVALID =
            "数据源 [%s] 的 lettuce.pool.max-wait-ms 不能小于 -1，当前值: %d";
    public static final String CONFIG_CLUSTER_TOPOLOGY_ADDRESS_FOLLOW_NODES_STANDALONE =
            "standalone 数据源 [%s] 不支持配置 cluster-topology-address-follow-nodes";
    public static final String CONFIG_CLUSTER_TOPOLOGY_ADDRESS_FOLLOW_NODES_MAPPING_INVALID =
            "cluster 数据源 [%s] 的 cluster-topology-address-follow-nodes 无法从 nodes 建立唯一 hostname 地址映射";
    public static final String DATASOURCE_NOT_FOUND = "Redis 数据源 [%s] 不存在，已配置的数据源: %s";
    public static final String DATASOURCE_CREATE_FAILED = "创建 Redis 数据源 [%s] 失败";
    public static final String ROUTE_KEY_EMPTY = "route key 不能为空";
    public static final String ROUTE_CROSS_DATASOURCE = "多 key 路由到不同 Redis 数据源，datasources=%s，keys=%s";
    public static final String CALLBACK_EMPTY = "Redis route callback 不能为空";

    /**
     * probe.server-info=false 时主动跳过探测的消息
     */
    public static final String PROBE_DISABLED = "probe.server-info=false，已跳过 Redis Server 信息探测";

    /**
     * probe 执行异常时使用的脱敏说明
     */
    public static final String PROBE_FAILED = "探测 Redis Server 信息失败";

    /**
     * 版本号字符串格式不合法
     */
    public static final String SERVER_VERSION_INVALID = "Redis Server 版本号格式不合法: [%s]";

    /**
     * 能力不满足要求
     */
    public static final String CAPABILITY_NOT_SATISFIED = "Redis 数据源 [%s] 不满足能力要求: [%s]，Server version=[%s]";
    public static final String TOPOLOGY_ADDRESS_RESOLVER_UNAVAILABLE = "Redis Cluster 拓扑地址解析器不可用";
    public static final String TOPOLOGY_ADDRESS_MAPPING_AMBIGUOUS = "Redis Cluster nodes 地址映射存在歧义";
    public static final String TOPOLOGY_ADDRESS_MAPPING_MISSING = "Redis Cluster nodes 未包含可用 hostname 地址映射";
    public static final String TOPOLOGY_ADDRESS_MAPPING_FAILED = "Redis Cluster 拓扑节点地址映射失败";
    public static final String LETTUCE_SOCKET_ADDRESS_RESOLVER_UNSUPPORTED = "当前 Lettuce 不支持节点地址解析器扩展";
    public static final String LETTUCE_CLIENT_RESOURCES_UNSUPPORTED = "当前 Spring Data Redis 不支持 Lettuce ClientResources 扩展";
    public static final String LETTUCE_CLIENT_OPTIONS_UNSUPPORTED = "当前 Spring Data Redis 不支持 Route 所需 Lettuce client options 配置";
    public static final String LETTUCE_READ_FROM_UNSUPPORTED = "当前 Spring Data Redis 不支持 Lettuce Cluster read-from 配置";
    public static final String REDIS_CONNECTION_FACTORY_CONFLICT =
            "Redis Route 启用时只允许 Registry 创建 RedisConnectionFactory，冲突 Bean: [%s]";
    public static final String REDIS_TEMPLATE_CONNECTION_FACTORY_MISMATCH =
            "Redis Route 启用时 RedisTemplate 必须绑定 default-source RedisConnectionFactory，冲突 Bean: [%s]";

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }
}
