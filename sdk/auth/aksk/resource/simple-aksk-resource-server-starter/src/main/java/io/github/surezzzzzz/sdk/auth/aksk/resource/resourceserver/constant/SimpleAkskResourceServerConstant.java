package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant;

/**
 * Simple AKSK Resource Server Constants
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public final class SimpleAkskResourceServerConstant {

    /**
     * Configuration prefix
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.aksk.resource.server";

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

    // ==================== 本地缓存默认值 ====================

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

    // ==================== Introspect / JWT 字段常量 ====================

    /**
     * Introspect 响应中表示 token 是否有效的字段名
     */
    public static final String INTROSPECT_CLAIM_ACTIVE = "active";

    /**
     * JWT subject claim 名
     */
    public static final String JWT_CLAIM_SUB = "sub";

    /**
     * Spring Security 权限前缀
     */
    public static final String AUTHORITY_SCOPE_PREFIX = "SCOPE_";

    /**
     * HTTP User-Agent 请求头名
     */
    public static final String HEADER_USER_AGENT = "User-Agent";

    /**
     * AkskAccessEvent source 标识：introspect 验证
     */
    public static final String ACCESS_SOURCE_INTROSPECT = "introspect";

    /**
     * AkskAccessEvent source 标识：JWT 验证
     */
    public static final String ACCESS_SOURCE_JWT = "jwt";

    /**
     * 链路追踪 ID 字段名
     */
    public static final String FIELD_TRACE_ID = "traceId";

    private SimpleAkskResourceServerConstant() {
        throw new UnsupportedOperationException("Utility class");
    }
}
