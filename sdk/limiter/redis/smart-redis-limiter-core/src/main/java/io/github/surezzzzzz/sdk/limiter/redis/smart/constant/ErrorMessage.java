package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

/**
 * SmartRedisLimiter 错误消息常量
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置错误 ====================

    /**
     * RedisRouteTemplate 缺失
     */
    public static final String CONFIG_REDIS_ROUTE_TEMPLATE_MISSING =
            "RedisRouteTemplate 不存在，请确认 simple-redis-route-starter 已引入且 io.github.surezzzzzz.sdk.redis.route.enable=true";

    // ==================== 路由错误 ====================

    /**
     * Redis Route 执行失败
     */
    public static final String ROUTE_EXECUTION_FAILED = "Redis Route 执行失败：%s";

    // ==================== Key 生成错误 ====================

    /**
     * KeyProvider 执行失败
     */
    public static final String KEY_PROVIDER_ERROR = "SmartRedisLimiter KeyProvider 执行失败：%s";

    /**
     * KeyGenerator 未找到
     */
    public static final String KEY_GENERATOR_NOT_FOUND = "未找到 KeyGenerator：%s";

    /**
     * KeyGenerator 未找到消息前缀，兼容 1.x 常量拼接用法
     */
    public static final String KEY_GENERATOR_NOT_FOUND_PREFIX = "未找到KeyGenerator: ";

    /**
     * Key 片段非法
     */
    public static final String KEY_PART_INVALID = "keyPart 不能为空";

    /**
     * 客户端 IP 缺失
     */
    public static final String KEY_PART_CLIENT_IP_MISSING = "ClientIp不能为null";

    /**
     * 请求路径缺失
     */
    public static final String KEY_PART_REQUEST_PATH_MISSING = "RequestPath不能为null";

    /**
     * HTTP 方法缺失
     */
    public static final String KEY_PART_METHOD_MISSING = "Method不能为null";

    /**
     * 路径模式缺失
     */
    public static final String KEY_PART_PATH_PATTERN_MISSING = "RequestPath和MatchedPathPattern都为null";

    /**
     * keyPart 为空
     *
     * @deprecated 使用 {@link #KEY_PART_INVALID}
     */
    @Deprecated
    public static final String KEY_PART_EMPTY = KEY_PART_INVALID;

    /**
     * ClientIp 为空
     *
     * @deprecated 使用 {@link #KEY_PART_CLIENT_IP_MISSING}
     */
    @Deprecated
    public static final String CLIENT_IP_NULL = KEY_PART_CLIENT_IP_MISSING;

    /**
     * RequestPath 为空
     *
     * @deprecated 使用 {@link #KEY_PART_REQUEST_PATH_MISSING}
     */
    @Deprecated
    public static final String REQUEST_PATH_NULL = KEY_PART_REQUEST_PATH_MISSING;

    /**
     * Method 为空
     *
     * @deprecated 使用 {@link #KEY_PART_METHOD_MISSING}
     */
    @Deprecated
    public static final String METHOD_NULL = KEY_PART_METHOD_MISSING;

    /**
     * RequestPath 和 MatchedPathPattern 都为空
     *
     * @deprecated 使用 {@link #KEY_PART_PATH_PATTERN_MISSING}
     */
    @Deprecated
    public static final String PATH_PATTERN_NULL = KEY_PART_PATH_PATTERN_MISSING;

    // ==================== 策略协议校验原因 ====================

    /**
     * 必填字段为空
     */
    public static final String REASON_FIELD_REQUIRED = "不能为空";

    /**
     * 字段必须大于零
     */
    public static final String REASON_FIELD_MUST_BE_POSITIVE = "必须大于0";

    /**
     * 字段超过最大长度
     */
    public static final String REASON_FIELD_MAX_LENGTH_EXCEEDED = "长度不能超过%d";

    /**
     * 字段包含控制字符
     */
    public static final String REASON_FIELD_CONTROL_CHARACTER = "不能包含控制字符";

    /**
     * 字段不符合稳定编码格式
     */
    public static final String REASON_FIELD_STABLE_CODE_INVALID = "格式不符合稳定编码规范";

    /**
     * 操作人字段非法
     */
    public static final String REASON_OPERATOR_INVALID = "operator非法";

    /**
     * 策略键为空
     */
    public static final String REASON_POLICY_KEY_REQUIRED = "策略键不能为空";

    /**
     * 限额窗口为空
     */
    public static final String REASON_POLICY_LIMIT_REQUIRED = "限额窗口不能为空";

    /**
     * 限额窗口超过最大数量
     */
    public static final String REASON_POLICY_LIMIT_MAX_EXCEEDED = "限额窗口数量不能超过%d";

    /**
     * 限额窗口项为空
     */
    public static final String REASON_POLICY_LIMIT_ITEM_REQUIRED = "限额窗口项不能为空";

    /**
     * 快照协议版本不受支持
     */
    public static final String REASON_SNAPSHOT_SCHEMA_UNSUPPORTED = "不支持的协议版本";

    /**
     * revision 为空
     */
    public static final String REASON_REVISION_REQUIRED = "revision不能为空";

    /**
     * revision 小于零
     */
    public static final String REASON_REVISION_NEGATIVE = "revision不能小于0";

    /**
     * 快照发布时间为空
     */
    public static final String REASON_PUBLISHED_AT_REQUIRED = "publishedAt不能为空";

    /**
     * 快照策略列表为空
     */
    public static final String REASON_POLICIES_REQUIRED = "policies不能为空";

    /**
     * 快照策略项为空
     */
    public static final String REASON_POLICY_ITEM_REQUIRED = "策略项不能为空";

    /**
     * 管理操作为空
     */
    public static final String REASON_OPERATION_REQUIRED = "operation不能为空";

    /**
     * 管理事件策略键为空
     */
    public static final String REASON_MANAGEMENT_POLICY_KEY_REQUIRED = "policyKey不能为空";

    /**
     * 管理事件发生时间为空
     */
    public static final String REASON_OCCURRED_AT_REQUIRED = "occurredAt不能为空";

    /**
     * 变更前策略键不匹配
     */
    public static final String REASON_BEFORE_POLICY_KEY_MISMATCH = "beforePolicy的策略键不匹配";

    /**
     * 变更后策略键不匹配
     */
    public static final String REASON_AFTER_POLICY_KEY_MISMATCH = "afterPolicy的策略键不匹配";

    /**
     * CREATE 操作载荷非法
     */
    public static final String REASON_CREATE_PAYLOAD_INVALID = "CREATE必须只包含变更后的策略和状态";

    /**
     * UPDATE 操作载荷非法
     */
    public static final String REASON_UPDATE_PAYLOAD_INVALID = "UPDATE必须包含前后策略且启用状态不能变化";

    /**
     * ENABLE 操作载荷非法
     */
    public static final String REASON_ENABLE_PAYLOAD_INVALID = "ENABLE必须表达false到true的状态变化";

    /**
     * DISABLE 操作载荷非法
     */
    public static final String REASON_DISABLE_PAYLOAD_INVALID = "DISABLE必须表达true到false的状态变化";

    /**
     * DELETE 操作载荷非法
     */
    public static final String REASON_DELETE_PAYLOAD_INVALID = "DELETE必须只包含变更前的策略和状态";

    /**
     * 管理操作不受支持
     */
    public static final String REASON_MANAGEMENT_OPERATION_UNSUPPORTED = "不支持的管理操作";

    /**
     * 管理事件载荷为空
     */
    public static final String REASON_MANAGEMENT_PAYLOAD_REQUIRED = "payload不能为空";

    /**
     * 扩展属性值类型不受支持
     */
    public static final String REASON_ATTRIBUTE_TYPE_UNSUPPORTED = "仅支持JSON基础值、Map、List、Set和对象数组";

    /**
     * 执行事件载荷为空
     */
    public static final String REASON_EXECUTION_PAYLOAD_REQUIRED = "payload不能为空";

    /**
     * 本地策略携带远程版本
     */
    public static final String REASON_LOCAL_POLICY_REVISION_FORBIDDEN = "本地策略不能设置policyRevision";

    /**
     * 远程策略缺少资源编码
     */
    public static final String REASON_REMOTE_RESOURCE_CODE_REQUIRED = "远程策略必须设置resourceCode";

    /**
     * 远程策略版本非法
     */
    public static final String REASON_REMOTE_POLICY_REVISION_INVALID = "远程策略必须设置非负policyRevision";

    /**
     * 策略来源不受支持
     */
    public static final String REASON_POLICY_SOURCE_UNSUPPORTED = "不支持的policySource";

    // ==================== 策略协议校验错误 ====================

    /**
     * 策略键字段非法
     */
    public static final String POLICY_KEY_INVALID = "动态策略字段非法：field=%s，reason=%s";

    /**
     * 时间单位非法
     */
    public static final String POLICY_TIME_UNIT_INVALID = "动态策略时间单位非法";

    /**
     * 限额窗口非法
     */
    public static final String POLICY_LIMIT_INVALID = "动态策略限额非法：field=%s，reason=%s";

    /**
     * 时间窗口换算溢出
     */
    public static final String POLICY_WINDOW_OVERFLOW = "动态策略时间窗口换算溢出";

    /**
     * 动态策略非法
     */
    public static final String POLICY_INVALID = "动态策略非法：%s";

    /**
     * 动态策略窗口重复
     */
    public static final String POLICY_DUPLICATE_WINDOW = "动态策略存在重复的标准化时间窗口：%d秒";

    /**
     * 策略快照非法
     */
    public static final String POLICY_SNAPSHOT_INVALID = "动态策略快照非法：%s";

    /**
     * 快照内策略服务不匹配
     */
    public static final String POLICY_SNAPSHOT_SERVICE_MISMATCH = "动态策略快照中的策略服务不匹配";

    /**
     * 快照内策略键重复
     */
    public static final String POLICY_DUPLICATE_KEY = "动态策略快照中存在重复策略键";

    /**
     * 管理事件载荷非法
     */
    public static final String MANAGEMENT_PAYLOAD_INVALID = "动态策略管理事件载荷非法：%s";

    /**
     * 扩展属性值非法
     */
    public static final String ATTRIBUTE_VALUE_INVALID = "扩展属性值非法：%s";

    /**
     * 执行事件载荷非法
     */
    public static final String EXECUTION_EVENT_PAYLOAD_INVALID = "限流执行事件载荷非法：%s";

    /**
     * 执行策略上下文非法
     */
    public static final String EXECUTION_POLICY_CONTEXT_INVALID = "动态策略执行上下文非法：%s";

    // ==================== 限流错误 ====================

    /**
     * 限流超限
     */
    public static final String RATE_LIMIT_EXCEEDED = "Rate limit exceeded for key: %s, retry after %d seconds";
}
