package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant;

/**
 * Simple AKSK Resource Server Constants
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public class SimpleAkskResourceServerConstant {

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
     * 链路追踪 ID 字段名
     */
    public static final String FIELD_TRACE_ID = "traceId";

    private SimpleAkskResourceServerConstant() {
        // Utility class, prevent instantiation
    }
}
