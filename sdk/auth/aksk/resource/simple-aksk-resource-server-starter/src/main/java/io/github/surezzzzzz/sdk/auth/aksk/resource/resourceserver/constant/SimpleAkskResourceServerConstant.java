package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant;

/**
 * Simple AKSK Resource Server Constants
 *
 * <p>仅包含 resource-server-starter 专属常量。
 * 通用常量（ACCESS_SOURCE_*、FIELD_TRACE_ID、HEADER_USER_AGENT 等）已统一定义在
 * {@link io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant}。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public final class SimpleAkskResourceServerConstant {

    /**
     * Configuration prefix
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.aksk.resource.server";

    /**
     * Spring Boot servlet context-path 配置键
     */
    public static final String PROPERTY_SERVER_SERVLET_CONTEXT_PATH = "server.servlet.context-path";

    // ==================== Path Constants ====================

    /**
     * URL 路径分隔符
     */
    public static final String URL_PATH_SEPARATOR = "/";

    /**
     * URL query string 分隔符
     */
    public static final String URL_QUERY_SEPARATOR = "?";

    /**
     * Spring Security 全量 Ant matcher
     */
    public static final String ANT_PATTERN_ALL = "/**";

    // ==================== PEM Format Constants ====================

    /**
     * PEM public key header
     */
    public static final String PEM_PUBLIC_KEY_HEADER = "-----BEGIN PUBLIC KEY-----";

    /**
     * PEM public key footer
     */
    public static final String PEM_PUBLIC_KEY_FOOTER = "-----END PUBLIC KEY-----";

    // ==================== Algorithm Constants ====================

    /**
     * RSA algorithm name
     */
    public static final String ALGORITHM_RSA = "RSA";

    // ==================== Error Message Templates ====================

    /**
     * Error message when public key is not configured
     */
    public static final String ERROR_PUBLIC_KEY_NOT_CONFIGURED = "Public key not configured. Please set either 'jwt.public-key' or 'jwt.public-key-location'";

    /**
     * Error message prefix when public key file is not found
     */
    public static final String ERROR_PUBLIC_KEY_FILE_NOT_FOUND = "Public key file not found: ";

    /**
     * permit-all-paths 包含 /** 时覆盖 protected-paths 的错误消息
     */
    public static final String ERROR_PERMIT_ALL_OVERRIDES_PROTECTED =
            "permit-all-paths contains /** after context-path normalization; " +
                    "it will override protected-paths because Spring Security uses first-match authorization rules. " +
                    "Remove /** from permit-all-paths or clear protected-paths.";

    /**
     * Security path 中不允许包含 query string 的错误消息模板
     * 参数: path
     */
    public static final String ERROR_SECURITY_PATH_CONTAINS_QUERY_STRING =
            "security path must not contain query string: %s";

    // ==================== 本地缓存默认值 ====================

    /**
     * 默认启用 context-path-aware 路径归一化
     */
    public static final boolean DEFAULT_CONTEXT_PATH_AWARE = true;

    /**
     * 本地缓存默认开启
     */
    public static final boolean DEFAULT_LOCAL_CACHE_ENABLED = true;

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
        throw new UnsupportedOperationException("Utility class");
    }
}
