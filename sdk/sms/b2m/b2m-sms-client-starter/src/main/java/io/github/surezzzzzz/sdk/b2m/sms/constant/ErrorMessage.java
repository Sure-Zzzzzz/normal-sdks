package io.github.surezzzzzz.sdk.b2m.sms.constant;

/**
 * B2M 短信客户端错误信息（与 {@link ErrorCode} 一一对应）。
 *
 * @author surezzzzzz
 */
public final class ErrorMessage {

    public static final String CONFIG_REQUIRED_MISSING = "B2M短信配置缺失：enable=true 时 appId 与 secretKey 必填";
    public static final String CONFIG_KEY_LENGTH_INVALID = "B2M短信密钥长度非法：须为 16/24/32 字节，当前 %d 字节";
    public static final String CRYPTO_ENCRYPT_FAILED = "B2M短信请求体加密失败";
    public static final String CRYPTO_DECRYPT_FAILED = "B2M短信响应体解密失败";
    public static final String CRYPTO_PROVIDER_MISSING = "B2M短信算法 %s 需要 BouncyCastle，请引入 bctls/bcprov 依赖";
    public static final String COMM_REQUEST_FAILED = "B2M短信平台通信失败：HTTP %d";
    public static final String COMM_RESPONSE_PARSE_FAILED = "B2M短信响应体解析失败";
    public static final String COMM_REQUEST_SERIALIZE_FAILED = "B2M短信请求体序列化失败";

    private ErrorMessage() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }
}
