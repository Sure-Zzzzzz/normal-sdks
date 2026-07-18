package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

/**
 * SmartRedisLimiter 错误码常量
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ==================== 配置错误 ====================

    /**
     * RedisRouteTemplate 缺失
     */
    public static final String CONFIG_REDIS_ROUTE_TEMPLATE_MISSING = "CONFIG_001";

    // ==================== 路由错误 ====================

    /**
     * Redis Route 执行失败
     */
    public static final String ROUTE_EXECUTION_FAILED = "ROUTE_001";

    // ==================== Key 生成错误 ====================

    /**
     * KeyProvider 执行失败
     */
    public static final String KEY_PROVIDER_ERROR = "KEY_001";

    /**
     * KeyGenerator 未找到
     */
    public static final String KEY_GENERATOR_NOT_FOUND = "KEY_002";

    /**
     * Key 片段为空
     */
    public static final String KEY_PART_INVALID = "KEY_003";

    /**
     * 客户端 IP 缺失
     */
    public static final String KEY_PART_CLIENT_IP_MISSING = "KEY_004";

    /**
     * 请求路径缺失
     */
    public static final String KEY_PART_REQUEST_PATH_MISSING = "KEY_005";

    /**
     * HTTP 方法缺失
     */
    public static final String KEY_PART_METHOD_MISSING = "KEY_006";

    /**
     * 路径模式缺失
     */
    public static final String KEY_PART_PATH_PATTERN_MISSING = "KEY_007";

    // ==================== 策略协议校验错误 ====================

    /**
     * 策略键非法
     */
    public static final String POLICY_KEY_INVALID = "VALIDATION_001";

    /**
     * 时间单位非法
     */
    public static final String POLICY_TIME_UNIT_INVALID = "VALIDATION_002";

    /**
     * 限额窗口非法
     */
    public static final String POLICY_LIMIT_INVALID = "VALIDATION_003";

    /**
     * 时间窗口换算溢出
     */
    public static final String POLICY_WINDOW_OVERFLOW = "VALIDATION_004";

    /**
     * 动态策略非法
     */
    public static final String POLICY_INVALID = "VALIDATION_005";

    /**
     * 动态策略窗口重复
     */
    public static final String POLICY_DUPLICATE_WINDOW = "VALIDATION_006";

    /**
     * 策略快照非法
     */
    public static final String POLICY_SNAPSHOT_INVALID = "VALIDATION_007";

    /**
     * 快照内策略服务不匹配
     */
    public static final String POLICY_SNAPSHOT_SERVICE_MISMATCH = "VALIDATION_008";

    /**
     * 快照内策略键重复
     */
    public static final String POLICY_DUPLICATE_KEY = "VALIDATION_009";

    /**
     * 管理事件载荷非法
     */
    public static final String MANAGEMENT_PAYLOAD_INVALID = "VALIDATION_010";

    /**
     * 执行策略上下文非法
     */
    public static final String EXECUTION_POLICY_CONTEXT_INVALID = "VALIDATION_011";

    /**
     * 扩展属性值非法
     */
    public static final String ATTRIBUTE_VALUE_INVALID = "VALIDATION_012";

    /**
     * 执行事件载荷非法
     */
    public static final String EXECUTION_EVENT_PAYLOAD_INVALID = "VALIDATION_013";

    // ==================== 限流错误 ====================

    /**
     * 限流超限
     */
    public static final String RATE_LIMIT_EXCEEDED = "BIZ_001";
}
