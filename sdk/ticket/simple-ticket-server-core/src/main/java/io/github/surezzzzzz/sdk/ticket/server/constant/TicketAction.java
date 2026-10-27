package io.github.surezzzzzz.sdk.ticket.server.constant;

import lombok.Getter;

/**
 * 工单动作（状态机入口全集，DESIGN §6.4 全枚举；COMMENT 不改状态不在此表）。
 *
 * @author surezzzzzz
 */
@Getter
public enum TicketAction {

    /**
     * 受理（认领）
     */
    CLAIM("受理"),

    /**
     * 分派（指定组与人）
     */
    ASSIGN("分派"),

    /**
     * 转派（换组/换人）
     */
    TRANSFER("转派"),

    /**
     * 待补充（计时暂停）
     */
    REQUEST_INFO("待补充"),

    /**
     * 补充（计时恢复）
     */
    SUPPLY("补充"),

    /**
     * 办结（生成确认期限）
     */
    RESOLVE("办结"),

    /**
     * 确认关闭
     */
    CONFIRM("确认关闭"),

    /**
     * 不认可重开（回处理中）
     */
    REJECT("不认可重开"),

    /**
     * 撤销（终态）
     */
    WITHDRAW("撤销"),

    /**
     * 确认超时自动关闭（系统）
     */
    CONFIRM_TIMEOUT("确认超时自动关闭");

    /**
     * 与原枚举名称一致的动作代码。
     */
    private final String code;

    /**
     * 动作说明。
     */
    private final String description;

    TicketAction(String description) {
        this.code = name();
        this.description = description;
    }

    /**
     * 按动作代码查找。
     *
     * @param code 动作代码
     * @return 动作，不存在时返回 null
     */
    public static TicketAction fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TicketAction action : values()) {
            if (action.code.equalsIgnoreCase(code)) {
                return action;
            }
        }
        return null;
    }

    /**
     * 判断动作代码是否有效。
     *
     * @param code 动作代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部动作代码。
     *
     * @return 动作代码数组
     */
    public static String[] getAllCodes() {
        TicketAction[] actions = values();
        String[] codes = new String[actions.length];
        for (int i = 0; i < actions.length; i++) {
            codes[i] = actions[i].code;
        }
        return codes;
    }

    /**
     * 返回与原枚举字符串表示一致的代码。
     *
     * @return 动作代码
     */
    @Override
    public String toString() {
        return code;
    }
}
