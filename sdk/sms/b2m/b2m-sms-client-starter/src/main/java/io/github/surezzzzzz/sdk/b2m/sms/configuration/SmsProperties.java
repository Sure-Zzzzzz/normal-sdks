package io.github.surezzzzzz.sdk.b2m.sms.configuration;

import io.github.surezzzzzz.sdk.b2m.sms.constant.SmsConstant;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * B2M 短信配置（单一 Properties）：enable 默认 false；enable=true 时 appId/secretKey 必填
 * （secretKey 敏感，只进 application-local.yml；启动校验 16/24/32 字节）；其余为平台协议参数默认值覆盖位。
 * enable=false 时 SmsClient 不装配，上游 adaptor（如 IAM 短信投递）随之整体消失——
 * 不产生"装配了但投递不可用"的静默态。
 *
 * @author surezzzzzz
 */
@Data
@ConfigurationProperties(prefix = SmsConstant.CONFIG_PREFIX)
public class SmsProperties {

    /**
     * 是否启用（默认 false）。
     */
    private boolean enable = false;

    /**
     * B2M 平台分配的应用 ID（enable=true 时必填）。
     */
    private String appId;

    /**
     * AES 密钥（enable=true 时必填，敏感：只进 application-local.yml；启动校验 16/24/32 字节）。
     */
    private String secretKey;

    /**
     * 模板变量短信端点（默认平台端点，私有化部署时覆盖）。
     */
    private String templateUrl = SmsConstant.DEFAULT_TEMPLATE_URL;

    /**
     * 单条短信端点（默认平台端点）。
     */
    private String singleUrl = SmsConstant.DEFAULT_SINGLE_URL;

    /**
     * 加密算法（平台协议约定；换 PKCS7Padding 需自行引入 BouncyCastle，缺失显式启动失败）。
     */
    private String algorithm = SmsConstant.DEFAULT_ALGORITHM;

    /**
     * 报文编码。
     */
    private String encode = SmsConstant.DEFAULT_ENCODE;

    /**
     * 请求体 GZIP 压缩开关。
     */
    private boolean gzip = SmsConstant.DEFAULT_GZIP;

    /**
     * 请求有效期（秒）。
     */
    private int validPeriod = SmsConstant.DEFAULT_VALID_PERIOD;

    /**
     * HTTP 连接超时（毫秒）。
     */
    private int connectTimeoutMs = SmsConstant.DEFAULT_CONNECT_TIMEOUT_MS;

    /**
     * HTTP 读取超时（毫秒）。
     */
    private int readTimeoutMs = SmsConstant.DEFAULT_READ_TIMEOUT_MS;
}
