package io.github.surezzzzzz.sdk.ticket.server.constant;

import lombok.Getter;

/**
 * 工单状态。
 *
 * @author surezzzzzz
 */
@Getter
public enum TicketStatus {

    /**
     * 已提交（待受理）
     */
    SUBMITTED("已提交"),

    /**
     * 处理中
     */
    PROCESSING("处理中"),

    /**
     * 待补充（计时暂停）
     */
    PENDING_INFO("待补充"),

    /**
     * 已解决（待确认）
     */
    RESOLVED("已解决"),

    /**
     * 已关闭（终态）
     */
    CLOSED("已关闭"),

    /**
     * 已撤销（终态）
     */
    WITHDRAWN("已撤销");

    /**
     * 与原枚举名称一致的状态代码。
     */
    private final String code;

    /**
     * 状态说明。
     */
    private final String description;

    TicketStatus(String description) {
        this.code = name();
        this.description = description;
    }

    /**
     * 按状态代码查找。
     *
     * @param code 状态代码
     * @return 状态，不存在时返回 null
     */
    public static TicketStatus fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TicketStatus status : values()) {
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
        TicketStatus[] statuses = values();
        String[] codes = new String[statuses.length];
        for (int i = 0; i < statuses.length; i++) {
            codes[i] = statuses[i].code;
        }
        return codes;
    }

    /**
     * 返回与原枚举字符串表示一致的代码。
     *
     * @return 状态代码
     */
    @Override
    public String toString() {
        return code;
    }
}
