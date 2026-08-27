package io.github.surezzzzzz.sdk.s3.route.constant;

/**
 * S3 Route 常量。
 *
 * @author surezzzzzz
 */
public final class SimpleS3RouteConstant {

    // ==================== 配置常量 ====================

    /**
     * 配置前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.s3.route";

    /**
     * 启用配置名称。
     */
    public static final String CONFIG_PROPERTY_ENABLE = "enable";

    /**
     * 布尔真值。
     */
    public static final String BOOLEAN_TRUE = "true";

    // ==================== 默认配置 ====================

    /**
     * 默认关闭等待时间（毫秒）。
     */
    public static final int DEFAULT_SHUTDOWN_TIMEOUT_MS = 10000;

    /**
     * 默认 Region。
     */
    public static final String DEFAULT_REGION = "us-east-1";

    /**
     * 默认启用 Path Style 寻址。
     */
    public static final boolean DEFAULT_PATH_STYLE_ENABLED = true;

    /**
     * 默认建连超时（毫秒）。
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 10000;

    /**
     * 默认读超时（毫秒）。
     */
    public static final int DEFAULT_SOCKET_TIMEOUT_MS = 50000;

    /**
     * 默认连接池最大连接数（与仓库既有 S3 客户端先例一致）。
     */
    public static final int DEFAULT_MAX_CONNECTIONS = 500;

    /**
     * 默认请求级超时（毫秒），0 表示不启用。
     */
    public static final int DEFAULT_REQUEST_TIMEOUT_MS = 0;

    /**
     * 默认客户端执行级超时（毫秒），0 表示不启用。
     */
    public static final int DEFAULT_CLIENT_EXECUTION_TIMEOUT_MS = 0;

    /**
     * 默认连接最大空闲时间（毫秒）。
     */
    public static final int DEFAULT_CONNECTION_MAX_IDLE_MS = 60000;

    /**
     * 默认连接 TTL（毫秒），-1 表示不限制。
     */
    public static final long DEFAULT_CONNECTION_TTL_MS = -1L;

    // ==================== Endpoint 校验常量 ====================

    /**
     * HTTP 协议。
     */
    public static final String HTTP_SCHEME = "http";

    /**
     * HTTPS 协议。
     */
    public static final String HTTPS_SCHEME = "https";

    /**
     * 根路径。
     */
    public static final String ROOT_PATH = "/";

    // ==================== 签名常量 ====================

    /**
     * S3 V2 签名（HmacSHA1）的 SDK SignerOverride 值，用于部署为 V2 签名的 S3 兼容存储。
     */
    public static final String SIGNER_TYPE_S3_V2 = "S3SignerType";

    // ==================== TLS 与私有 CA 常量 ====================

    /**
     * X.509 证书类型。
     */
    public static final String CERTIFICATE_TYPE_X509 = "X.509";

    /**
     * 私有 CA 信任 KeyStore 类型。
     */
    public static final String TRUSTED_CA_KEY_STORE_TYPE = "JKS";

    /**
     * TLS 上下文协议。
     */
    public static final String TLS_CONTEXT_PROTOCOL = "TLS";

    /**
     * 私有 CA 证书在信任 KeyStore 中的别名模板。
     */
    public static final String TRUSTED_CA_CERTIFICATE_ALIAS_TEMPLATE = "trusted-ca-%d";

    /**
     * KeyUsage 数组中 keyCertSign 位下标。
     */
    public static final int KEY_USAGE_CERT_SIGN_INDEX = 5;

    /**
     * AWS SDK 关闭证书校验的全局系统属性；配置私有 CA 时该运行态被拒绝。
     */
    public static final String AWS_CERT_CHECKING_DISABLED_PROPERTY = "com.amazonaws.sdk.disableCertChecking";

    private SimpleS3RouteConstant() {
    }
}
