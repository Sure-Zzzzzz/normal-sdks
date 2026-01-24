package io.github.surezzzzzz.sdk.auth.aksk.core.constant;

/**
 * JWT Claims 常量定义
 *
 * @author Sure
 * @since 1.0.0
 */
public final class JwtClaimConstant {

    private JwtClaimConstant() {
    }

    // ==================== OAuth2 标准 Claims ====================

    /**
     * Subject（主题）- 通常是 Client ID
     */
    public static final String SUB = "sub";

    /**
     * Issuer（签发者）
     */
    public static final String ISS = "iss";

    /**
     * Audience（受众）
     */
    public static final String AUD = "aud";

    /**
     * Expiration Time（过期时间）
     */
    public static final String EXP = "exp";

    /**
     * Issued At（签发时间）
     */
    public static final String IAT = "iat";

    // ==================== AKSK 自定义 Claims ====================

    /**
     * Client ID（调用方系统标识）
     */
    public static final String CLIENT_ID = "client_id";

    /**
     * Client Type（客户端类型：platform/user）
     */
    public static final String CLIENT_TYPE = "client_type";

    /**
     * User ID（用户ID，用于数据权限控制）
     */
    public static final String USER_ID = "user_id";

    /**
     * Username（用户名）
     */
    public static final String USERNAME = "username";

    /**
     * Scope（OAuth2 权限范围）
     */
    public static final String SCOPE = "scope";

    /**
     * Security Context（自定义安全上下文）
     */
    public static final String SECURITY_CONTEXT = "security_context";
}
