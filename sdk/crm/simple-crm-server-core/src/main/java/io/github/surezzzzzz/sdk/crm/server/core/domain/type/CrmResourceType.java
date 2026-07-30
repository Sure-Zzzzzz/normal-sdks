package io.github.surezzzzzz.sdk.crm.server.core.domain.type;

import lombok.Getter;

/**
 * CRM 稳定资源类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum CrmResourceType {

    /**
     * 客户。
     */
    CUSTOMER("customer", "客户"),

    /**
     * 联系人。
     */
    CONTACT("contact", "联系人"),

    /**
     * 商品或服务。
     */
    OFFERING("offering", "商品或服务"),

    /**
     * 报价。
     */
    QUOTATION("quotation", "报价"),

    /**
     * 订单。
     */
    ORDER("order", "订单"),

    /**
     * 履约、项。
     */
    FULFILLMENT_ITEM("fulfillment_item", "履约、项");

    /**
     * 稳定类型代码。
     */
    private final String code;

    /**
     * 面向使用者的中文说明。
     */
    private final String description;

    CrmResourceType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据稳定代码获取枚举值。
     *
     * @param code 稳定类型代码
     * @return 匹配的枚举值；不存在时返回 null
     */
    public static CrmResourceType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CrmResourceType type : values()) {
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
        CrmResourceType[] types = values();
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
