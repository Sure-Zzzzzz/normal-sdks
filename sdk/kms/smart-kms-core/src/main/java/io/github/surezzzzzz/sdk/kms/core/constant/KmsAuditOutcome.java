package io.github.surezzzzzz.sdk.kms.core.constant;

import lombok.Getter;

/**
 * 安全审计结果。
 *
 * @author surezzzzzz
 */
@Getter
public enum KmsAuditOutcome {

    /**
     * 操作通过授权并完成。
     */
    ALLOWED("ALLOWED", "允许"),
    /**
     * 操作被参数、状态或授权规则拒绝。
     */
    REJECTED("REJECTED", "拒绝"),
    /**
     * 操作已开始但因安全失败或基础设施失败而未完成。
     */
    FAILED("FAILED", "失败");

    /**
     * 持久化使用的稳定编码。
     */
    private final String code;
    /**
     * 面向管理界面的中文说明。
     */
    private final String description;

    KmsAuditOutcome(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 按稳定编码查找审计结果。
     *
     * @param code 审计结果编码
     * @return 对应结果；未知编码时返回 {@code null}
     */
    public static KmsAuditOutcome fromCode(String code) {
        for (KmsAuditOutcome value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }

    /**
     * 判断审计结果编码是否已定义。
     *
     * @param code 审计结果编码
     * @return 已定义时返回 {@code true}
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }

    /**
     * 获取全部审计结果编码。
     *
     * @return 新建的审计结果编码数组
     */
    public static String[] getAllCodes() {
        KmsAuditOutcome[] values = values();
        String[] codes = new String[values.length];
        for (int index = 0; index < values.length; index++) {
            codes[index] = values[index].code;
        }
        return codes;
    }

    /**
     * 返回稳定审计结果编码。
     *
     * @return 审计结果编码
     */
    @Override
    public String toString() {
        return code;
    }
}
