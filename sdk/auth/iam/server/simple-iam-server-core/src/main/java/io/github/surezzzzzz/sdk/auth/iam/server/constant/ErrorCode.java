package io.github.surezzzzzz.sdk.auth.iam.server.constant;

/**
 * Error Code Constants
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    public static final String VALIDATION_FAILED = "VALIDATION_001";

    // ==================== 参数验证错误 ====================
    public static final String CONFIG_VALIDATION_FAILED = "CONFIG_001";

    // ==================== 配置错误 ====================
    public static final String USER_CREATE_FAILED = "USER_001";

    // ==================== 用户管理错误 ====================
    public static final String USER_NOT_FOUND = "USER_002";
    public static final String USER_ALREADY_EXISTS = "USER_003";
    public static final String USER_UPDATE_FAILED = "USER_004";
    public static final String USER_DISABLED = "USER_005";
    public static final String LAST_ADMIN_PROTECTED = "USER_006";
    public static final String DEPARTMENT_NOT_FOUND = "DEPARTMENT_001";

    // ==================== 部门管理错误 ====================
    public static final String DEPARTMENT_ALREADY_EXISTS = "DEPARTMENT_002";
    public static final String DEPARTMENT_DELETE_BLOCKED = "DEPARTMENT_003";
    public static final String DEPARTMENT_PARENT_INVALID = "DEPARTMENT_004";
    public static final String USER_GROUP_NOT_FOUND = "USER_GROUP_001";

    // ==================== 协作组管理错误 ====================
    public static final String USER_GROUP_ALREADY_EXISTS = "USER_GROUP_002";
    public static final String MESSAGE_NOT_FOUND = "MESSAGE_001";

    // ==================== 站内信错误 ====================
    public static final String MESSAGE_FORBIDDEN = "MESSAGE_002";
    public static final String MESSAGE_RECIPIENT_EMPTY = "MESSAGE_003";
    public static final String BAD_CREDENTIALS = "AUTH_001";

    // ==================== 认证错误 ====================
    public static final String ACCOUNT_LOCKED = "AUTH_002";
    public static final String TOKEN_INVALID = "AUTH_003";
    public static final String TOKEN_EXPIRED = "AUTH_004";
    public static final String REFRESH_TOKEN_REUSE = "AUTH_005";
    public static final String CAPTCHA_REQUIRED = "AUTH_006";
    public static final String CAPTCHA_INVALID = "AUTH_007";
    public static final String CAPTCHA_PROVIDER_MISSING = "AUTH_008";
    public static final String MUST_CHANGE_PASSWORD = "AUTH_009";
    public static final String PASSWORD_CHANGE_NOT_ALLOWED = "AUTH_010";
    public static final String PASSWORD_POLICY_VIOLATION = "AUTH_011";
    public static final String SESSION_NOT_FOUND = "SESSION_001";

    // ==================== 会话错误 ====================
    public static final String SESSION_EXPIRED = "SESSION_002";
    public static final String AUTHORIZE_CONTEXT_INVALID = "AUTHORIZE_CONTEXT_001";

    // ==================== 授权交易错误 ====================
    public static final String AUTHORIZE_CONTEXT_EXPIRED = "AUTHORIZE_CONTEXT_002";
    public static final String AUTHORIZE_CONTEXT_FORBIDDEN = "AUTHORIZE_CONTEXT_003";
    public static final String PERMISSION_DENIED = "PERMISSION_001";

    // ==================== 权限错误 ====================
    public static final String TRUSTED_APPLICATION_NOT_FOUND = "TRUSTED_APPLICATION_001";

    // ==================== 可信应用错误 ====================
    public static final String TRUSTED_APPLICATION_REDIRECT_URI_INVALID = "TRUSTED_APPLICATION_002";
    public static final String TRUSTED_APPLICATION_ID_EXISTS = "TRUSTED_APPLICATION_003";
    public static final String TRUSTED_APPLICATION_GRANT_TYPE_NOT_ALLOWED = "TRUSTED_APPLICATION_004";
    public static final String TRUSTED_APPLICATION_CLIENT_POLICY_INVALID = "TRUSTED_APPLICATION_005";
    public static final String TRUSTED_APPLICATION_CODE_EXISTS = "TRUSTED_APPLICATION_006";
    public static final String TRUSTED_APPLICATION_CLIENT_REQUIRED = "TRUSTED_APPLICATION_007";
    public static final String TRUSTED_APPLICATION_PORTAL_CONFIG_INVALID = "TRUSTED_APPLICATION_008";
    public static final String TRUSTED_APPLICATION_CLIENT_NOT_BELONG = "TRUSTED_APPLICATION_009";
    public static final String TRUSTED_APPLICATION_ICON_INVALID = "TRUSTED_APPLICATION_010";
    public static final String TRUSTED_APPLICATION_DELETE_BLOCKED = "TRUSTED_APPLICATION_011";
    public static final String APPLICATION_MANIFEST_NOT_FOUND = "TRUSTED_APPLICATION_012";
    public static final String APPLICATION_MANIFEST_CONFLICT = "TRUSTED_APPLICATION_013";
    public static final String TRUSTED_APPLICATION_MENU_TREE_INVALID = "TRUSTED_APPLICATION_014";
    public static final String TRUSTED_APPLICATION_MENU_TREE_LEGACY_CONFLICT = "TRUSTED_APPLICATION_015";
    public static final String TRUSTED_APPLICATION_MENU_PERMISSION_REFERENCED = "TRUSTED_APPLICATION_016";
    public static final String TOKEN_OPERATION_FAILED = "TOKEN_001";

    // ==================== Token / Cache 错误 ====================
    public static final String CACHE_OPERATION_FAILED = "CACHE_001";

    // ==================== 资源验证客户端错误 ====================
    public static final String RESOURCE_VERIFICATION_CLIENT_NOT_FOUND = "RESOURCE_VERIFICATION_CLIENT_001";
    public static final String RESOURCE_VERIFICATION_CLIENT_NOT_BELONG = "RESOURCE_VERIFICATION_CLIENT_002";
    public static final String RESOURCE_VERIFICATION_CLIENT_ID_EXISTS = "RESOURCE_VERIFICATION_CLIENT_003";
    public static final String RESOURCE_VERIFICATION_CLIENT_REVOKED = "RESOURCE_VERIFICATION_CLIENT_004";

    // ==================== 用户应用授权错误 ====================
    public static final String APPLICATION_AUTHORIZATION_NOT_FOUND = "APPLICATION_AUTHORIZATION_001";
    public static final String APPLICATION_AUTHORIZATION_CONTENT_INVALID = "APPLICATION_AUTHORIZATION_002";
    public static final String APPLICATION_AUTHORIZATION_CONFLICT = "APPLICATION_AUTHORIZATION_003";

    private ErrorCode() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
