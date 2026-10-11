package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 站内信发送结果（messages 族，1.3.7）。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamMessageSendResult {

    /**
     * 发送批次标识（对账键）。
     */
    String sendBatchId;

    /**
     * 命中收件人数。
     */
    long recipientCount;
}
