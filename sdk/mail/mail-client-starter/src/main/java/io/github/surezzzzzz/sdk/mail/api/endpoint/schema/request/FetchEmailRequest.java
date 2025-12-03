package io.github.surezzzzzz.sdk.mail.api.endpoint.schema.request;

import io.github.surezzzzzz.sdk.mail.api.endpoint.schema.constant.MailFlag;
import lombok.*;

/**
 * @author: Sure.
 * @description
 * @Date: 2025/2/13 10:51
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FetchEmailRequest {
    private int pageSize = 10; // 默认每页10封邮件
    private MailFlag mailFlag; // 邮件标志（例如UNREAD、SEEN等）
    private String subjectKeyword; // 主题关键字，用于模糊搜索
}