package io.github.surezzzzzz.sdk.crm.server.core.domain.event;

import lombok.Getter;

/**
 * CRM 首发内部领域事件类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum CrmDomainEventType {

    /**
     * 客户、已创建。
     */
    CUSTOMER_CREATED("customer_created", "客户、已创建"),

    /**
     * 联系人、已创建。
     */
    CONTACT_CREATED("contact_created", "联系人、已创建"),

    /**
     * 商品或服务、已创建。
     */
    OFFERING_CREATED("offering_created", "商品或服务、已创建"),

    /**
     * 报价、已创建。
     */
    QUOTATION_CREATED("quotation_created", "报价、已创建"),

    /**
     * 报价、已签发。
     */
    QUOTATION_ISSUED("quotation_issued", "报价、已签发"),

    /**
     * 报价、已确认。
     */
    QUOTATION_CONFIRMED("quotation_confirmed", "报价、已确认"),

    /**
     * 订单、已创建。
     */
    ORDER_CREATED("order_created", "订单、已创建"),

    /**
     * 履约、已创建。
     */
    FULFILLMENT_CREATED("fulfillment_created", "履约、已创建");

    /**
     * 稳定类型代码。
     */
    private final String code;

    /**
     * 面向使用者的中文说明。
     */
    private final String description;

    CrmDomainEventType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据稳定代码获取枚举值。
     *
     * @param code 稳定类型代码
     * @return 匹配的枚举值；不存在时返回 null
     */
    public static CrmDomainEventType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CrmDomainEventType type : values()) {
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
        CrmDomainEventType[] types = values();
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
