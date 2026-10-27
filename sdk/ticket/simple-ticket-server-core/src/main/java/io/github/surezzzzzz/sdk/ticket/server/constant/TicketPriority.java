package io.github.surezzzzzz.sdk.ticket.server.constant;

import lombok.Getter;

/**
 * 工单优先级（仅排序与展示，不影响 SLA 时限——DESIGN §6.3 钉死）。
 *
 * @author surezzzzzz
 */
@Getter
public enum TicketPriority {

    /**
     * 低
     */
    LOW(1, "低"),

    /**
     * 普通
     */
    NORMAL(2, "普通"),

    /**
     * 高
     */
    HIGH(3, "高"),

    /**
     * 紧急
     */
    URGENT(4, "紧急");

    /**
     * 与原枚举名称一致的优先级代码。
     */
    private final String code;

    /**
     * 优先级说明。
     */
    private final String description;

    /**
     * 队列排序权重，值越大优先级越高。
     */
    private final int sortOrder;

    TicketPriority(int sortOrder, String description) {
        this.code = name();
        this.description = description;
        this.sortOrder = sortOrder;
    }

    /**
     * 按优先级代码查找。
     *
     * @param code 优先级代码
     * @return 优先级，不存在时返回 null
     */
    public static TicketPriority fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TicketPriority priority : values()) {
            if (priority.code.equalsIgnoreCase(code)) {
                return priority;
            }
        }
        return null;
    }

    /**
     * 判断优先级代码是否有效。
     *
     * @param code 优先级代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部优先级代码。
     *
     * @return 优先级代码数组
     */
    public static String[] getAllCodes() {
        TicketPriority[] priorities = values();
        String[] codes = new String[priorities.length];
        for (int i = 0; i < priorities.length; i++) {
            codes[i] = priorities[i].code;
        }
        return codes;
    }

    /**
     * 返回与原枚举字符串表示一致的代码。
     *
     * @return 优先级代码
     */
    @Override
    public String toString() {
        return code;
    }
}
