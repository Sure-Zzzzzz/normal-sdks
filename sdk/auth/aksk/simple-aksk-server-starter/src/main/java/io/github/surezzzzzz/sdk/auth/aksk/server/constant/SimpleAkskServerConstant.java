package io.github.surezzzzzz.sdk.auth.aksk.server.constant;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.core.exception.AkskException;

/**
 * Simple AKSK Server Constants
 *
 * @author surezzzzzz
 */
public final class SimpleAkskServerConstant {

    private SimpleAkskServerConstant() {
        throw new AkskException(ErrorMessage.UTILITY_CLASS_INSTANTIATION);
    }

    // ==================== 配置相关常量 ====================

    /**
     * 配置前缀
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.auth.aksk.server";

    /**
     * 默认Token有效期（秒）
     */
    public static final int DEFAULT_TOKEN_EXPIRES_IN = 3600;

    /**
     * 默认JWT Key ID
     */
    public static final String DEFAULT_JWT_KEY_ID = "sure-auth-aksk-2026";

    // ==================== Redis相关常量 ====================

    /**
     * 默认应用名称
     */
    public static final String DEFAULT_APPLICATION_NAME = "default";

    /**
     * Redis SCAN命令每次扫描的key数量
     */
    public static final int REDIS_SCAN_COUNT = 100;

    // ==================== Client相关常量 ====================

    /**
     * 平台级ClientId前缀: AKP (AccessKey Platform)
     */
    public static final String CLIENT_ID_PREFIX_PLATFORM = "AKP";

    /**
     * 用户级ClientId前缀: AKU (AccessKey User)
     */
    public static final String CLIENT_ID_PREFIX_USER = "AKU";

    /**
     * SecretKey前缀: SK (SecretKey)
     */
    public static final String SECRET_KEY_PREFIX = "SK";

    /**
     * ClientId随机部分长度（Base62字符）
     */
    public static final int CLIENT_ID_RANDOM_LENGTH = 20;

    /**
     * SecretKey随机部分长度（Base62字符）
     */
    public static final int SECRET_KEY_RANDOM_LENGTH = 40;

    /**
     * OAuth2客户端认证方法
     */
    public static final String CLIENT_AUTHENTICATION_METHOD = "client_secret_basic";

    /**
     * OAuth2授权类型
     */
    public static final String AUTHORIZATION_GRANT_TYPE = "client_credentials";

    /**
     * 默认Scope（逗号分隔）
     */
    public static final String DEFAULT_SCOPES = "read,write";

    /**
     * Scope分隔符
     */
    public static final String SCOPE_DELIMITER = ",";

    // ==================== JWT密钥相关常量 ====================

    /**
     * RSA算法名称
     */
    public static final String KEY_ALGORITHM_RSA = "RSA";

    /**
     * Classpath资源前缀
     */
    public static final String KEY_PATH_PREFIX_CLASSPATH = "classpath:";

    /**
     * File资源前缀
     */
    public static final String KEY_PATH_PREFIX_FILE = "file:";

    /**
     * Unix文件路径前缀
     */
    public static final String KEY_PATH_PREFIX_UNIX = "/";

    /**
     * PEM格式标记
     */
    public static final String PEM_BEGIN_MARKER = "-----BEGIN";

    /**
     * PEM格式正则表达式 - BEGIN标记
     */
    public static final String PEM_REGEX_BEGIN = "-----BEGIN [A-Z ]+-----";

    /**
     * PEM格式正则表达式 - END标记
     */
    public static final String PEM_REGEX_END = "-----END [A-Z ]+-----";

    /**
     * 空白字符正则表达式
     */
    public static final String REGEX_WHITESPACE = "\\s+";

    /**
     * 空字符串替换
     */
    public static final String EMPTY_STRING = "";

    /**
     * 换行符
     */
    public static final String LINE_SEPARATOR = "\n";

    // ==================== API参数名称常量 ====================

    /**
     * API参数名: owner_user_id
     */
    public static final String PARAM_OWNER_USER_ID = "owner_user_id";

    /**
     * 默认管理员用户名
     */
    public static final String DEFAULT_ADMIN_USERNAME = "admin";

    // ==================== JWT Claims常量 ====================

    /**
     * JWT Claim名称: client_id
     * 客户端标识（AKSK），与标准claim sub相同，但更明确
     */
    public static final String JWT_CLAIM_CLIENT_ID = "client_id";

    /**
     * JWT Claim名称: auth_server_id
     * 认证服务器标识，用于多认证服务器场景区分token来源
     * 值来自配置项 jwt.key-id，通常与 JWT Header 的 kid 保持一致
     * 用途：API网关（如APISIX）可通过此字段识别token来源，实现多租户或多环境隔离
     */
    public static final String JWT_CLAIM_AUTH_SERVER_ID = "auth_server_id";

    /**
     * JWT Claim名称: client_type
     */
    public static final String JWT_CLAIM_CLIENT_TYPE = "client_type";

    /**
     * JWT Claim名称: user_id
     */
    public static final String JWT_CLAIM_USER_ID = "user_id";

    /**
     * JWT Claim名称: username
     */
    public static final String JWT_CLAIM_USERNAME = "username";

    /**
     * JWT Claim名称: security_context
     * 用于传递自定义的安全上下文信息
     */
    public static final String JWT_CLAIM_SECURITY_CONTEXT = "security_context";

    /**
     * OAuth2 Token请求参数名: security_context
     * 客户端在请求token时可以通过此参数传递自定义的安全上下文数据
     */
    public static final String OAUTH2_PARAM_SECURITY_CONTEXT = "security_context";

}
