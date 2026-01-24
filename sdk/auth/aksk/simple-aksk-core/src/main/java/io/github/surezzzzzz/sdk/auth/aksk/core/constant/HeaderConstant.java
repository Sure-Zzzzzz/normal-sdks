package io.github.surezzzzzz.sdk.auth.aksk.core.constant;

/**
 * HTTP Header 常量定义
 *
 * @author Sure
 * @since 1.0.0
 */
public final class HeaderConstant {

    private HeaderConstant() {
    }

    /**
     * Header 前缀
     */
    public static final String HEADER_PREFIX = "x-sure-auth-aksk-";

    // ==================== 核心认证字段 ====================

    /**
     * Client ID（调用方系统标识）
     */
    public static final String CLIENT_ID = HEADER_PREFIX + "client-id";

    /**
     * User ID（用户ID，用于数据权限控制）
     */
    public static final String USER_ID = HEADER_PREFIX + "user-id";

    /**
     * Username（用户名）
     */
    public static final String USERNAME = HEADER_PREFIX + "username";

    // ==================== 权限相关 ====================

    /**
     * Roles（角色列表，逗号分隔）
     */
    public static final String ROLES = HEADER_PREFIX + "roles";

    /**
     * Scope（OAuth2 权限范围，逗号分隔）
     */
    public static final String SCOPE = HEADER_PREFIX + "scope";

    // ==================== Security Context ====================

    /**
     * Security Context（原始安全上下文，字符串格式）
     */
    public static final String SECURITY_CONTEXT = HEADER_PREFIX + "security-context";

    // ==================== 分隔符 ====================

    /**
     * 列表分隔符（用于 roles、scope 等数组字段）
     */
    public static final String LIST_SEPARATOR = ",";
}
