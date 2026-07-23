package io.github.surezzzzzz.sdk.messaging.kafka.outbox.constant;

import lombok.Getter;

/**
 * Outbox 投递状态。
 *
 * @author surezzzzzz
 */
@Getter
public enum OutboxStatus {
    /**
     * 可被 worker 正常领取的消息。
     */
    PENDING("PENDING", "待投递"),
    /**
     * 已被 worker 领取并处于租约中的消息。
     */
    PROCESSING("PROCESSING", "投递中"),
    /**
     * 等待下次投递时间到达的消息。
     */
    RETRY_WAIT("RETRY_WAIT", "等待重试"),
    /**
     * 已成功写入消息代理回执的消息。
     */
    SENT("SENT", "已发送"),
    /**
     * 需要人工介入的终态消息。
     */
    POISON("POISON", "毒消息");

    private final String code;
    private final String description;

    OutboxStatus(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据状态代码获取状态。
     *
     * @param code 状态代码
     * @return 状态；不存在时返回 null
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
     * 判断状态代码是否有效。
     *
     * @param code 状态代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部状态代码。
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
