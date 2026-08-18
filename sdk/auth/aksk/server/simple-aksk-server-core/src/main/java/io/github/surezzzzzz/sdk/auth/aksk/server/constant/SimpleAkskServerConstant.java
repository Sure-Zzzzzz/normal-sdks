package io.github.surezzzzzz.sdk.auth.aksk.server.constant;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.JwtClaimConstant;
import io.github.surezzzzzz.sdk.auth.aksk.core.exception.AkskException;

import java.util.concurrent.TimeUnit;

/**
 * Simple AKSK Server 常量
 *
 * @author surezzzzzz
 */
public final class SimpleAkskServerConstant {

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
    /**
     * 默认 Security Context 最大大小（字节）
     */
    public static final int DEFAULT_SECURITY_CONTEXT_MAX_SIZE = 4096;
    /**
     * 默认管理员用户名
     */
    public static final String DEFAULT_ADMIN_USERNAME = "admin";

    // ==================== 限流相关常量 ====================
    /**
     * OAuth2端点限流默认开关
     */
    public static final boolean DEFAULT_LIMITER_OAUTH2_ENABLE = true;
    /**
     * 默认限流算法
     */
    public static final String DEFAULT_LIMITER_ALGORITHM = "sliding";
    /**
     * 默认限流Key策略
     */
    public static final String DEFAULT_LIMITER_KEY_STRATEGY = "ip";
    /**
     * Token端点默认降级策略
     */
    public static final String DEFAULT_LIMITER_TOKEN_FALLBACK = "deny";
    /**
     * Introspect端点默认降级策略
     */
    public static final String DEFAULT_LIMITER_INTROSPECT_FALLBACK = "allow";
    /**
     * Revoke端点默认降级策略
     */
    public static final String DEFAULT_LIMITER_REVOKE_FALLBACK = "allow";
    /**
     * 默认限流窗口
     */
    public static final int DEFAULT_LIMITER_WINDOW = 1;
    /**
     * 默认限流窗口单位
     */
    public static final TimeUnit DEFAULT_LIMITER_WINDOW_UNIT = TimeUnit.MINUTES;
    /**
     * Token端点默认限流次数
     */
    public static final int DEFAULT_LIMITER_TOKEN_COUNT = 60;
    /**
     * Introspect端点默认限流次数
     */
    public static final int DEFAULT_LIMITER_INTROSPECT_COUNT = 300;
    /**
     * Revoke端点默认限流次数
     */
    public static final int DEFAULT_LIMITER_REVOKE_COUNT = 120;
    /**
     * OAuth2 Security Filter限流事件来源
     */
    public static final String LIMITER_SOURCE_OAUTH2_FILTER = "OAUTH2_FILTER";
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
    /**
     * OAuth2标准Scope空格分隔符（用于token响应和introspect响应）
     */
    public static final String SCOPE_SEPARATOR_SPACE = " ";
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

    // ==================== JWT Claims常量 ====================
    /**
     * JWT Claim名称: client_id
     */
    public static final String JWT_CLAIM_CLIENT_ID = "client_id";
    /**
     * JWT Claim名称: auth_server_id
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
     */
    public static final String JWT_CLAIM_SECURITY_CONTEXT = "security_context";
    /**
     * JWT Claim名称: aksk_authorization
     */
    public static final String JWT_CLAIM_APPLICATION_AUTHORIZATION =
            JwtClaimConstant.APPLICATION_AUTHORIZATION;

    // ==================== 管理 REST API 授权常量 ====================
    /**
     * AKSK Server 管理应用编码。
     */
    public static final String MANAGEMENT_APPLICATION_CODE = "aksk-server";

    // ==================== 管理资源常量 ====================
    /**
     * Client 管理资源。
     */
    public static final String MANAGEMENT_RESOURCE_CLIENT = "akskClient";
    /**
     * Token 管理资源。
     */
    public static final String MANAGEMENT_RESOURCE_TOKEN = "akskToken";
    /**
     * 应用授权管理资源。
     */
    public static final String MANAGEMENT_RESOURCE_APPLICATION_AUTHORIZATION =
            "akskApplicationAuthorization";

    // ==================== 管理动作常量 ====================
    /**
     * 创建动作。
     */
    public static final String MANAGEMENT_ACTION_CREATE = "create";
    /**
     * 查询动作。
     */
    public static final String MANAGEMENT_ACTION_READ = "read";
    /**
     * 更新动作。
     */
    public static final String MANAGEMENT_ACTION_UPDATE = "update";
    /**
     * 删除动作。
     */
    public static final String MANAGEMENT_ACTION_DELETE = "delete";
    /**
     * 撤销动作。
     */
    public static final String MANAGEMENT_ACTION_REVOKE = "revoke";

