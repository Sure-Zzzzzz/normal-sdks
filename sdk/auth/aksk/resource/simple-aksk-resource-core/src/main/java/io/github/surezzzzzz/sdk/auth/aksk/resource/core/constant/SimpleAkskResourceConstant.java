package io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant;

import java.util.Map;

/**
 * Simple AKSK Resource Constants
 * <p>
 * Defines naming conventions for:
 * - Field names (camelCase) - used as keys in context Map
 * - JWT Claim names (snake_case) - used in JWT token
 * - HTTP Header names (kebab-case with prefix) - used in HTTP requests
 * </p>
 *
 * @author surezzzzzz
 */
public class SimpleAkskResourceConstant {

    // ==================== Request Attribute Key ====================

    /**
     * Request Attribute key for storing security context
     * <p>
     * Used by both security-context-starter and resource-server-starter
     * to store the security context Map in HttpServletRequest attributes
     * </p>
     */
    public static final String CONTEXT_ATTRIBUTE = "simple-aksk-security-context";

    // ==================== SpEL Expression Constants ====================

    /**
     * SpEL 表达式中的上下文变量名
     * <p>
     * 在 @RequireExpression 注解中使用此变量名访问安全上下文
     * <p>
     * 示例：@RequireExpression("#context['userId'] != null")
     */
    public static final String SPEL_CONTEXT_VARIABLE = "context";

    // ==================== Error Message Templates ====================

    /**
     * 缺少必需字段的错误消息模板
     */
    public static final String ERROR_REQUIRED_FIELD_MISSING = "Required field '%s' is missing";

    /**
     * 字段值不匹配的错误消息模板
     */
    public static final String ERROR_FIELD_VALUE_MISMATCH = "Field '%s' value mismatch: expected '%s', actual '%s'";

    /**
     * 表达式求值失败的错误消息前缀
     */
    public static final String ERROR_EXPRESSION_EVALUATION_FAILED = "Expression evaluation failed: ";

    /**
     * 表达式检查失败的错误消息前缀
     */
    public static final String ERROR_EXPRESSION_CHECK_FAILED = "Expression check failed: ";

    // ==================== Field Names (for context Map keys) ====================

    /**
     * Client ID field name
     */
    public static final String FIELD_CLIENT_ID = "clientId";

    /**
     * Client Type field name (e.g., "user", "service")
     */
    public static final String FIELD_CLIENT_TYPE = "clientType";

    /**
     * User ID field name
     */
    public static final String FIELD_USER_ID = "userId";

    /**
     * Username field name
     */
    public static final String FIELD_USERNAME = "username";

    /**
     * Security Context field name
     */
    public static final String FIELD_SECURITY_CONTEXT = "securityContext";

    /**
     * Roles field name
     */
    public static final String FIELD_ROLES = "roles";

    /**
     * Scope field name
     */
    public static final String FIELD_SCOPE = "scope";

    // ==================== JWT Claim Names ====================

    /**
     * JWT Claim: client_id
     */
    public static final String JWT_CLAIM_CLIENT_ID = "client_id";

    /**
     * JWT Claim: client_type
     */
    public static final String JWT_CLAIM_CLIENT_TYPE = "client_type";

    /**
     * JWT Claim: user_id
     */
    public static final String JWT_CLAIM_USER_ID = "user_id";

    /**
     * JWT Claim: username
     */
    public static final String JWT_CLAIM_USERNAME = "username";

    /**
     * JWT Claim: security_context
     */
    public static final String JWT_CLAIM_SECURITY_CONTEXT = "security_context";

    /**
     * JWT Claim: scope
     */
    public static final String JWT_CLAIM_SCOPE = "scope";

    // ==================== HTTP Header Names ====================

    /**
     * Default HTTP Header prefix
     */
    public static final String DEFAULT_HEADER_PREFIX = "x-sure-auth-aksk-";

    /**
     * HTTP Header: x-sure-auth-aksk-user-id
     */
    public static final String HEADER_USER_ID = DEFAULT_HEADER_PREFIX + "user-id";

    /**
     * HTTP Header: x-sure-auth-aksk-username
     */
    public static final String HEADER_USERNAME = DEFAULT_HEADER_PREFIX + "username";

    /**
     * HTTP Header: x-sure-auth-aksk-client-id
     */
    public static final String HEADER_CLIENT_ID = DEFAULT_HEADER_PREFIX + "client-id";

    /**
     * HTTP Header: x-sure-auth-aksk-client-type
     */
    public static final String HEADER_CLIENT_TYPE = DEFAULT_HEADER_PREFIX + "client-type";

    /**
     * HTTP Header: x-sure-auth-aksk-security-context
     */
    public static final String HEADER_SECURITY_CONTEXT = DEFAULT_HEADER_PREFIX + "security-context";

    /**
     * HTTP Header: x-sure-auth-aksk-roles
     */
    public static final String HEADER_ROLES = DEFAULT_HEADER_PREFIX + "roles";

    /**
     * HTTP Header: x-sure-auth-aksk-scope
     */
    public static final String HEADER_SCOPE = DEFAULT_HEADER_PREFIX + "scope";

    // ==================== Field Name to JWT Claim Mapping ====================

    /**
     * Mapping from field names (camelCase) to JWT claim names (snake_case)
     * <p>
     * This mapping is used by AkskJwtContext to convert JWT claims to context fields
     * </p>
     */
    public static final Map<String, String> FIELD_TO_JWT_CLAIM;

    /**
     * Mapping from JWT claim names (snake_case) to field names (camelCase)
     * <p>
     * This is the reverse mapping of FIELD_TO_JWT_CLAIM
     * </p>
     */
    public static final Map<String, String> JWT_CLAIM_TO_FIELD;

    static {
        Map<String, String> fieldToJwt = new java.util.HashMap<>();
        fieldToJwt.put(FIELD_CLIENT_ID, JWT_CLAIM_CLIENT_ID);
        fieldToJwt.put(FIELD_CLIENT_TYPE, JWT_CLAIM_CLIENT_TYPE);
        fieldToJwt.put(FIELD_USER_ID, JWT_CLAIM_USER_ID);
        fieldToJwt.put(FIELD_USERNAME, JWT_CLAIM_USERNAME);
        fieldToJwt.put(FIELD_SECURITY_CONTEXT, JWT_CLAIM_SECURITY_CONTEXT);
        fieldToJwt.put(FIELD_SCOPE, JWT_CLAIM_SCOPE);
        FIELD_TO_JWT_CLAIM = java.util.Collections.unmodifiableMap(fieldToJwt);

        Map<String, String> jwtToField = new java.util.HashMap<>();
        jwtToField.put(JWT_CLAIM_CLIENT_ID, FIELD_CLIENT_ID);
        jwtToField.put(JWT_CLAIM_CLIENT_TYPE, FIELD_CLIENT_TYPE);
        jwtToField.put(JWT_CLAIM_USER_ID, FIELD_USER_ID);
        jwtToField.put(JWT_CLAIM_USERNAME, FIELD_USERNAME);
        jwtToField.put(JWT_CLAIM_SECURITY_CONTEXT, FIELD_SECURITY_CONTEXT);
        jwtToField.put(JWT_CLAIM_SCOPE, FIELD_SCOPE);
        JWT_CLAIM_TO_FIELD = java.util.Collections.unmodifiableMap(jwtToField);
    }

    private SimpleAkskResourceConstant() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
