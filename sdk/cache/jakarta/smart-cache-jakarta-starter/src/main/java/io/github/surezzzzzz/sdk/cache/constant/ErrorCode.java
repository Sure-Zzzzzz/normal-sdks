package io.github.surezzzzzz.sdk.cache.constant;

/**
 * Smart Cache 错误码
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * 配置错误
     */
    public static final String SMART_CACHE_CONFIG_ERROR = "SMART_CACHE_001";

    /**
     * Route 缺失
     */
    public static final String SMART_CACHE_ROUTE_MISSING = "SMART_CACHE_002";

    /**
     * Route 跨数据源
     */
    public static final String SMART_CACHE_ROUTE_CROSS_DATASOURCE = "SMART_CACHE_003";

    /**
     * L2 操作失败
     */
    public static final String SMART_CACHE_L2_OPERATION_FAILED = "SMART_CACHE_004";

    /**
     * 序列化失败
     */
    public static final String SMART_CACHE_SERIALIZATION_FAILED = "SMART_CACHE_005";

    /**
     * Loader 执行失败
     */
    public static final String SMART_CACHE_LOAD_FAILED = "SMART_CACHE_006";

    /**
     * 循环依赖
     */
    public static final String SMART_CACHE_CIRCULAR_DEPENDENCY = "SMART_CACHE_007";

    /**
     * Pub/Sub 初始化失败
     */
    public static final String SMART_CACHE_PUBSUB_INIT_FAILED = "SMART_CACHE_008";

    /**
     * 预热执行失败
     */
    public static final String SMART_CACHE_WARMUP_FAILED = "SMART_CACHE_009";

    private ErrorCode() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }
}