    // ==================== 管理权限常量 ====================
    /**
     * 应用授权创建 API 权限。
     */
    public static final String MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_CREATE =
            "akskApplicationAuthorization:create";
    /**
     * 应用授权查询 API 权限。
     */
    public static final String MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_READ =
            "akskApplicationAuthorization:read";
    /**
     * 应用授权更新 API 权限。
     */
    public static final String MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_UPDATE =
            "akskApplicationAuthorization:update";
    /**
     * 应用授权撤销 API 权限。
     */
    public static final String MANAGEMENT_PERMISSION_APPLICATION_AUTHORIZATION_REVOKE =
            "akskApplicationAuthorization:revoke";
    /**
     * Client 创建 API 权限。
     */
    public static final String MANAGEMENT_PERMISSION_CLIENT_CREATE = "akskClient:create";
    /**
     * Client 查询 API 权限。
     */
    public static final String MANAGEMENT_PERMISSION_CLIENT_READ = "akskClient:read";
    /**
     * Client 更新 API 权限。
     */
    public static final String MANAGEMENT_PERMISSION_CLIENT_UPDATE = "akskClient:update";
    /**
     * Client 删除 API 权限。
     */
    public static final String MANAGEMENT_PERMISSION_CLIENT_DELETE = "akskClient:delete";
    /**
     * Token 查询 API 权限。
     */
    public static final String MANAGEMENT_PERMISSION_TOKEN_READ = "akskToken:read";
    /**
     * Token 更新 API 权限。
     */
    public static final String MANAGEMENT_PERMISSION_TOKEN_UPDATE = "akskToken:update";
    /**
     * Token 删除 API 权限。
     */
    public static final String MANAGEMENT_PERMISSION_TOKEN_DELETE = "akskToken:delete";

    // ==================== 管理数据权限维度常量 ====================
    /**
     * 应用编码维度。
     */
    public static final String MANAGEMENT_DIMENSION_APPLICATION_CODE = "applicationCode";
    /**
     * Client 标识维度。
     */
    public static final String MANAGEMENT_DIMENSION_CLIENT_ID = "clientId";
    /**
     * Client 类型维度。
     */
    public static final String MANAGEMENT_DIMENSION_CLIENT_TYPE = "clientType";
    /**
     * Client 所属用户维度。
     */
    public static final String MANAGEMENT_DIMENSION_OWNER_USER_ID = "ownerUserId";
    /**
     * Token 标识维度。
     */
    public static final String MANAGEMENT_DIMENSION_TOKEN_ID = "tokenId";

    // ==================== OAuth2 参数与响应字段常量 ====================
    /**
     * OAuth2 Token请求参数名: security_context
     */
    public static final String OAUTH2_PARAM_SECURITY_CONTEXT = "security_context";
    /**
     * OAuth2 Token请求参数名: grant_type
     */
    public static final String OAUTH2_PARAM_GRANT_TYPE = "grant_type";
    /**
     * OAuth2 Token请求参数名: scope
     */
    public static final String OAUTH2_PARAM_SCOPE = "scope";
    /**
     * OAuth2 Revoke/Introspect请求参数名: token
     */
    public static final String OAUTH2_PARAM_TOKEN = "token";
    /**
     * OAuth2 Token响应字段: access_token
     */
    public static final String OAUTH2_RESPONSE_ACCESS_TOKEN = "access_token";
    /**
     * OAuth2 Token响应字段: token_type
     */
    public static final String OAUTH2_RESPONSE_TOKEN_TYPE = "token_type";
    /**
     * OAuth2 Token响应字段: expires_in
     */
    public static final String OAUTH2_RESPONSE_EXPIRES_IN = "expires_in";

    // ==================== JWE 算法常量 ====================
    /**
     * JWE 密钥加密算法：AES-256 密钥封装
     */
    public static final String JWE_KEY_ENCRYPTION_ALGORITHM = "A256GCMKW";
    /**
     * JWE 内容加密算法：AES-256 伽罗瓦计数器模式
     */
    public static final String JWE_CONTENT_ENCRYPTION_ALGORITHM = "A256GCM";
    /**
     * JWE Header 的 alg 字段值
     */
    public static final String JWE_ALGORITHM_HEADER_VALUE = "A256GCMKW";
    /**
     * AES-256 密钥长度（字节）
     */
    public static final int AES_256_KEY_LENGTH = 32;
    /**
     * JWE Header 的 cty 字段值
     */
    public static final String JWE_CONTENT_TYPE_JWT = "JWT";

    // ==================== Token数据源常量 ====================
    /**
     * Token数据源: MySQL
     */
    public static final String TOKEN_SOURCE_MYSQL = "mysql";
    /**
     * Token数据源: Redis
     */
    public static final String TOKEN_SOURCE_REDIS = "redis";

    // ==================== Spring属性常量 ====================
    /**
     * Spring属性: server.port
     */
    public static final String SPRING_PROPERTY_SERVER_PORT = "server.port";
    /**
     * 默认服务端口
     */
    public static final String DEFAULT_SERVER_PORT = "8080";

    // ==================== HTTP认证常量 ====================
    /**
     * HTTP Basic认证前缀
     */
    public static final String HTTP_BASIC_AUTH_PREFIX = "Basic ";

    // ==================== OAuth2错误码常量 ====================
    /**
     * OAuth2错误码: invalid_scope
     */
    public static final String OAUTH2_ERROR_INVALID_SCOPE = "invalid_scope";
    /**
     * OAuth2错误码: security_context_too_large
     */
    public static final String OAUTH2_ERROR_SECURITY_CONTEXT_TOO_LARGE = "security_context_too_large";

    // ==================== Admin API响应字段常量 ====================
    /**
     * Admin API响应字段: success
     */
    public static final String ADMIN_RESPONSE_SUCCESS = "success";
    /**
     * Admin API响应字段: status
     */
    public static final String ADMIN_RESPONSE_STATUS = "status";
    /**
     * Admin API响应字段: message
     */
    public static final String ADMIN_RESPONSE_MESSAGE = "message";
    /**
     * Admin API响应字段: deletedCount
     */
    public static final String ADMIN_RESPONSE_DELETED_COUNT = "deletedCount";

    private SimpleAkskServerConstant() {
        throw new AkskException(ErrorMessage.UTILITY_CLASS_INSTANTIATION);
    }

}
