package io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter;

/**
 * SmartRedisLimiter Starter 错误码
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * 配置验证失败
     */
    public static final String CONFIG_VALIDATION_FAILED = "CONFIG_002";

    // ==================== 配置错误 ====================
    /**
     * KeyProvider Bean 不存在或类型不匹配
     */
    public static final String KEY_PROVIDER_INVALID = "CONFIG_003";
    /**
     * KeyProvider 缓存未命中
     */
    public static final String KEY_PROVIDER_CACHE_MISSING = "CONFIG_004";
    /**
     * 远程策略快照校验失败
     */
    public static final String POLICY_SNAPSHOT_INVALID = "POLICY_001";

    // ==================== 远程策略错误 ====================
    /**
     * 远程策略版本冲突
     */
    public static final String POLICY_REVISION_CONFLICT = "POLICY_002";
    /**
     * 摘要计算失败
     */
    public static final String POLICY_DIGEST_FAILED = "POLICY_003";
    /**
     * 远程策略请求失败
     */
    public static final String POLICY_FETCH_FAILED = "POLICY_004";
    /**
     * 远程策略响应非法
     */
    public static final String POLICY_RESPONSE_INVALID = "POLICY_005";
    /**
     * 远程策略 JSON 非法
     */
    public static final String POLICY_JSON_INVALID = "POLICY_006";
    /**
     * Lua 脚本执行失败
     */
    public static final String SCRIPT_EXECUTION_FAILED = "SCRIPT_001";

    // ==================== 脚本错误 ====================
    /**
     * Lua 返回字段非法
     */
    public static final String SCRIPT_RESULT_FIELD_INVALID = "SCRIPT_002";
    /**
     * 固定窗口 Lua 返回结构非法
     */
    public static final String FIXED_WINDOW_SCRIPT_RESULT_INVALID = "SCRIPT_003";
    /**
     * 滑动窗口 Lua 返回结构非法
     */
    public static final String SLIDING_WINDOW_SCRIPT_RESULT_INVALID = "SCRIPT_004";

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class");
    }
}
