package io.github.surezzzzzz.sdk.retry.redis.smart.constant;

/**
 * Smart Redis Retry 错误码
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * 重试类型不能为空
     */
    public static final String RETRY_TYPE_EMPTY = "SMART_REDIS_RETRY_001";
    /**
     * 重试标识不能为空
     */
    public static final String RETRY_KEY_EMPTY = "SMART_REDIS_RETRY_002";
    /**
     * 重试标识长度超限
     */
    public static final String RETRY_KEY_TOO_LONG = "SMART_REDIS_RETRY_003";
    /**
     * 路由 Key 不能为空
     */
    public static final String ROUTE_KEY_EMPTY = "SMART_REDIS_RETRY_004";
    /**
     * 重试策略配置非法
     */
    public static final String RETRY_POLICY_INVALID = "SMART_REDIS_RETRY_005";
    /**
     * 重试上下文长度超限
     */
    public static final String RETRY_CONTEXT_TOO_LONG = "SMART_REDIS_RETRY_006";
    /**
     * Redis 失败处理策略非法
     */
    public static final String REDIS_FAILURE_STRATEGY_INVALID = "SMART_REDIS_RETRY_007";
    /**
     * 重试记录 TTL 非法
     */
    public static final String RECORD_TTL_INVALID = "SMART_REDIS_RETRY_008";
    /**
     * 重试记录 TTL 超出毫秒转换范围
     */
    public static final String RECORD_TTL_TOO_LARGE = "SMART_REDIS_RETRY_009";
    /**
     * Redis 操作失败
     */
    public static final String REDIS_OPERATION_FAILED = "SMART_REDIS_RETRY_010";
    /**
     * Lua 脚本执行失败
     */
    public static final String LUA_SCRIPT_EXECUTION_FAILED = "SMART_REDIS_RETRY_011";
    /**
     * Lua 脚本返回结果非法
     */
    public static final String LUA_SCRIPT_RESULT_INVALID = "SMART_REDIS_RETRY_012";
    /**
     * Lua 脚本返回结果转换失败
     */
    public static final String LUA_SCRIPT_RESULT_CONVERT_FAILED = "SMART_REDIS_RETRY_013";
    /**
     * 重试标识摘要计算失败
     */
    public static final String RETRY_KEY_DIGEST_FAILED = "SMART_REDIS_RETRY_014";
    /**
     * 上下文序列化失败
     */
    public static final String JSON_SERIALIZE_FAILED = "SMART_REDIS_RETRY_015";
    /**
     * 上下文反序列化失败
     */
    public static final String JSON_DESERIALIZE_FAILED = "SMART_REDIS_RETRY_016";
    /**
     * 扫描数量非法
     */
    public static final String SCAN_COUNT_INVALID = "SMART_REDIS_RETRY_017";
    /**
     * 扫描游标非法
     */
    public static final String SCAN_CURSOR_INVALID = "SMART_REDIS_RETRY_018";
    /**
     * 重试标识最大长度配置非法
     */
    public static final String MAX_RETRY_KEY_LENGTH_INVALID = "SMART_REDIS_RETRY_019";
    /**
     * 上下文最大长度配置非法
     */
    public static final String MAX_CONTEXT_JSON_LENGTH_INVALID = "SMART_REDIS_RETRY_020";

    /**
     * 禁止实例化错误码常量类
     */
    private ErrorCode() {
        throw new UnsupportedOperationException(SmartRedisRetryConstant.UTILITY_CLASS_EXCEPTION_MESSAGE);
    }
}
