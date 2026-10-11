package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

/**
 * 站内信发送批次详情（messages 族，1.3.7）。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamMessageBatch {

    /**
     * 批次标识。
     */
    String sendBatchId;

    /**
     * 标题。
     */
    String title;

    /**
     * 内容。
     */
    String content;

    /**
     * 发送者标识（外部主体为 aksk:{clientId}）。
     */
    String senderUsername;

    /**
     * 收件用户数。
     */
    int targetUserCount;

    /**
     * 收件部门数。
     */
    int targetDepartmentCount;
}
