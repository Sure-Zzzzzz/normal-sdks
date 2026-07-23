package io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter;

/**
 * SmartRedisLimiter Starter 错误消息
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * 配置验证失败消息模板
     */
    public static final String CONFIG_VALIDATION_FAILED =
            "SmartRedisLimiter 配置验证失败，请检查配置文件：%s";

    // ==================== 配置错误 ====================
    /**
     * 服务标识为空
     */
    public static final String CONFIG_ME_REQUIRED = "配置项 '%s' 不能为空，请设置服务标识（如：test-service）";
    /**
     * 服务标识非法
     */
    public static final String CONFIG_ME_INVALID = "配置项 '%s' 不符合服务编码规范：%s";
    /**
     * 限流模式非法
     */
    public static final String CONFIG_MODE_INVALID = "%s 值非法：%s，有效值：%s";
    /**
     * 配置项为空
     */
    public static final String CONFIG_ITEM_REQUIRED = "%s 不能为空";
    /**
     * 配置项必须大于零
     */
    public static final String CONFIG_ITEM_MUST_BE_POSITIVE = "%s 必须大于0，当前值：%d";
    /**
     * 配置项超过最大值
     */
    public static final String CONFIG_ITEM_MAX_EXCEEDED = "%s 不能超过%d，当前值：%d";
    /**
     * 限流算法非法
     */
    public static final String CONFIG_ALGORITHM_INVALID = "%s 值非法：%s，有效值：%s, %s";
    /**
     * 枚举型配置非法
     */
    public static final String CONFIG_ENUM_VALUE_INVALID = "%s 值非法：%s，有效值：%s";
    /**
     * 限额窗口数量超过 core 上限
     */
    public static final String CONFIG_LIMIT_COUNT_EXCEEDED = "%s 窗口数量不能超过%d，当前数量：%d";
    /**
     * 限额窗口为空
     */
    public static final String CONFIG_LIMIT_ITEM_REQUIRED = "%s 不能为空";
    /**
     * 限额窗口非法
     */
    public static final String CONFIG_LIMIT_INVALID = "%s 非法：%s";
    /**
     * 限额窗口重复
     */
    public static final String CONFIG_LIMIT_DUPLICATE_WINDOW = "%s 存在等价重复窗口：%d秒";
    /**
     * Lua 安全整数越界
     */
    public static final String CONFIG_LUA_SAFE_INTEGER_EXCEEDED =
            "%s 超过 Lua 安全整数上限%d，当前值：%d";
    /**
     * 资源编码非法
     */
    public static final String CONFIG_RESOURCE_CODE_INVALID = "%s 不符合资源编码规范：%s";
    /**
     * Redis 命令超时为空
     */
    public static final String CONFIG_COMMAND_TIMEOUT_REQUIRED = "%s 不能为空";
    /**
     * Redis 命令超时非法
     */
    public static final String CONFIG_COMMAND_TIMEOUT_MUST_BE_POSITIVE = "%s 必须大于0，当前值：%d";
    /**
     * 超时保护线程数非法
     */
    public static final String CONFIG_TIMEOUT_EXECUTOR_THREADS_MUST_BE_POSITIVE = "%s 必须大于0";
    /**
     * 超时保护队列容量非法
     */
    public static final String CONFIG_TIMEOUT_EXECUTOR_QUEUE_CAPACITY_MUST_BE_POSITIVE = "%s 必须大于0";
    /**
     * 远程策略快照地址非法
     */
    public static final String CONFIG_REMOTE_POLICY_URL_INVALID =
            "%s 必须是绝对 http/https URL，且不能包含 user-info、query 或 fragment，当前值：%s";
    /**
     * 远程策略单策略窗口上限非法
     */
    public static final String CONFIG_REMOTE_POLICY_MAX_LIMITS_EXCEEDED =
            "%s 不能超过 core 上限%d，当前值：%d";
    /**
     * KeyProvider Bean 不存在或类型不匹配消息模板
     */
    public static final String KEY_PROVIDER_INVALID =
            "KeyProvider Bean 不存在或类型不匹配: name=%s, rule.pathPattern=%s, rule.method=%s";
    /**
     * KeyProvider 缓存未命中消息模板
     */
    public static final String KEY_PROVIDER_CACHE_MISSING = "KeyProvider 缓存未命中: %s";
    /**
     * 远程策略快照校验失败
     */
    public static final String POLICY_SNAPSHOT_INVALID = "远程策略快照校验失败：%s";

    // ==================== 远程策略错误 ====================
    /**
     * 策略数量超过配置上限
     */
    public static final String POLICY_COUNT_EXCEEDED = "策略数量不能超过%d，当前数量：%d";
    /**
     * 单策略窗口数量超过配置上限
     */
    public static final String POLICY_LIMIT_COUNT_EXCEEDED =
            "单策略窗口数量不能超过%d，当前数量：%d";
    /**
     * 快照服务编码不匹配
     */
    public static final String POLICY_SERVICE_CODE_MISMATCH =
            "快照服务编码与本服务不匹配，期望：%s，实际：%s";
    /**
     * Lua 数值超过安全整数上限
     */
    public static final String POLICY_LUA_SAFE_INTEGER_EXCEEDED =
            "%s 超过 Lua 安全整数上限%d，当前值：%d";
    /**
     * 快照版本发生回退
     */
    public static final String POLICY_REVISION_ROLLBACK =
            "快照版本不能回退，当前版本：%d，收到版本：%d";
    /**
     * 同版本快照内容发生漂移
     */
    public static final String POLICY_SAME_REVISION_DRIFT =
            "同版本快照的 ETag 或内容摘要发生变化，版本：%d";
    /**
     * 新版本复用了旧 ETag
     */
    public static final String POLICY_ETAG_REUSED =
            "新版本快照不能复用旧 ETag，当前版本：%d，收到版本：%d";
    /**
     * ETag 非法
     */
    public static final String POLICY_ETAG_INVALID = "ETag 必须是合法的强标签或弱标签：%s";
    /**
     * 摘要计算失败
     */
    public static final String POLICY_DIGEST_FAILED = "远程策略 canonical 摘要计算失败";
    /**
     * 远程策略请求失败
     */
    public static final String POLICY_FETCH_FAILED = "远程策略请求失败：%s";
    /**
     * 远程策略 HTTP 状态非法
     */
    public static final String POLICY_HTTP_STATUS_INVALID = "远程策略响应状态非法：%d";
    /**
     * 首次请求返回 304
     */
    public static final String POLICY_INITIAL_NOT_MODIFIED = "尚无已接受快照时不能接受 304 响应";
    /**
     * 200 响应缺少 ETag
     */
    public static final String POLICY_ETAG_REQUIRED = "远程策略 200 响应必须携带 ETag";
    /**
     * 响应体超过配置上限
     */
    public static final String POLICY_RESPONSE_TOO_LARGE =
            "远程策略响应体超过最大字节数%d";
    /**
     * 远程策略 JSON 非法
     */
    public static final String POLICY_JSON_INVALID = "远程策略 JSON 解析失败：%s";
    /**
     * 刷新失败分类
     */
    public static final String POLICY_REFRESH_FAILURE_REASON = "%s:%s";
    /**
     * Lua 脚本执行失败消息
     */
    public static final String SCRIPT_EXECUTION_FAILED = "SmartRedisLimiter 脚本执行失败：%s";

    // ==================== 脚本错误 ====================
    /**
     * Lua 返回字段非法消息模板
     */
    public static final String SCRIPT_RESULT_FIELD_INVALID = "Lua 返回字段无法解析：%s";
    /**
     * 固定窗口 Lua 返回结构非法
     */
    public static final String FIXED_WINDOW_SCRIPT_RESULT_INVALID = "固定窗口限流脚本返回异常";
    /**
     * 滑动窗口 Lua 返回结构非法
     */
    public static final String SLIDING_WINDOW_SCRIPT_RESULT_INVALID = "滑动窗口限流脚本返回异常";

    private ErrorMessage() {
        throw new UnsupportedOperationException("Utility class");
    }
}
