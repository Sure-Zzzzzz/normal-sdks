package io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 站内信发送响应
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class MessageSendResponse {

    private String sendBatchId;

    /**
     * 实际收件人数，面向全量发送结果使用 long，避免人数增长后的计数溢出。
     */
    private long recipientCount;
}
