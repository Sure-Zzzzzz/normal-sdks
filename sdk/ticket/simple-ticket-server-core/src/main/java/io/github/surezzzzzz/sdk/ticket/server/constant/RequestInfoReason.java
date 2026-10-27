package io.github.surezzzzzz.sdk.ticket.server.constant;

import lombok.Getter;

/**
 * 待补充原因。
 *
 * @author surezzzzzz
 */
@Getter
public enum RequestInfoReason {

    /**
     * 等提单人补充材料
     */
    AWAIT_MATERIAL("等待提单人补充材料"),

    /**
     * 等第三方
     */
    AWAIT_THIRDPARTY("等待第三方");

    /**
     * 与原枚举名称一致的原因代码。
     */
    private final String code;

    /**
     * 原因说明。
     */
    private final String description;

    RequestInfoReason(String description) {
        this.code = name();
        this.description = description;
    }

    /**
     * 按原因代码查找。
     *
     * @param code 原因代码
     * @return 原因，不存在时返回 null
     */
    public static RequestInfoReason fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (RequestInfoReason reason : values()) {
            if (reason.code.equalsIgnoreCase(code)) {
                return reason;
            }
        }
        return null;
    }

    /**
     * 判断原因代码是否有效。
     *
     * @param code 原因代码
     * @return 是否有效
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部原因代码。
     *
     * @return 原因代码数组
     */
    public static String[] getAllCodes() {
        RequestInfoReason[] reasons = values();
        String[] codes = new String[reasons.length];
        for (int i = 0; i < reasons.length; i++) {
            codes[i] = reasons[i].code;
        }
        return codes;
    }

    /**
     * 返回与原枚举字符串表示一致的代码。
     *
     * @return 原因代码
     */
    @Override
    public String toString() {
        return code;
    }
}
