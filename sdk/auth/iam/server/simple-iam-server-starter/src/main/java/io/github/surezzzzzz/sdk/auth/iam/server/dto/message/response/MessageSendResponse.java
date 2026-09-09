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

    private int recipientCount;
}
