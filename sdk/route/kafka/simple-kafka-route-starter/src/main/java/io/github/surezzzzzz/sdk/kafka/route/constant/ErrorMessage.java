package io.github.surezzzzzz.sdk.kafka.route.constant;

/**
 * 错误消息常量
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    public static final String CONFIG_SOURCES_EMPTY = "配置项 'sources' 不能为空，至少需要配置一个 Kafka datasource";
    public static final String CONFIG_DEFAULT_SOURCE_NOT_FOUND = "默认 datasource [%s] 不存在，已配置 datasource: %s";
    public static final String CONFIG_DATASOURCE_KEY_EMPTY = "Kafka datasource key 不能为空";
    public static final String CONFIG_BOOTSTRAP_SERVERS_EMPTY = "datasource [%s] 的 bootstrap-servers 不能为空";
    public static final String CONFIG_BOOTSTRAP_SERVER_INVALID = "datasource [%s] 的 bootstrap server [%s] 不是合法 host:port 格式";
    public static final String CONFIG_RESERVED_PROPERTY_KEY = "datasource [%s] 的 raw properties 包含保留 key [%s]，请使用 route typed field 配置";
    public static final String CONFIG_PRODUCER_INVALID = "datasource [%s] 的 producer 配置非法，字段: %s";
    public static final String CONFIG_CONSUMER_INVALID = "datasource [%s] 的 consumer 配置非法，字段: %s";
    public static final String CONFIG_SERIALIZER_INVALID = "datasource [%s] 的 serializer 配置非法，字段: %s";
    public static final String CONFIG_DESERIALIZER_INVALID = "datasource [%s] 的 deserializer 配置非法，字段: %s";
    public static final String CONFIG_SECURITY_INVALID = "datasource [%s] 的 Kafka security 配置非法，字段: %s";
    public static final String CONFIG_ROUTE_PATTERN_EMPTY = "第 %d 条路由规则 pattern 不能为空";
    public static final String CONFIG_ROUTE_DATASOURCE_NOT_FOUND = "第 %d 条路由规则引用的 datasource [%s] 不存在，pattern=[%s]，type=[%s]，已配置 datasource: %s";
    public static final String CONFIG_ROUTE_TYPE_INVALID = "第 %d 条路由规则 type [%s] 无效，pattern=[%s]，datasource=[%s]，有效值: %s";
    public static final String CONFIG_ROUTE_REGEX_INVALID = "第 %d 条路由规则 regex 编译失败，pattern=[%s]，type=[%s]，datasource=[%s]";
    public static final String DATASOURCE_NOT_FOUND = "Kafka datasource [%s] 不存在，已配置 datasource: %s";
    public static final String DATASOURCE_CREATE_FAILED = "创建 Kafka datasource [%s] 失败";
    public static final String ROUTE_INPUT_EMPTY = "topic / route key 不能为空";
    public static final String RECORD_INVALID = "Kafka route record 参数非法：%s";
    public static final String CALLBACK_EMPTY = "Kafka route callback 不能为空";
    public static final String DIAGNOSTICS_FAIL_FAST = "Kafka route broker 诊断失败，fail-fast 已开启，失败 datasource: %s";
    public static final String COMPAT_REFLECT_CLASS_NOT_FOUND = "Kafka route 兼容层未找到 class: %s";
    public static final String COMPAT_REFLECT_METHOD_NOT_FOUND = "Kafka route 兼容层未找到 method: %s#%s";
    public static final String DIAGNOSTICS_DESCRIBE_CLUSTER_EMPTY = "describeCluster 返回空结果";
    public static final String CONSUMER_FACTORY_OVERRIDE_UNSUPPORTED =
            "自定义 Kafka ConsumerFactoryFactory 未实现 consumer 覆盖配置：datasource [%s]";
    public static final String REGISTRY_DESTROYED =
            "Kafka route registry 已关闭，不能创建派生 ConsumerFactory：datasource [%s]";

    private ErrorMessage() {
        throw new UnsupportedOperationException(SimpleKafkaRouteConstant.EXCEPTION_MESSAGE_UTILITY_CLASS);
    }
}
