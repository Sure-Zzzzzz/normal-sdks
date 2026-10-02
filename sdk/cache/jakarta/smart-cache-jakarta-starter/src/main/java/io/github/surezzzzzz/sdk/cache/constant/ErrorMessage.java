package io.github.surezzzzzz.sdk.cache.constant;

/**
 * Smart Cache 错误消息
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    /**
     * 配置错误
     */
    public static final String SMART_CACHE_CONFIG_ERROR = "缓存配置错误：%s";

    /**
     * Route 缺失
     */
    public static final String SMART_CACHE_ROUTE_MISSING = "开启 L2 时必须提供 RedisRouteTemplate";

    /**
     * 强一致性 Route 缺失
     */
    public static final String SMART_CACHE_STRONG_CONSISTENCY_ROUTE_MISSING = "强一致性模式必须提供 RedisRouteTemplate";

    /**
     * Route 跨数据源
     */
    public static final String SMART_CACHE_ROUTE_CROSS_DATASOURCE = "批量缓存 Key 不能跨数据源：%s";

    /**
     * L2 操作失败
     */
    public static final String SMART_CACHE_L2_OPERATION_FAILED = "L2 缓存操作失败：%s";

    /**
     * 序列化失败
     */
    public static final String SMART_CACHE_SERIALIZATION_FAILED = "缓存序列化失败：%s";

    /**
     * Loader 执行失败
     */
    public static final String SMART_CACHE_LOAD_FAILED = "缓存加载失败：%s";

    /**
     * 循环依赖
     */
    public static final String SMART_CACHE_CIRCULAR_DEPENDENCY = "检测到循环依赖：%s";

    /**
     * Pub/Sub 初始化失败
     */
    public static final String SMART_CACHE_PUBSUB_INIT_FAILED = "Pub/Sub 初始化失败：%s";

    /**
     * 预热执行失败
     */
    public static final String SMART_CACHE_WARMUP_FAILED = "缓存预热执行失败：%s";

    /**
     * 预热任务名称
     */
    public static final String SMART_CACHE_WARMUP_TASK_NAME = "%s.%s";

    /**
     * 预热失败详情
     */
    public static final String SMART_CACHE_WARMUP_FAILURE_DETAIL = "%s，原因：%s";

    /**
     * 等待其他实例预热超时
     */
    public static final String SMART_CACHE_WARMUP_WAIT_TIMEOUT = "等待其他实例完成缓存预热超时";

    /**
     * 预热租约续租失败
     */
    public static final String SMART_CACHE_WARMUP_LEASE_RENEW_FAILED = "缓存预热租约续租失败";

    /**
     * 预热方法返回类型无效
     */
    public static final String SMART_CACHE_WARMUP_RETURN_TYPE_INVALID = "预热方法必须返回 Map<String, Object>";

    /**
     * 预热 Map 键类型无效
     */
    public static final String SMART_CACHE_WARMUP_MAP_KEY_INVALID = "预热方法返回的 Map key 必须为 String";

    private ErrorMessage() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }
}
