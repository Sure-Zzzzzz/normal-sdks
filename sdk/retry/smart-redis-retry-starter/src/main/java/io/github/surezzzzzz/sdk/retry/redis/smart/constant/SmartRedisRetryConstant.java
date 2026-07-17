package io.github.surezzzzzz.sdk.retry.redis.smart.constant;

/**
 * Smart Redis Retry 常量
 *
 * @author surezzzzzz
 */
public final class SmartRedisRetryConstant {

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.retry.redis.smart";
    /**
     * 启用配置项名称
     */
    public static final String PROPERTY_ENABLE = "enable";
    /**
     * 配置启用值
     */
    public static final String PROPERTY_TRUE = "true";
    /**
     * Redis Route 自动配置类名
     */
    public static final String REDIS_ROUTE_AUTO_CONFIGURATION_CLASS_NAME =
            "io.github.surezzzzzz.sdk.redis.route.configuration.SimpleRedisRouteConfiguration";

    /**
     * 默认启用状态
     */
    public static final boolean DEFAULT_ENABLE = true;
    /**
     * 默认 Redis Key 前缀
     */
    public static final String DEFAULT_KEY_PREFIX = "sure-smart-redis-retry";
    /**
     * 默认实例标识
     */
    public static final String DEFAULT_ME = "default";
    /**
     * 默认启用 Redis Cluster hash tag
     */
    public static final boolean DEFAULT_USE_HASH_TAG = true;
    /**
     * 默认单次扫描建议返回数量
     */
    public static final int DEFAULT_SCAN_COUNT = 500;
    /**
     * 默认重试记录存活秒数
     */
    public static final long DEFAULT_RECORD_TTL_SECONDS = 86400L;
    /**
     * 默认保留耗尽记录
     */
    public static final boolean DEFAULT_RETAIN_EXHAUSTED = true;
    /**
     * 默认重试标识最大长度
     */
    public static final int DEFAULT_MAX_RETRY_KEY_LENGTH = 512;
    /**
     * 默认上下文 JSON 最大长度
     */
    public static final int DEFAULT_MAX_CONTEXT_JSON_LENGTH = 4096;
    /**
     * 默认 Redis 失败处理策略
     */
    public static final String DEFAULT_REDIS_FAILURE_STRATEGY = RedisFailureStrategy.FAIL_CLOSED.getCode();
    /**
     * 工具类异常消息
     */
    public static final String UTILITY_CLASS_EXCEPTION_MESSAGE = "Utility class";
    /**
     * 默认最大重试次数
     */
    public static final int DEFAULT_MAX_RETRY_TIMES = 3;
    /**
     * Lua 指数退避允许的最大重试次数
     */
    public static final int MAX_RETRY_TIMES = 100;
    /**
     * Lua 指数退避允许的最大退避倍数
     */
    public static final double MAX_BACKOFF_MULTIPLIER = 10D;
    /**
     * Lua 可精确表示的最大整数
     */
    public static final long MAX_LUA_SAFE_INTEGER = 9007199254740991L;
    /**
     * 首次失败计数
     */
    public static final int RETRY_COUNT_INITIAL = 1;
    /**
     * 抖动范围倍率
     */
    public static final long JITTER_RANGE_MULTIPLIER = 2L;
    /**
     * 抖动范围偏移量
     */
    public static final long JITTER_RANGE_OFFSET = 1L;
    /**
     * Lua 计算抖动和时间戳时允许的最大重试间隔
     */
    public static final long MAX_RETRY_INTERVAL_MILLIS =
            (MAX_LUA_SAFE_INTEGER - RETRY_COUNT_INITIAL) / JITTER_RANGE_MULTIPLIER;
    /**
     * 默认基础重试间隔毫秒数
     */
    public static final long DEFAULT_RETRY_INTERVAL_MILLIS = 1000L;
    /**
     * 默认最大重试间隔毫秒数
     */
    public static final long DEFAULT_MAX_INTERVAL_MILLIS = 30000L;
    /**
     * 默认退避倍数
     */
    public static final double DEFAULT_BACKOFF_MULTIPLIER = 1.5D;
    /**
     * 默认抖动比例
     */
    public static final double DEFAULT_JITTER_RATIO = 0.1D;

