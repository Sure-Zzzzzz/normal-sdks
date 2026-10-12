package io.github.surezzzzzz.sdk.iam.feign.client.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 站内信发送结果 wire 模型（1.3.7）
 *
 * @author surezzzzzz
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class IamMessageSendResultResponse {
    /**
     * 批次标识。
     */
    public String sendBatchId;
    /**
     * 命中收件人数。
     */
    public long recipientCount;
}
