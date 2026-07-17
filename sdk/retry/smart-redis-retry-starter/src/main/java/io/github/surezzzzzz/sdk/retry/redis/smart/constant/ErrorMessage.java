package io.github.surezzzzzz.sdk.retry.redis.smart.constant;

/**
 * Smart Redis Retry 错误消息
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * 重试类型不能为空
     */
    public static final String RETRY_TYPE_EMPTY = "retryType 不能为空";
    /**
     * 重试标识不能为空
     */
    public static final String RETRY_KEY_EMPTY = "retryKey 不能为空";
    /**
     * 重试标识长度超限
     */
    public static final String RETRY_KEY_TOO_LONG = "retryKey 长度不能超过 %s";
    /**
     * 路由 Key 不能为空
     */
    public static final String ROUTE_KEY_EMPTY = "routeKey 不能为空";
    /**
     * 重试策略配置非法
     */
    public static final String RETRY_POLICY_INVALID = "重试策略配置非法";
    /**
     * 重试上下文长度超限
     */
    public static final String RETRY_CONTEXT_TOO_LONG = "context 序列化后长度不能超过 %s";
    /**
     * Redis 失败处理策略非法
     */
    public static final String REDIS_FAILURE_STRATEGY_INVALID = "redisFailureStrategy 非法，可选值为 %s";
    /**
     * 重试记录 TTL 非法
     */
    public static final String RECORD_TTL_INVALID = "recordTtlSeconds 必须大于 0";
    /**
     * 重试记录 TTL 超出毫秒转换范围
     */
    public static final String RECORD_TTL_TOO_LARGE = "recordTtlSeconds 超出可转换为毫秒的最大值";
    /**
     * Redis 操作失败
     */
    public static final String REDIS_OPERATION_FAILED = "Redis 操作失败";
    /**
     * Lua 脚本执行失败
     */
    public static final String LUA_SCRIPT_EXECUTION_FAILED = "Lua 脚本执行失败";
    /**
     * Lua 脚本返回结果非法
     */
    public static final String LUA_SCRIPT_RESULT_INVALID = "Lua 脚本返回结果非法";
    /**
     * Lua 脚本返回结果转换失败
     */
    public static final String LUA_SCRIPT_RESULT_CONVERT_FAILED = "Lua 脚本返回结果转换失败";
    /**
     * 重试标识摘要计算失败
     */
    public static final String RETRY_KEY_DIGEST_FAILED = "retryKey 摘要计算失败";
    /**
     * 上下文序列化失败
     */
    public static final String JSON_SERIALIZE_FAILED = "context 序列化失败";
    /**
     * 上下文反序列化失败
     */
    public static final String JSON_DESERIALIZE_FAILED = "context 反序列化失败";
    /**
     * 扫描数量非法
     */
    public static final String SCAN_COUNT_INVALID = "scan count 必须大于 0";
    /**
     * 扫描游标非法
     */
    public static final String SCAN_CURSOR_INVALID = "scan cursor 非法";
    /**
     * 重试标识最大长度配置非法
     */
    public static final String MAX_RETRY_KEY_LENGTH_INVALID = "maxRetryKeyLength 必须大于 0";
    /**
     * 上下文最大长度配置非法
     */
    public static final String MAX_CONTEXT_JSON_LENGTH_INVALID = "maxContextJsonLength 必须大于 0";

    /**
     * 禁止实例化错误消息常量类
     */
    private ErrorMessage() {
        throw new UnsupportedOperationException(SmartRedisRetryConstant.UTILITY_CLASS_EXCEPTION_MESSAGE);
    }
}
