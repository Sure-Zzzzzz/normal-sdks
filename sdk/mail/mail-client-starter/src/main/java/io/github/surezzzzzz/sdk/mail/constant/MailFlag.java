package io.github.surezzzzzz.sdk.mail.constant;

import lombok.Getter;

import javax.mail.Flags;

/**
 * Mail 标志
 *
 * @author surezzzzzz
 */
@Getter
public enum MailFlag {

    UNREAD("unread", "未读", null),
    SEEN("seen", "已读", Flags.Flag.SEEN),
    RECENT("recent", "最近邮件", Flags.Flag.RECENT),
    ANSWERED("answered", "已回复", Flags.Flag.ANSWERED),
    DELETED("deleted", "已删除", Flags.Flag.DELETED),
    DRAFT("draft", "草稿", Flags.Flag.DRAFT),
    FLAGGED("flagged", "重要标记", Flags.Flag.FLAGGED);

    private final String code;
    private final String description;
    private final Flags.Flag flag;

    MailFlag(String code, String description, Flags.Flag flag) {
        this.code = code;
        this.description = description;
        this.flag = flag;
    }

    public boolean isUnread() {
        return this == UNREAD;
    }

    public static MailFlag fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (MailFlag type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    public static String[] getAllCodes() {
        MailFlag[] types = values();
        String[] codes = new String[types.length];
        for (int i = 0; i < types.length; i++) {
            codes[i] = types[i].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
