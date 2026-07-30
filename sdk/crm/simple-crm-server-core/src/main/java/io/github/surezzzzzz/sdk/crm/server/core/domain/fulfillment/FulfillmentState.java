package io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment;

import lombok.Getter;

/**
 * 履约项展示状态。
 *
 * @author surezzzzzz
 */
@Getter
public enum FulfillmentState {

    /**
     * 待投递、派发。
     */
    PENDING_DISPATCH("pending_dispatch", "待投递、派发"),

    /**
     * 已派发。
     */
    DISPATCHED("dispatched", "已派发"),

    /**
     * 已受理。
     */
    ACCEPTED("accepted", "已受理"),

    /**
     * 处理中。
     */
    PROCESSING("processing", "处理中"),

    /**
     * 已履约。
     */
    FULFILLED("fulfilled", "已履约"),

    /**
     * 失败。
     */
    FAILED("failed", "失败"),

    /**
     * 已撤销。
     */
    REVOKED("revoked", "已撤销");

    /**
     * 稳定类型代码。
     */
    private final String code;

    /**
     * 面向使用者的中文说明。
     */
    private final String description;

    FulfillmentState(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据稳定代码获取枚举值。
     *
     * @param code 稳定类型代码
     * @return 匹配的枚举值；不存在时返回 null
     */
    public static FulfillmentState fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (FulfillmentState type : values()) {
            if (type.code.equalsIgnoreCase(code)) {
                return type;
            }
        }
        return null;
    }

    /**
     * 判断稳定代码是否有效。
     *
     * @param code 稳定类型代码
     * @return 有效时返回 true
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部稳定类型代码。
     *
     * @return 类型代码数组
     */
    public static String[] getAllCodes() {
        FulfillmentState[] types = values();
        String[] codes = new String[types.length];
        for (int index = 0; index < types.length; index++) {
            codes[index] = types[index].code;
        }
        return codes;
    }

    /**
     * 返回稳定类型代码。
     *
     * @return 稳定类型代码
     */
    /**
     * 返回稳定类型代码。
     *
     * @return 处理后的领域事实或校验结果。
     */
    @Override
    public String toString() {
        return code;
    }
}
