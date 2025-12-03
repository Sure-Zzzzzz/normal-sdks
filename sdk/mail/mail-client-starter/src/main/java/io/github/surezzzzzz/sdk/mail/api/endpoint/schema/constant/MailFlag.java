package io.github.surezzzzzz.sdk.mail.api.endpoint.schema.constant;

import lombok.AllArgsConstructor;
import lombok.Getter;

import javax.mail.Flags;

/**
 * @author: Sure.
 * @description
 * @Date: 2025/2/11 18:18
 */

@Getter
@AllArgsConstructor
public enum MailFlag {
    UNREAD(null),    // 未读（特殊处理）
    SEEN(Flags.Flag.SEEN),       // 已读
    RECENT(Flags.Flag.RECENT),   // 最近邮件
    ANSWERED(Flags.Flag.ANSWERED), // 已回复
    DELETED(Flags.Flag.DELETED), // 已删除
    DRAFT(Flags.Flag.DRAFT),     // 草稿
    FLAGGED(Flags.Flag.FLAGGED); // 重要标记

    private final Flags.Flag flag;

    public boolean isUnread() {
        return this == UNREAD;
    }
}