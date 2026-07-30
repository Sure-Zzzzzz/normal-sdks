package io.github.surezzzzzz.sdk.crm.server.core.domain.type;

import lombok.Getter;

/**
 * CRM 首发受保护动作。
 *
 * @author surezzzzzz
 */
@Getter
public enum CrmAction {

    /**
     * 客户、创建。
     */
    CUSTOMER_CREATE("customer_create", "客户、创建"),

    /**
     * 联系人、创建。
     */
    CONTACT_CREATE("contact_create", "联系人、创建"),

    /**
     * 商品或服务、创建。
     */
    OFFERING_CREATE("offering_create", "商品或服务、创建"),

    /**
     * 报价、创建。
     */
    QUOTATION_CREATE("quotation_create", "报价、创建"),

    /**
     * 报价、签发。
     */
    QUOTATION_ISSUE("quotation_issue", "报价、签发"),

    /**
     * 报价、确认。
     */
    QUOTATION_CONFIRM("quotation_confirm", "报价、确认"),

    /**
     * 客户、查询。
     */
    CUSTOMER_READ("customer_read", "客户、查询"),

    /**
     * 联系人、查询。
     */
    CONTACT_READ("contact_read", "联系人、查询"),

    /**
     * 商品或服务、查询。
     */
    OFFERING_READ("offering_read", "商品或服务、查询"),

    /**
     * 报价、查询。
     */
    QUOTATION_READ("quotation_read", "报价、查询"),

    /**
     * 订单、查询。
     */
    ORDER_READ("order_read", "订单、查询");

    /**
     * 稳定类型代码。
     */
    private final String code;

    /**
     * 面向使用者的中文说明。
     */
    private final String description;

    CrmAction(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据稳定代码获取枚举值。
     *
     * @param code 稳定类型代码
     * @return 匹配的枚举值；不存在时返回 null
     */
    public static CrmAction fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CrmAction type : values()) {
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
        CrmAction[] types = values();
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
