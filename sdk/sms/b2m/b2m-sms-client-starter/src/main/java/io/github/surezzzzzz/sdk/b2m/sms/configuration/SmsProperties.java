package io.github.surezzzzzz.sdk.b2m.sms.configuration;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/3/8 16:56
 */
@Getter
@Setter
@NoArgsConstructor
@SmsComponent
@ConfigurationProperties(prefix = "io.github.surezzzzzz.sdk.b2m.sms")
public class SmsProperties {

    // appId
    private String appId;
    // 密钥
    private String secretKey;
    // 接口地址
    private String templateUrl = "http://bjksmtn.b2m.cn/inter/sendTemplateVariableSMS";
    // 接口地址
    private String singleUrl = "http://bjksmtn.b2m.cn/inter/sendSingleSMS";
    // 加密算法
    private String algorithm = "AES/ECB/PKCS5Padding";
    // 编码
    private String encode = "UTF-8";
    // 是否压缩
    private boolean isGzip = true;

    private int validPeriod = 60;
}