    /**
     * 秒转换为毫秒的倍率
     */
    public static final long MILLIS_PER_SECOND = 1000L;
    /**
     * 可安全转换为毫秒的最大 TTL 秒数
     */
    public static final long MAX_RECORD_TTL_SECONDS = Long.MAX_VALUE / MILLIS_PER_SECOND;
    /**
     * 重试业务类型
     */
    public static final String BUSINESS_TYPE_RETRY = "retry";
    /**
     * SHA-1 摘要算法名称
     */
    public static final String HASH_ALGORITHM_SHA1 = "SHA-1";
    /**
     * 十六进制字符串容量倍率
     */
    public static final int HASH_HEX_CAPACITY_MULTIPLIER = 2;
    /**
     * 数组首个元素的下标
     */
    public static final int ARRAY_INITIAL_INDEX = 0;
    /**
     * 长整型零值
     */
    public static final long LONG_ZERO = 0L;
    /**
     * 双精度零值
     */
    public static final double DOUBLE_ZERO = 0D;
    /**
     * 最小退避倍数
     */
    public static final double MIN_BACKOFF_MULTIPLIER = 1D;
    /**
     * 最大抖动比例
     */
    public static final double MAX_JITTER_RATIO = 1D;
    /**
     * Redis Hash 条目中字段和值的元素数量
     */
    public static final int HASH_ENTRY_WIDTH = 2;
    /**
     * Redis Hash 条目中值相对于字段的下标偏移
     */
    public static final int HASH_VALUE_INDEX_OFFSET = 1;
    /**
     * 字节转换无符号整数的掩码
     */
    public static final int HASH_BYTE_MASK = 0xff;
    /**
     * 十六进制字节格式
     */
    public static final String HASH_HEX_FORMAT = "%02X";
    /**
     * 普通 Redis Key 模板
     */
    public static final String KEY_TEMPLATE = "%s:%s:%s:%s::%s";
    /**
     * 带 Cluster hash tag 的 Redis Key 模板
     */
    public static final String KEY_HASH_TAG_TEMPLATE = "%s:%s:%s:%s::{%s}";
    /**
     * Redis Key 扫描模式模板
     */
    public static final String SCAN_PATTERN_TEMPLATE = "%s:%s:%s:%s::*";
    /**
     * 初始扫描游标
     */
    public static final String CURSOR_INITIAL = "0";
    /**
     * Cluster 扫描游标模板，参数依次为节点下标和节点内游标
     */
    public static final String CLUSTER_CURSOR_TEMPLATE = "%s:%s";
    /**
     * Cluster 扫描游标节点与节点内游标的分隔符
     */
    public static final String CLUSTER_CURSOR_SEPARATOR = ":";
    /**
     * Cluster 扫描游标节点下标
     */
    public static final int CLUSTER_CURSOR_NODE_INDEX = 0;
    /**
     * Cluster 扫描游标节点内游标下标
     */
    public static final int CLUSTER_CURSOR_NODE_SCAN_INDEX = 1;
    /**
     * Cluster 扫描游标的字段数量
     */
    public static final int CLUSTER_CURSOR_PART_SIZE = 2;
    /**
     * 抖动哈希拼接分隔符
     */
    public static final String JITTER_HASH_SEPARATOR = "#";
    /**
     * 空字符串
     */
    public static final String EMPTY = "";
    /**
     * 记录失败 Lua 脚本路径
     */
    public static final String RECORD_FAILURE_SCRIPT_PATH = "scripts/smart_redis_retry_record_failure.lua";
    /**
     * 清理重试记录 Lua 脚本路径
     */
    public static final String CLEAR_RETRY_SCRIPT_PATH = "scripts/smart_redis_retry_clear.lua";

