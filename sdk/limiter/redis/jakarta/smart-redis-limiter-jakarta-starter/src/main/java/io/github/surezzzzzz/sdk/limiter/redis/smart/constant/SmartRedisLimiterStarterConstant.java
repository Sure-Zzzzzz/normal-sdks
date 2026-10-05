package io.github.surezzzzzz.sdk.limiter.redis.smart.constant;

/**
 * SmartRedisLimiter Starter 常量
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterStarterConstant {

    /**
     * 默认空资源编码
     */
    public static final String DEFAULT_RESOURCE_CODE = "";

    // ==================== 通用值 ====================
    /**
     * SHA-256 摘要算法名
     */
    public static final String DIGEST_ALGORITHM_SHA_256 = "SHA-256";
    /**
     * 十六进制格式
     */
    public static final String FORMAT_HEX_BYTE = "%02x";
    /**
     * canonical 字段长度分隔符
     */
    public static final byte CANONICAL_LENGTH_SEPARATOR = (byte) ':';
    /**
     * ETag 弱标签前缀
     */
    public static final String ETAG_WEAK_PREFIX = "W/";
    /**
     * ETag 引号
     */
    public static final String ETAG_QUOTE = "\"";
    /**
     * 服务标识配置路径
     */
    public static final String CONFIG_PATH_ME = SmartRedisLimiterConstant.CONFIG_PREFIX + ".me";

    // ==================== 配置路径 ====================
    /**
     * 限流模式配置路径
     */
    public static final String CONFIG_PATH_MODE = SmartRedisLimiterConstant.CONFIG_PREFIX + ".mode";
    /**
     * 注解默认 Key 策略配置路径
     */
    public static final String CONFIG_PATH_ANNOTATION_DEFAULT_KEY_STRATEGY =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".annotation.default-key-strategy";
    /**
     * 注解默认限额配置路径
     */
    public static final String CONFIG_PATH_ANNOTATION_DEFAULT_LIMITS =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".annotation.default-limits";
    /**
     * 注解默认降级策略配置路径
     */
    public static final String CONFIG_PATH_ANNOTATION_DEFAULT_FALLBACK =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".annotation.default-fallback";
    /**
     * 拦截器默认 Key 策略配置路径
     */
    public static final String CONFIG_PATH_INTERCEPTOR_DEFAULT_KEY_STRATEGY =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".interceptor.default-key-strategy";
    /**
     * 拦截器默认限额配置路径
     */
    public static final String CONFIG_PATH_INTERCEPTOR_DEFAULT_LIMITS =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".interceptor.default-limits";
    /**
     * 拦截器默认降级策略配置路径
     */
    public static final String CONFIG_PATH_INTERCEPTOR_DEFAULT_FALLBACK =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".interceptor.default-fallback";
    /**
     * 拦截器规则配置路径
     */
    public static final String CONFIG_PATH_INTERCEPTOR_RULES =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".interceptor.rules";
    /**
     * Redis 命令超时配置路径
     */
    public static final String CONFIG_PATH_REDIS_COMMAND_TIMEOUT =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".redis.command-timeout";
    /**
     * Redis 超时执行器线程数配置路径
     */
    public static final String CONFIG_PATH_REDIS_TIMEOUT_EXECUTOR_THREADS =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".redis.timeout-executor-threads";
    /**
     * Redis 超时执行器队列容量配置路径
     */
    public static final String CONFIG_PATH_REDIS_TIMEOUT_EXECUTOR_QUEUE_CAPACITY =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".redis.timeout-executor-queue-capacity";
    /**
     * 全局降级策略配置路径
     */
    public static final String CONFIG_PATH_FALLBACK_ON_REDIS_ERROR =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".fallback.on-redis-error";
    /**
     * 带索引配置路径模板
     */
    public static final String TEMPLATE_CONFIG_INDEXED_PATH = "%s[%d]";
    /**
     * 子配置路径模板
     */
    public static final String TEMPLATE_CONFIG_CHILD_PATH = "%s.%s";
    /**
     * 配置字段名：路径模式
     */
    public static final String CONFIG_FIELD_PATH_PATTERN = "path-pattern";
    /**
     * 配置字段名：Key 策略
     */
    public static final String CONFIG_FIELD_KEY_STRATEGY = "key-strategy";
    /**
     * 配置字段名：算法
     */
    public static final String CONFIG_FIELD_ALGORITHM = "algorithm";
    /**
     * 配置字段名：限额列表
     */
    public static final String CONFIG_FIELD_LIMITS = "limits";
    /**
     * 配置字段名：降级策略
     */
    public static final String CONFIG_FIELD_FALLBACK = "fallback";
    /**
     * 配置字段名：资源编码
     */
    public static final String CONFIG_FIELD_RESOURCE_CODE = "resource-code";
    /**
     * 配置字段名：限流次数
     */
    public static final String CONFIG_FIELD_COUNT = "count";
    /**
     * 配置字段名：时间窗口
     */
    public static final String CONFIG_FIELD_WINDOW = "window";
    /**
     * 配置字段名：时间单位
     */
    public static final String CONFIG_FIELD_UNIT = "unit";
    /**
     * 远程策略开关配置路径
     */
    public static final String CONFIG_PREFIX_REMOTE_POLICY =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".remote-policy";
    /**
     * 配置字段名：启用开关
     */
    public static final String CONFIG_FIELD_ENABLE = "enable";
    /**
     * 远程策略开关配置路径
     */
    public static final String CONFIG_PATH_REMOTE_POLICY_ENABLE =
            CONFIG_PREFIX_REMOTE_POLICY + "." + CONFIG_FIELD_ENABLE;
    /**
     * 远程策略快照地址配置路径
     */
    public static final String CONFIG_PATH_REMOTE_POLICY_SNAPSHOT_URL =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".remote-policy.snapshot-url";
    /**
     * 远程策略固定 token 配置路径
     */
    public static final String CONFIG_PATH_REMOTE_POLICY_POLICY_TOKEN =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".remote-policy.policy-token";
    /**
     * 远程策略刷新间隔配置路径
     */
    public static final String CONFIG_PATH_REMOTE_POLICY_REFRESH_INTERVAL_MILLIS =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".remote-policy.refresh-interval-millis";
    /**
     * 远程策略连接超时配置路径
     */
    public static final String CONFIG_PATH_REMOTE_POLICY_CONNECT_TIMEOUT_MILLIS =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".remote-policy.connect-timeout-millis";
    /**
     * 远程策略读取超时配置路径
     */
    public static final String CONFIG_PATH_REMOTE_POLICY_READ_TIMEOUT_MILLIS =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".remote-policy.read-timeout-millis";
    /**
     * 远程策略最大策略数配置路径
     */
    public static final String CONFIG_PATH_REMOTE_POLICY_MAX_POLICY_COUNT =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".remote-policy.max-policy-count";
    /**
     * 远程策略单策略最大窗口数配置路径
     */
    public static final String CONFIG_PATH_REMOTE_POLICY_MAX_LIMITS_PER_POLICY =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".remote-policy.max-limits-per-policy";
    /**
     * 远程策略最大响应字节数配置路径
     */
    public static final String CONFIG_PATH_REMOTE_POLICY_MAX_RESPONSE_BYTES =
            SmartRedisLimiterConstant.CONFIG_PREFIX + ".remote-policy.max-response-bytes";
    /**
     * 默认关闭远程策略
     */
    public static final boolean DEFAULT_REMOTE_POLICY_ENABLE = false;

    // ==================== 远程策略默认值 ====================
    /**
     * 默认快照地址
     */
    public static final String DEFAULT_REMOTE_POLICY_SNAPSHOT_URL = null;
    /**
     * 默认刷新间隔（毫秒）
     */
    public static final long DEFAULT_REMOTE_POLICY_REFRESH_INTERVAL_MILLIS = 60_000L;
    /**
     * 默认连接超时（毫秒）
     */
    public static final long DEFAULT_REMOTE_POLICY_CONNECT_TIMEOUT_MILLIS = 2_000L;
    /**
     * 默认读取超时（毫秒）
     */
    public static final long DEFAULT_REMOTE_POLICY_READ_TIMEOUT_MILLIS = 3_000L;
    /**
     * HTTP 请求工厂超时配置最大值
     */
    public static final long MAX_HTTP_TIMEOUT_MILLIS = Integer.MAX_VALUE;
    /**
     * 默认在应用就绪后立即执行首次刷新
     */
    public static final boolean DEFAULT_REMOTE_POLICY_INITIAL_REFRESH = true;
    /**
     * 默认单服务最大策略数
     */
    public static final int DEFAULT_REMOTE_POLICY_MAX_POLICY_COUNT = 10_000;
    /**
     * 默认单策略最大窗口数
     */
    public static final int DEFAULT_REMOTE_POLICY_MAX_LIMITS_PER_POLICY =
            SmartRedisLimiterConstant.MAX_LIMITS_PER_POLICY;
    /**
     * 默认最大响应字节数
     */
    public static final long DEFAULT_REMOTE_POLICY_MAX_RESPONSE_BYTES = 4_194_304L;
    /**
     * HTTP 协议名
     */
    public static final String HTTP_SCHEME_HTTP = "http";

    // ==================== HTTP 协议 ====================
    /**
     * HTTPS 协议名
     */
    public static final String HTTP_SCHEME_HTTPS = "https";
    /**
     * If-None-Match 请求头
     */
    public static final String HTTP_HEADER_IF_NONE_MATCH = "If-None-Match";
    /**
     * ETag 响应头
     */
    public static final String HTTP_HEADER_ETAG = "ETag";
    /**
     * 策略 REST 固定 token 请求头
     */
    public static final String HTTP_HEADER_POLICY_TOKEN = "X-Smart-Redis-Limiter-Policy-Token";
    /**
     * Content-Length 响应头
     */
    public static final String HTTP_HEADER_CONTENT_LENGTH = "Content-Length";
    /**
     * 服务编码查询参数名
     */
    public static final String HTTP_QUERY_PARAM_SERVICE_CODE = "serviceCode";
    /**
     * HTTP 成功状态码
     */
    public static final int HTTP_STATUS_OK = 200;
    /**
     * HTTP 未修改状态码
     */
    public static final int HTTP_STATUS_NOT_MODIFIED = 304;
    /**
     * HTTP 状态码下界
     */
    public static final int HTTP_STATUS_MIN = 100;
    /**
     * HTTP 状态码上界
     */
    public static final int HTTP_STATUS_MAX = 599;
    /**
     * URI 未指定端口返回值
     */
    public static final int URI_UNSPECIFIED_PORT = -1;
    /**
     * 最小有效端口
     */
    public static final int MIN_VALID_PORT = 1;
    /**
     * 最大有效端口
     */
    public static final int MAX_VALID_PORT = 65_535;
    /**
     * 超时执行器线程名前缀
     */
    public static final String TIMEOUT_EXECUTOR_THREAD_NAME_PREFIX = "SmartRedisLimiter-Timeout-";

    // ==================== 线程 ====================
    /**
     * 远程策略刷新线程名前缀
     */
    public static final String REMOTE_POLICY_REFRESH_THREAD_NAME_PREFIX = "SmartRedisLimiter-PolicyRefresh-";
    /**
     * 超时执行器线程保活时间
     */
    public static final long TIMEOUT_EXECUTOR_KEEP_ALIVE_MILLIS = 0L;
    /**
     * 线程编号初始值
     */
    public static final int THREAD_INDEX_INITIAL_VALUE = 1;
    /**
     * Lua 5.1 双精度浮点数可精确表达的最大安全整数（2^53-1）
     */
    public static final long LUA_MAX_SAFE_INTEGER = 9_007_199_254_740_991L;

    // ==================== Lua 数值边界 ====================
    /**
     * 固定窗口 2.0 已使用次数 Key 后缀
     */
    public static final String SUFFIX_FIXED_WINDOW_USED_V2 = ":fw2:";
    /**
     * Lua 返回字段数量
     */
    public static final int LUA_RESULT_FIELD_COUNT = 4;

    // ==================== Lua 返回协议 ====================
    /**
     * Lua 放行标识
     */
    public static final long LUA_RESULT_PASSED = 1L;
    /**
     * Lua 放行字段索引
     */
    public static final int LUA_RESULT_PASSED_INDEX = 0;
    /**
     * Lua 阈值字段索引
     */
    public static final int LUA_RESULT_LIMIT_INDEX = 1;
    /**
     * Lua 剩余配额字段索引
     */
    public static final int LUA_RESULT_REMAINING_INDEX = 2;
    /**
     * Lua 重置时间字段索引
     */
    public static final int LUA_RESULT_RESET_AT_INDEX = 3;
    /**
     * Lua 放行字段名称
     */
    public static final String LUA_RESULT_PASSED_FIELD = "passed";
    /**
     * Lua 阈值字段名称
     */
    public static final String LUA_RESULT_LIMIT_FIELD = "limit";
    /**
     * Lua 剩余配额字段名称
     */
    public static final String LUA_RESULT_REMAINING_FIELD = "remaining";
    /**
     * Lua 重置时间字段名称
     */
    public static final String LUA_RESULT_RESET_AT_FIELD = "resetAt";
    /**
     * 每秒微秒数
     */
    public static final long MICROSECONDS_PER_SECOND = 1_000_000L;

    // ==================== 滑动窗口 ====================
    /**
     * 每毫秒微秒数
     */
    public static final long MICROSECONDS_PER_MILLISECOND = 1_000L;
    /**
     * 滑动窗口成员模板
     * 参数：请求唯一标识、窗口序号
     */
    public static final String TEMPLATE_SLIDING_WINDOW_MEMBER = "%s-%d";

    private SmartRedisLimiterStarterConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
