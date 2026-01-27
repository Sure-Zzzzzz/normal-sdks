package io.github.surezzzzzz.sdk.auth.aksk.resource.securitycontext.constant;

/**
 * Simple AKSK Security Context 常量
 *
 * <p>定义 Security Context Starter 使用的常量。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public class SimpleAkskSecurityContextConstant {

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.aksk.resource.security-context";

    /**
     * Request Attribute Key - 用于存储安全上下文
     */
    public static final String CONTEXT_ATTRIBUTE = "AKSK_SECURITY_CONTEXT";

    /**
     * 默认 Header 前缀
     */
    public static final String DEFAULT_HEADER_PREFIX = "x-sure-auth-aksk-";

    /**
     * 预定义的 Header 名称（移除前缀后的 camelCase 形式）
     */
    public static final String HEADER_USER_ID = "userId";
    public static final String HEADER_USERNAME = "username";
    public static final String HEADER_CLIENT_ID = "clientId";
    public static final String HEADER_ROLES = "roles";
    public static final String HEADER_SCOPE = "scope";
    public static final String HEADER_SECURITY_CONTEXT = "securityContext";

    private SimpleAkskSecurityContextConstant() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
}
