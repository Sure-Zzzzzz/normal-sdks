package io.github.surezzzzzz.sdk.ticket.server.constant;

import lombok.Getter;

/**
 * 工单来源。
 *
 * @author surezzzzzz
 */
@Getter
public enum TicketSource {

    /**
     * 门户页面
     */
    PORTAL("门户页面"),

    /**
     * 机器开放接口
     */
    OPENAPI("机器开放接口");

    /**
     * 与原枚举名称一致的来源代码。
     */
    private final String code;

    /**
     * 来源说明。
     */
    private final String description;

    TicketSource(String description) {
        this.code = name();
        this.description = description;
    }

    /**
     * 按来源代码查找。
     *
     * @param code 来源代码
     * @return 来源，不存在时返回 null
     */
    public static TicketSource fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (TicketSource source : values()) {
            if (source.code.equalsIgnoreCase(code)) {
                return source;
            }
        }
        return null;
    }

    /**
     * 判断来源代码是否有效。
     *
     * @param code 来源代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部来源代码。
     *
     * @return 来源代码数组
     */
    public static String[] getAllCodes() {
        TicketSource[] sources = values();
        String[] codes = new String[sources.length];
        for (int i = 0; i < sources.length; i++) {
            codes[i] = sources[i].code;
        }
        return codes;
    }

    /**
     * 返回与原枚举字符串表示一致的代码。
     *
     * @return 来源代码
     */
    @Override
    public String toString() {
        return code;
    }
}
