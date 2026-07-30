package io.github.surezzzzzz.sdk.crm.server.core.domain.quotation;

import lombok.Getter;

/**
 * 报价版本状态。
 *
 * @author surezzzzzz
 */
@Getter
public enum QuotationState {

    /**
     * 草稿。
     */
    DRAFT("draft", "草稿"),

    /**
     * 已签发。
     */
    ISSUED("issued", "已签发"),

    /**
     * 已确认。
     */
    CONFIRMED("confirmed", "已确认"),

    /**
     * 已撤回。
     */
    WITHDRAWN("withdrawn", "已撤回"),

    /**
     * 已过期。
     */
    EXPIRED("expired", "已过期"),

    /**
     * 已被替代。
     */
    SUPERSEDED("superseded", "已被替代");

    /**
     * 稳定类型代码。
     */
    private final String code;

    /**
     * 面向使用者的中文说明。
     */
    private final String description;

    QuotationState(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据稳定代码获取枚举值。
     *
     * @param code 稳定类型代码
     * @return 匹配的枚举值；不存在时返回 null
     */
    public static QuotationState fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (QuotationState type : values()) {
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
        QuotationState[] types = values();
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
