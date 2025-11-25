package io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author: Sure.
 * @description
 * @Date: 2024/3/21 15:49
 */

@Data
@SuperBuilder
@NoArgsConstructor
public class SmsSingleRequest {
    /**
     * 定时时间
     * yyyy-MM-dd HH:mm:ss
     */
    private String timerTime;

    /**
     * 扩展码
     */
    private String extendedCode;
    /**
     * 电话号码
     */
    private String mobile;

    /**
     * 短信内容
     */
    private String content;

    /**
     * 自定义smsid
     */
    private String customSmsId;
}
