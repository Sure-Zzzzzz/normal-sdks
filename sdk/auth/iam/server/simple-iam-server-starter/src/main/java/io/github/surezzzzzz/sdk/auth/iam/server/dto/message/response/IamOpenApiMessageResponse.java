package io.github.surezzzzzz.sdk.auth.iam.server.dto.message.response;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.Instant;

/**
 * 开放 API 站内信响应（1.3.7）：外部对账所需最小字段集，不带内部收件人 userId。
 *
 * @author surezzzzzz
 */
@Data
@AllArgsConstructor
public class IamOpenApiMessageResponse {

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 发送时间
     */
    private Instant createdAt;

    /**
     * 收件人是否已读
     */
    private Boolean read;
}
