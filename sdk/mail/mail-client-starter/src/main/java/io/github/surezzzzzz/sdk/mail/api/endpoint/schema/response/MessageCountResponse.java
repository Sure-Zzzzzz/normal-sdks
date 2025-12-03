package io.github.surezzzzzz.sdk.mail.api.endpoint.schema.response;

import lombok.*;

/**
 * @author: Sure.
 * @description
 * @Date: 2025/2/11 13:59
 */
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class MessageCountResponse {
    // 邮件总数
    private int total;
    // 未读邮件数
    private int unread;
    // 新邮件数（不等同于未读）
    private int recent;
    // 已删除但未清除的邮件数
    private int deleted;

}