package io.github.surezzzzzz.sdk.b2m.sms.constant;

/**
 * B2M 短信客户端常量。
 *
 * @author surezzzzzz
 */
public final class SmsConstant {

    /**
     * 配置前缀。
     */
    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.b2m.sms";

    // ==================== 配置 ====================
    /**
     * 模板变量短信端点（平台默认）。
     */
    public static final String DEFAULT_TEMPLATE_URL = "http://bjksmtn.b2m.cn/inter/sendTemplateVariableSMS";
    /**
     * 单条短信端点（平台默认）。
     */
    public static final String DEFAULT_SINGLE_URL = "http://bjksmtn.b2m.cn/inter/sendSingleSMS";
    /**
     * 默认加密算法（平台协议约定）。
     */
    public static final String DEFAULT_ALGORITHM = "AES/ECB/PKCS5Padding";
    /**
     * 默认报文编码。
     */
    public static final String DEFAULT_ENCODE = "UTF-8";
    /**
     * 默认开启请求体 GZIP 压缩。
     */
    public static final boolean DEFAULT_GZIP = true;
    /**
     * 默认请求有效期（秒）。
     */
    public static final int DEFAULT_VALID_PERIOD = 60;
    /**
     * AES 密钥合法字节长度。
     */
    public static final int[] AES_KEY_LENGTHS = {16, 24, 32};

    // ==================== 加密 ====================
    /**
     * PKCS7 填充算法后缀（需要使用方自行引入 BouncyCastle）。
     */
    public static final String ALGORITHM_PKCS7_SUFFIX = "PKCS7Padding";
    /**
     * PKCS7 所需 JCE Provider 名。
     */
    public static final String JCE_PROVIDER_BC = "BC";
    /**
     * 密钥算法名。
     */
    public static final String KEY_ALGORITHM = "AES";
    /**
     * 默认连接超时（毫秒）。
     */
    public static final int DEFAULT_CONNECT_TIMEOUT_MS = 5000;

    // ==================== HTTP ====================
    /**
     * 默认读取超时（毫秒）。
     */
    public static final int DEFAULT_READ_TIMEOUT_MS = 10000;
    /**
     * 平台成功 HTTP 状态码上限（含）。
     */
    public static final int HTTP_OK_MAX = 299;
    /**
     * RestTemplate bean 名（自建专用实例，不注入应用容器）。
     */
    public static final String BEAN_SMS_REST_TEMPLATE = "smsRestTemplate";
    /**
     * 单条短信的日志模板标识（门面日志用，与模板短信的 templateId 同位）。
     */
    public static final String TEMPLATE_SINGLE = "single";
    /**
     * 请求头：gzip 开关。
     */
    public static final String HEADER_GZIP = "gzip";
    /**
     * 请求头：gzip 开启值。
     */
    public static final String HEADER_GZIP_ON = "on";
    /**
     * 请求头：应用 ID。
     */
    public static final String HEADER_APP_ID = "appId";
    /**
     * 请求头：编码。
     */
    public static final String HEADER_ENCODE = "encode";

    private SmsConstant() {
        throw new UnsupportedOperationException("常量类不能实例化");
    }
}
