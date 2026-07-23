package io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant;

import lombok.Getter;

/**
 * Outbox 快照 payload 类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum OutboxPayloadKind {
    /**
     * 原始字符串消息内容。
     */
    STRING("STRING", "字符串"),
    /**
     * JSON 序列化消息内容。
     */
    JSON("JSON", "JSON"),
    /**
     * 空消息内容。
     */
    NULL("NULL", "空值");

    private final String code;
    private final String description;

    OutboxPayloadKind(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据类型代码获取类型。
     *
     * @param code 类型代码
     * @return 类型；不存在时返回 null
     */
    public static OutboxPayloadKind fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OutboxPayloadKind kind : values()) {
            if (kind.code.equalsIgnoreCase(code)) {
                return kind;
            }
        }
        return null;
    }

    /**
     * 判断类型代码是否有效。
     *
     * @param code 类型代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部类型代码。
     *
     * @return 类型代码数组
     */
    public static String[] getAllCodes() {
        OutboxPayloadKind[] values = values();
        String[] codes = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            codes[index] = values[index].code;
        }
        return codes;
    }

    @Override
    public String toString() {
        return code;
    }
}
