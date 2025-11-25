package io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.request;

import io.github.surezzzzzz.sdk.b2m.sms.api.endpoint.schema.request.entity.TemplateSmsIdAndMobile;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Data
@SuperBuilder
@NoArgsConstructor
public class SendTemplateSmsRequest {

    private TemplateSmsIdAndMobile[] smses;
    private String templateId;

    /**
     * 请求时间
     */
    private long requestTime;

    /**
     * 请求有效时间(秒)<br/>
     * 服务器接受时间与请求时间对比，如果超过有效时间，拒绝此次请求<br/>
     * 防止被网络抓包不断发送同一条请求<br/>
     * 默认1分钟有效期
     */
    private int requestValidPeriod;

    /**
     * 定时时间
     * yyyy-MM-dd HH:mm:ss
     */
    private String timerTime;

    /**
     * 扩展码
     */
    private String extendedCode;

}
