package io.github.surezzzzzz.sdk.b2m.sms.model;

import lombok.Builder;
import lombok.Getter;

/**
 * 短信发送结果：平台业务结果进本对象（不抛异常），SDK 故障才抛 {@code SmsException}。
 * 不携带 ResponseEntity 等框架类型，不含手机号明文。
 *
 * @author surezzzzzz
 */
@Getter
@Builder
public class SmsSendResult {

    /**
     * 平台是否受理成功（HTTP 2xx 且回执解析成功；平台结果码口径以 B2M 平台文档定值为准）。
     */
    private final boolean success;

    /**
     * 结果码：成功时为回执 smsId，失败时为 HTTP 状态码文本。
     */
    private final String resultCode;

    /**
     * 结果说明（不含手机号明文与报文内容）。
     */
    private final String message;

    /**
     * 本次发送的自定义追踪 ID（调用方可传业务标识，跨层日志关联用）。
     */
    private final String customSmsId;
}
