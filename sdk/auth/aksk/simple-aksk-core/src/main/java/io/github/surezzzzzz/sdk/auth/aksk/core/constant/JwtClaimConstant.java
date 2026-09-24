package io.github.surezzzzzz.sdk.auth.aksk.core.constant;

/**
 * JWT Claims 常量定义
 *
 * @author Sure
 * @since 1.0.0
 */
public final class JwtClaimConstant {

    /**
     * Subject（主题）- 通常是 Client ID
     */
    public static final String SUB = "sub";

    // ==================== OAuth2 标准 Claims ====================
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
    /**
     * Client ID（调用方系统标识）
     */
    public static final String CLIENT_ID = "client_id";

    // ==================== AKSK 自定义 Claims ====================
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
    /**
     * AKSK服务主体应用授权快照。
     */
    public static final String APPLICATION_AUTHORIZATION = "aksk_authorization";
    /**
     * AKSK 授权来源模式。
     */
    public static final String AUTHORIZATION_MODE = "authorization_mode";
    /**
     * OWNER_INHERITED AKU 的目标可信应用标识。
     */
    public static final String TARGET_APPLICATION_ID = "target_application_id";
    /**
     * OWNER_INHERITED AKU 所属人安全纪元。
     */
    public static final String OWNER_SECURITY_EPOCH = "owner_security_epoch";
    /**
     * OWNER_INHERITED AKU 可信应用授权纪元。
     */
    public static final String APPLICATION_AUTHORIZATION_EPOCH = "application_authorization_epoch";
    /**
     * OWNER_INHERITED AKU 的 IAM 稳定所属人来源。
     */
    public static final String OWNER_SOURCE_ID = "owner_source_id";
    /**
     * OWNER_INHERITED AKU 的目标应用访问纪元。
     */
    public static final String OWNER_INHERITED_ACCESS_EPOCH = "owner_inherited_access_epoch";
    /**
     * OWNER_INHERITED AKU 的人员-应用投影访问纪元。
     */
    public static final String PROJECTION_ACCESS_EPOCH = "projection_access_epoch";

    private JwtClaimConstant() {
    }
}
