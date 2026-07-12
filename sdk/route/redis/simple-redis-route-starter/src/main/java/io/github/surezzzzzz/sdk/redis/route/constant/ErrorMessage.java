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
    public static final String CONFIG_PORT_INVALID = "数据源 [%s] 的 port 必须在 1~65535 范围内，当前值: %d";
    public static final String CONFIG_DATABASE_INVALID = "数据源 [%s] 的 database 不能小于 0，当前值: %d";
    public static final String CONFIG_CLUSTER_DATABASE_INVALID = "cluster 数据源 [%s] 的 database 必须为 0，当前值: %d";
    public static final String CONFIG_CLUSTER_NODES_EMPTY = "cluster 数据源 [%s] 的 nodes 不能为空";
    public static final String CONFIG_CLUSTER_MAX_REDIRECTS_INVALID = "cluster 数据源 [%s] 的 maxRedirects 不能小于 0，当前值: %d";
    public static final String CONFIG_NODE_INVALID = "数据源 [%s] 的节点 [%s] 不是合法 host:port 格式";
    public static final String CONFIG_ROUTE_PATTERN_EMPTY = "第 %d 条路由规则 pattern 不能为空";
    public static final String CONFIG_ROUTE_DATASOURCE_NOT_FOUND = "第 %d 条路由规则引用的数据源 [%s] 不存在，pattern=[%s]，type=[%s]，已配置的数据源: %s";
    public static final String CONFIG_ROUTE_TYPE_INVALID = "第 %d 条路由规则 type [%s] 无效，pattern=[%s]，datasource=[%s]，有效值: %s";
    public static final String CONFIG_ROUTE_REGEX_INVALID = "第 %d 条路由规则 regex 编译失败，pattern=[%s]，type=[%s]，datasource=[%s]";
    public static final String CONFIG_TIMEOUT_INVALID = "数据源 [%s] 的 timeout-ms / connect-timeout-ms / lettuce.shutdown-timeout-ms 必须大于 0";
    public static final String CONFIG_LETTUCE_REQUEST_QUEUE_SIZE_INVALID = "数据源 [%s] 的 lettuce.request-queue-size 必须大于 0，当前值: %d";
    public static final String CONFIG_CLUSTER_REFRESH_PERIOD_INVALID = "数据源 [%s] 的 lettuce.cluster-refresh-period-ms 必须大于 0，当前值: %d";
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
     * probe 执行时发生异常，sanitized 描述
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

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }
}
