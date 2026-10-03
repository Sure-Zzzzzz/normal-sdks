package io.github.surezzzzzz.sdk.s3.route.constant;

/**
 * S3 Route 受控错误消息。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    // ==================== Target 错误 ====================

    /**
     * target 未登记。
     */
    public static final String TARGET_NOT_REGISTERED = "target 未登记";

    /**
     * targetKey 非法。
     */
    public static final String TARGET_KEY_ILLEGAL = "targetKey 非法";

    // ==================== 配置错误 ====================

    /**
     * target 配置非法。
     */
    public static final String TARGET_CONFIGURATION_ILLEGAL = "target 配置非法";

    /**
     * 配置私有 CA 时 endpoint 必须为 HTTPS。
     */
    public static final String TRUSTED_CA_ENDPOINT_NOT_HTTPS = "配置 trusted-ca-file 时，target endpoint 必须使用 HTTPS";

    /**
     * 私有 CA 文件不存在、不是普通文件或不可读，格式化参数为文件路径。
     */
    public static final String TRUSTED_CA_FILE_NOT_READABLE = "私有 CA 文件不存在、不是普通文件或不可读：%s";

    /**
     * 私有 CA 文件无法解析为 X.509 证书，格式化参数为文件路径。
     */
    public static final String TRUSTED_CA_FILE_INVALID = "私有 CA 文件无法解析为 X.509 证书：%s";

    /**
     * 私有 CA 信任链初始化失败（无文件路径上下文的失败场景）。
     */
    public static final String TRUSTED_CA_INIT_FAILED = "私有 CA 信任链初始化失败";

    /**
     * 私有 CA 文件不包含有效 X.509 证书，格式化参数为文件路径。
     */
    public static final String TRUSTED_CA_FILE_EMPTY = "私有 CA 文件不包含有效 X.509 证书：%s";

    /**
     * 私有 CA 文件包含非 CA 证书，格式化参数为文件路径。
     */
    public static final String TRUSTED_CA_NOT_CA = "私有 CA 文件包含非 CA 证书：%s";

    /**
     * 私有 CA 证书未声明 keyCertSign 用途，格式化参数为文件路径。
     */
    public static final String TRUSTED_CA_KEY_USAGE_INVALID = "私有 CA 证书未声明 keyCertSign 用途：%s";

    /**
     * 私有 CA 证书不在有效期内，格式化参数为文件路径。
     */
    public static final String TRUSTED_CA_NOT_VALID = "私有 CA 证书不在有效期内：%s";

    /**
     * AWS SDK 全局关闭证书校验的运行态被拒绝。
     */
    public static final String AWS_CERT_CHECKING_DISABLED = "检测到 AWS SDK 全局证书校验已关闭，不允许与 target 客户端安全配置同时使用";

    // ==================== 请求错误 ====================

    /**
     * 请求非法。
     */
    public static final String REQUEST_ILLEGAL = "请求非法";

    // ==================== 生命周期错误 ====================

    /**
     * Route 已关闭。
     */
    public static final String ROUTE_CLOSED = "Route 已关闭";

    private ErrorMessage() {
    }
}
