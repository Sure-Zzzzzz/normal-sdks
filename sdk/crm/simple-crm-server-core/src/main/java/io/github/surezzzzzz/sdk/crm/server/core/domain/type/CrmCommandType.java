package io.github.surezzzzzz.sdk.crm.server.core.domain.type;

import lombok.Getter;

/**
 * CRM 首发幂等命令类型。
 *
 * @author surezzzzzz
 */
@Getter
public enum CrmCommandType {

    /**
     * 创建、客户。
     */
    CREATE_CUSTOMER("create_customer", "创建、客户"),

    /**
     * 创建、联系人。
     */
    CREATE_CONTACT("create_contact", "创建、联系人"),

    /**
     * 创建、商品或服务。
     */
    CREATE_OFFERING("create_offering", "创建、商品或服务"),

    /**
     * 创建、报价。
     */
    CREATE_QUOTATION("create_quotation", "创建、报价"),

    /**
     * 签发、报价。
     */
    ISSUE_QUOTATION("issue_quotation", "签发、报价"),

    /**
     * 确认、报价。
     */
    CONFIRM_QUOTATION("confirm_quotation", "确认、报价"),

    /**
     * 编辑报价草稿。
     */
    EDIT_QUOTATION("edit_quotation", "编辑报价草稿"),

    /**
     * 撤回报价。
     */
    WITHDRAW_QUOTATION("withdraw_quotation", "撤回报价"),

    /**
     * 重议派生新草稿。
     */
    RENEGOTIATE_QUOTATION("renegotiate_quotation", "重议派生新草稿"),

    /**
     * 停用客户。
     */
    DEACTIVATE_CUSTOMER("deactivate_customer", "停用客户"),

    /**
     * 复启客户。
     */
    ACTIVATE_CUSTOMER("activate_customer", "复启客户"),

    /**
     * 上架商品或服务。
     */
    PUBLISH_OFFERING("publish_offering", "上架商品或服务"),

    /**
     * 下架商品或服务。
     */
    DISABLE_OFFERING("disable_offering", "下架商品或服务"),

    /**
     * 合并客户。
     */
    MERGE_CUSTOMER("merge_customer", "合并客户"),

    /**
     * 转移客户归属。
     */
    TRANSFER_CUSTOMER("transfer_customer", "转移客户归属"),

    /**
     * 转移报价归属。
     */
    TRANSFER_QUOTATION("transfer_quotation", "转移报价归属"),

    /**
     * 回写履约结果。
     */
    REPORT_FULFILLMENT_RESULT("report_fulfillment_result", "回写履约结果"),

    /**
     * 重置履约项。
     */
    RESET_FULFILLMENT_ITEM("reset_fulfillment_item", "重置履约项"),

    /**
     * 注册或更新履约消费者。
     */
    UPSERT_FULFILLMENT_CONSUMER("upsert_fulfillment_consumer", "注册履约消费者");

    /**
     * 稳定类型代码。
     */
    private final String code;

    /**
     * 面向使用者的中文说明。
     */
    private final String description;

    CrmCommandType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据稳定代码获取枚举值。
     *
     * @param code 稳定类型代码
     * @return 匹配的枚举值；不存在时返回 null
     */
    public static CrmCommandType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CrmCommandType type : values()) {
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
        CrmCommandType[] types = values();
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
