package io.github.surezzzzzz.sdk.iam.client.model;

import lombok.Builder;
import lombok.Value;

import java.time.Instant;

/**
 * 站内信投影（messages 族响应模型，1.3.7）。
 *
 * @author surezzzzzz
 */
@Value
@Builder
public class IamMessage {

    /**
     * 标题。
     */
    String title;

    /**
     * 内容。
     */
    String content;

    /**
     * 发送时间。
     */
    Instant createdAt;

    /**
     * 收件人是否已读。
     */
    Boolean read;
}
