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
    CONFIRM_QUOTATION("confirm_quotation", "确认、报价");

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
