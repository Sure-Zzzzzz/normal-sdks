package io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant;

import lombok.Getter;

/**
 * Outbox 状态
 *
 * @author surezzzzzz
 */
@Getter
public enum OutboxStatus {
    PENDING("PENDING", "待投递"),
    PROCESSING("PROCESSING", "投递中"),
    RETRY_WAIT("RETRY_WAIT", "等待重试"),
    SENT("SENT", "已发送"),
    POISON("POISON", "毒消息");

    private final String code;
    private final String description;

    OutboxStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据代码获取状态
     *
     * @param code 状态代码
     * @return 状态，不存在时返回 null
     */
    public static OutboxStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OutboxStatus status : values()) {
            if (status.code.equalsIgnoreCase(code)) {
                return status;
            }
        }
        return null;
    }

    /**
     * 判断状态代码是否有效
     *
     * @param code 状态代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部状态代码
     *
     * @return 状态代码数组
     */
    public static String[] getAllCodes() {
        OutboxStatus[] values = values();
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
