package io.github.surezzzzzz.sdk.license.server.limiter.constant;

/**
 * License 限频适配件的配置、领域映射与校验消息。
 *
 * @author surezzzzzz
 */
public final class SimpleLicenseLimiterPolicyAdaptorConstant {

    public static final String CONFIG_PREFIX = "io.github.surezzzzzz.sdk.license.limiter";
    public static final String CUSTOMER_DIMENSION = "CUSTOMER";
    public static final String EXACT_SELECTOR = "EXACT";
    public static final String ROW_VERSION_REQUIRED = "限频规则行版本不能为空";
    public static final String STALE_RULE = "limiter 待停用规则版本已变化";
    public static final String LIMITS_REQUIRED = "limiter 规则缺少限频窗口";
    public static final String KEY_REQUIRED = "限频策略键不能为空";
    public static final String MUTATION_STATE_INVALID = "limiter 变更响应缺少预期规则状态";
    public static final String MUTATION_RULE_MISSING = "limiter 变更后无法回读原规则";
    public static final String MUTATION_READBACK_MISMATCH = "limiter 变更与回读状态不一致";
    public static final String RULE_IDENTITY_MISMATCH = "limiter 回读规则身份不一致";
    public static final String READBACK_KEY_INCOMPLETE = "limiter 回读响应缺少完整策略键";
    public static final String REQUEST_INCOMPLETE = "限频履约请求不完整";
    public static final String CUSTOMER_NAMESPACE_REQUIRED = "CUSTOMER namespace 未配置";
    public static final String RULE_ID_REQUIRED = "limiter ruleId 不能为空";

    private SimpleLicenseLimiterPolicyAdaptorConstant() {
    }
}
