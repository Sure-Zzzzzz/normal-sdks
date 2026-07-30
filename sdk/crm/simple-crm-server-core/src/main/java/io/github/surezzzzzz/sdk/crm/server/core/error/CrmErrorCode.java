package io.github.surezzzzzz.sdk.crm.server.core.error;

import lombok.Getter;

/**
 * CRM 稳定错误码。
 *
 * @author surezzzzzz
 */
@Getter
public enum CrmErrorCode {

    /**
     * 参数校验、失败。
     */
    VALIDATION_FAILED("validation_failed", "参数校验、失败"),

    /**
     * invalid、格式。
     */
    INVALID_FORMAT("invalid_format", "invalid、格式"),

    /**
     * 未认证。
     */
    UNAUTHENTICATED("unauthenticated", "未认证"),

    /**
     * 操作者、已停用。
     */
    ACTOR_DISABLED("actor_disabled", "操作者、已停用"),

    /**
     * 租户、不匹配。
     */
    TENANT_MISMATCH("tenant_mismatch", "租户、不匹配"),

    /**
     * 操作、未授权。
     */
    ACTION_FORBIDDEN("action_forbidden", "操作、未授权"),

    /**
     * 数据、访问、被拒绝。
     */
    DATA_ACCESS_DENIED("data_access_denied", "数据、访问、被拒绝"),

    /**
     * 不存在、资源、或、不存在、不可访问。
     */
    NOT_FOUND_OR_NOT_ACCESSIBLE("not_found_or_not_accessible", "不存在、资源、或、不存在、不可访问"),

    /**
     * 聚合、版本、冲突。
     */
    AGGREGATE_VERSION_CONFLICT("aggregate_version_conflict", "聚合、版本、冲突"),

    /**
     * 幂等、键、已复用、但、不同、请求内容。
     */
    IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD("idempotency_key_reused_with_different_payload", "幂等、键、已复用、但、不同、请求内容"),

    /**
     * invalid、state、迁移。
     */
    INVALID_STATE_TRANSITION("invalid_state_transition", "invalid、state、迁移"),

    /**
     * 报价、已过期。
     */
    QUOTATION_EXPIRED("quotation_expired", "报价、已过期"),

    /**
     * 报价、已、已确认。
     */
    QUOTATION_ALREADY_CONFIRMED("quotation_already_confirmed", "报价、已、已确认"),

    /**
     * commercial、能力、不可用。
     */
    COMMERCIAL_CAPABILITY_UNAVAILABLE("commercial_capability_unavailable", "commercial、能力、不可用"),

    /**
     * 履约、结果、一致性、failure。
     */
    FULFILLMENT_RESULT_CONSISTENCY_FAILURE("fulfillment_result_consistency_failure", "履约、结果、一致性、failure"),

    /**
     * 可重试、依赖、不可用。
     */
    RETRYABLE_DEPENDENCY_UNAVAILABLE("retryable_dependency_unavailable", "可重试、依赖、不可用");

    /**
     * 稳定类型代码。
     */
    private final String code;

    /**
     * 面向使用者的中文说明。
     */
    private final String description;

    CrmErrorCode(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * 根据稳定代码获取枚举值。
     *
     * @param code 稳定类型代码
     * @return 匹配的枚举值；不存在时返回 null
     */
    public static CrmErrorCode fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (CrmErrorCode type : values()) {
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
        CrmErrorCode[] types = values();
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