    /**
     * Redis Hash 中的失败次数字段
     */
    public static final String FIELD_COUNT = "count";
    /**
     * Redis Hash 中的最大重试次数字段
     */
    public static final String FIELD_MAX_RETRY_TIMES = "maxRetryTimes";
    /**
     * Redis Hash 中的基础重试间隔字段
     */
    public static final String FIELD_RETRY_INTERVAL_MILLIS = "retryIntervalMillis";
    /**
     * Redis Hash 中的最大重试间隔字段
     */
    public static final String FIELD_MAX_INTERVAL_MILLIS = "maxIntervalMillis";
    /**
     * Redis Hash 中的退避倍数字段
     */
    public static final String FIELD_BACKOFF_MULTIPLIER = "backoffMultiplier";
    /**
     * Redis Hash 中的首次失败时间字段
     */
    public static final String FIELD_FIRST_FAIL_TIME = "firstFailTime";
    /**
     * Redis Hash 中的最近失败时间字段
     */
    public static final String FIELD_LAST_FAIL_TIME = "lastFailTime";
    /**
     * Redis Hash 中的下次重试时间字段
     */
    public static final String FIELD_NEXT_RETRY_TIME = "nextRetryTime";
    /**
     * Redis Hash 中的最近错误码字段
     */
    public static final String FIELD_LAST_ERROR_CODE = "lastErrorCode";
    /**
     * Redis Hash 中的最近错误消息字段
     */
    public static final String FIELD_LAST_ERROR_MESSAGE = "lastErrorMessage";
    /**
     * Redis Hash 中的上下文字段
     */
    public static final String FIELD_CONTEXT = "context";

    /**
     * 脚本返回结果字段数量
     */
    public static final int SCRIPT_RESULT_SIZE = 11;
    /**
     * 脚本返回结果中失败次数的下标
     */
    public static final int SCRIPT_RESULT_COUNT_INDEX = 0;
    /**
     * 脚本返回结果中最大重试次数的下标
     */
    public static final int SCRIPT_RESULT_MAX_RETRY_TIMES_INDEX = 1;
    /**
     * 脚本返回结果中基础重试间隔的下标
     */
    public static final int SCRIPT_RESULT_RETRY_INTERVAL_MILLIS_INDEX = 2;
    /**
     * 脚本返回结果中最大重试间隔的下标
     */
    public static final int SCRIPT_RESULT_MAX_INTERVAL_MILLIS_INDEX = 3;
    /**
     * 脚本返回结果中退避倍数的下标
     */
    public static final int SCRIPT_RESULT_BACKOFF_MULTIPLIER_INDEX = 4;
    /**
     * 脚本返回结果中首次失败时间的下标
     */
    public static final int SCRIPT_RESULT_FIRST_FAIL_TIME_INDEX = 5;
    /**
     * 脚本返回结果中最近失败时间的下标
     */
    public static final int SCRIPT_RESULT_LAST_FAIL_TIME_INDEX = 6;
    /**
     * 脚本返回结果中下次重试时间的下标
     */
    public static final int SCRIPT_RESULT_NEXT_RETRY_TIME_INDEX = 7;
    /**
     * 脚本返回结果中最近错误码的下标
     */
    public static final int SCRIPT_RESULT_LAST_ERROR_CODE_INDEX = 8;
    /**
     * 脚本返回结果中最近错误消息的下标
     */
    public static final int SCRIPT_RESULT_LAST_ERROR_MESSAGE_INDEX = 9;
    /**
     * 脚本返回结果中上下文的下标
     */
    public static final int SCRIPT_RESULT_CONTEXT_INDEX = 10;

    /**
     * 禁止实例化常量类
     */
    private SmartRedisRetryConstant() {
        throw new UnsupportedOperationException(UTILITY_CLASS_EXCEPTION_MESSAGE);
    }
}
