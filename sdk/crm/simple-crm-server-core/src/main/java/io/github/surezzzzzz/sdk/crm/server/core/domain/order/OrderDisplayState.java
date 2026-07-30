package io.github.surezzzzzz.sdk.crm.server.core.domain.order;

import lombok.Getter;

/**
 * 订单由履约事实导出的展示状态。
 *
 * @author surezzzzzz
 */
@Getter
public enum OrderDisplayState {

    /**
     * 待投递、履约。
     */
    PENDING_FULFILLMENT("pending_fulfillment", "待投递、履约"),

    /**
     * 部分、已履约。
     */
    PARTIALLY_FULFILLED("partially_fulfilled", "部分、已履约"),

    /**
     * 已履约。
     */
    FULFILLED("fulfilled", "已履约"),

    /**
     * 变更、中、进行。
     */
    CHANGE_IN_PROGRESS("change_in_progress", "变更、中、进行"),

    /**
     * 已关闭。
     */
    CLOSED("closed", "已关闭");

    /**
     * 稳定类型代码。
     */
    private final String code;

    /**
     * 面向使用者的中文说明。
     */
    private final String description;

    OrderDisplayState(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据稳定代码获取枚举值。
     *
     * @param code 稳定类型代码
     * @return 匹配的枚举值；不存在时返回 null
     */
    public static OrderDisplayState fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (OrderDisplayState type : values()) {
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
        OrderDisplayState[] types = values();
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
