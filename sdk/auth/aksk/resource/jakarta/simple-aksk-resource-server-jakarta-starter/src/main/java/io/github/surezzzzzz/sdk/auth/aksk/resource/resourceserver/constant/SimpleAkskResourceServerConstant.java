package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant;

/**
 * Simple AKSK Resource Server 常量。
 *
 * <p>仅包含 AKSK Provider Starter 的配置和本地缓存常量。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public final class SimpleAkskResourceServerConstant {

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.aksk.resource.server";

    /**
     * 内省配置前缀
     */
    public static final String CONFIG_PREFIX_INTROSPECT = CONFIG_PREFIX + ".introspect";

    // ==================== 本地缓存默认值 ====================

    /**
     * 本地缓存默认关闭
     */
    public static final boolean DEFAULT_LOCAL_CACHE_ENABLED = false;

    /**
     * 本地缓存默认 TTL（秒）
     */
    public static final int DEFAULT_LOCAL_CACHE_EXPIRE_SECONDS = 3;

    /**
     * 本地缓存默认最大条目数
     */
    public static final int DEFAULT_LOCAL_CACHE_MAX_SIZE = 10000;

    /**
     * 统计日志默认打印间隔（秒）
     */
    public static final int DEFAULT_STATS_LOG_INTERVAL_SECONDS = 60;

    // ==================== 兜底缓存默认值 ====================

    /**
     * 兜底缓存默认关闭，需显式开启
     */
    public static final boolean DEFAULT_FALLBACK_ENABLED = false;

    /**
     * 兜底缓存 TTL 倍数默认值：兜底 TTL = expire-seconds × 此值
     */
    public static final int DEFAULT_STALE_TTL_MULTIPLIER = 10;

    /**
     * 兜底缓存默认最大条目数
     */
    public static final int DEFAULT_STALE_MAX_SIZE = 10000;

    /**
     * stale-ttl-multiplier 建议最小值，低于此值无意义
     */
    public static final int MIN_STALE_TTL_MULTIPLIER = 2;

    /**
     * stale-ttl-multiplier 建议最大值，超过此值打 WARN 提示安全风险
     */
    public static final int WARN_STALE_TTL_MULTIPLIER_MAX = 100;

    private SimpleAkskResourceServerConstant() {
        throw new UnsupportedOperationException("工具类不能实例化");
    }
}
