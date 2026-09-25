package io.github.surezzzzzz.sdk.b2m.sms.constant;

/**
 * B2M 短信客户端错误码。
 *
 * @author surezzzzzz
 */
public final class ErrorCode {

    /**
     * enable 已开但必填配置缺失。
     */
    public static final String CONFIG_REQUIRED_MISSING = "SMS_CONFIG_001";

    // ==================== 配置错误（启动期） ====================
    /**
     * AES 密钥长度非法（须 16/24/32 字节）。
     */
    public static final String CONFIG_KEY_LENGTH_INVALID = "SMS_CONFIG_002";
    /**
     * 请求体加密失败。
     */
    public static final String CRYPTO_ENCRYPT_FAILED = "SMS_CRYPTO_001";

    // ==================== 加密错误（运行期） ====================
    /**
     * 响应体解密失败。
     */
    public static final String CRYPTO_DECRYPT_FAILED = "SMS_CRYPTO_002";
    /**
     * PKCS7 算法需要 BouncyCastle，类路径缺失。
     */
    public static final String CRYPTO_PROVIDER_MISSING = "SMS_CRYPTO_003";
    /**
     * 平台 HTTP 通信失败。
     */
    public static final String COMM_REQUEST_FAILED = "SMS_COMM_001";

    // ==================== 通信错误（运行期） ====================
    /**
     * 响应体解析失败。
     */
    public static final String COMM_RESPONSE_PARSE_FAILED = "SMS_COMM_002";

    /**
     * 请求体序列化失败。
     */
    public static final String COMM_REQUEST_SERIALIZE_FAILED = "SMS_COMM_003";

    private ErrorCode() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }
}
