package io.github.surezzzzzz.sdk.auth.aksk.core.constant;

/**
 * AKSK 通用常量定义
 *
 * @author Sure
 * @since 1.0.0
 */
public final class AkskConstant {

    private AkskConstant() {
    }

    // ==================== ClientId 格式 ====================

    /**
     * 平台级 ClientId 前缀
     */
    public static final String PLATFORM_CLIENT_ID_PREFIX = "AKP";

    /**
     * 用户级 ClientId 前缀
     */
    public static final String USER_CLIENT_ID_PREFIX = "AKU";

    /**
     * ClientId 随机部分长度（Base62）
     */
    public static final int CLIENT_ID_RANDOM_LENGTH = 20;

    /**
     * ClientId 总长度（前缀3位 + 随机20位）
     */
    public static final int CLIENT_ID_TOTAL_LENGTH = 23;

    // ==================== ClientSecret 格式 ====================

    /**
     * ClientSecret 前缀
     */
    public static final String CLIENT_SECRET_PREFIX = "SK";

    /**
     * ClientSecret 随机部分长度（Base62）
     */
    public static final int CLIENT_SECRET_RANDOM_LENGTH = 40;

    /**
     * ClientSecret 总长度（前缀2位 + 随机40位）
     */
    public static final int CLIENT_SECRET_TOTAL_LENGTH = 42;

    // ==================== Security Context ====================

    /**
     * Security Context 默认最大大小（4KB）
     */
    public static final int DEFAULT_SECURITY_CONTEXT_MAX_SIZE = 4096;

    /**
     * Security Context 原始值的 key（用于从扁平化的 Map 中获取）
     */
    public static final String SECURITY_CONTEXT_RAW_KEY = "_raw";

    // ==================== JWT ====================

    /**
     * JWT 默认有效期（秒）- 1小时
     */
    public static final int DEFAULT_JWT_EXPIRES_IN = 3600;

    /**
     * Token 类型
     */
    public static final String TOKEN_TYPE_BEARER = "Bearer";
}
