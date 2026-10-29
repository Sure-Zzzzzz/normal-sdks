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
     * 报价、编辑。
     */
    QUOTATION_EDIT("quotation_edit", "报价、编辑"),

    /**
     * 报价、撤回。
     */
    QUOTATION_WITHDRAW("quotation_withdraw", "报价、撤回"),

    /**
     * 报价、重议。
     */
    QUOTATION_RENEGOTIATE("quotation_renegotiate", "报价、重议"),

    /**
     * 客户、停用。
     */
    CUSTOMER_DEACTIVATE("customer_deactivate", "客户、停用"),

    /**
     * 客户、复启。
     */
    CUSTOMER_ACTIVATE("customer_activate", "客户、复启"),

    /**
     * 客户、合并。
     */
    CUSTOMER_MERGE("customer_merge", "客户、合并"),

    /**
     * 客户、归属转移。
     */
    CUSTOMER_TRANSFER("customer_transfer", "客户、归属转移"),

    /**
     * 报价、归属转移。
     */
    QUOTATION_TRANSFER("quotation_transfer", "报价、归属转移"),

    /** 商机、创建。 */
    OPPORTUNITY_CREATE("opportunity_create", "商机、创建"),

    /** 商机、阶段推进。 */
    OPPORTUNITY_STAGE("opportunity_stage", "商机、阶段推进"),

    /** 商机、归属转移。 */
    OPPORTUNITY_TRANSFER("opportunity_transfer", "商机、归属转移"),

    /**
     * 目录、上架。
     */
    OFFERING_PUBLISH("offering_publish", "目录、上架"),

    /**
     * 目录、下架。
     */
    OFFERING_DISABLE("offering_disable", "目录、下架"),

    /**
     * 履约、回写。
     */
    FULFILLMENT_REPORT("fulfillment_report", "履约、回写"),

    /**
     * 履约、重置。
     */
    FULFILLMENT_RESET("fulfillment_reset", "履约、重置"),

    /**
     * 履约、消费者管理。
     */
    FULFILLMENT_CONSUMER_ADMIN("fulfillment_consumer_admin", "履约、消费者管理"),

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
