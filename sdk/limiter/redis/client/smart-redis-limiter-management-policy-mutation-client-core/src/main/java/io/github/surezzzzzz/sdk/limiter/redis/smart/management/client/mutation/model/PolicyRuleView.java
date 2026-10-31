package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model;

import java.util.List;

/**
 * 完整策略键回读结果（规则现值，不含 revision——列表契约不回传）
 *
 * @author surezzzzzz
 */
public final class PolicyRuleView {

    private final long ruleId;
    private final long rowVersion;
    private final String serviceCode;
    private final String resourceCode;
    private final String dimension;
    private final String selector;
    private final String namespace;
    private final String customType;
    private final String objectId;
    private final boolean enabled;
    private final List<PolicyLimit> limits;

    public PolicyRuleView(long ruleId, long rowVersion, String serviceCode, String resourceCode,
                          String dimension, String selector, String namespace, String customType,
                          String objectId, boolean enabled, List<PolicyLimit> limits) {
        this.ruleId = ruleId;
        this.rowVersion = rowVersion;
        this.serviceCode = serviceCode;
        this.resourceCode = resourceCode;
        this.dimension = dimension;
        this.selector = selector;
        this.namespace = namespace;
        this.customType = customType;
        this.objectId = objectId;
        this.enabled = enabled;
        this.limits = limits;
    }

    public long getRuleId() {
        return ruleId;
    }

    public long getRowVersion() {
        return rowVersion;
    }

    public String getServiceCode() {
        return serviceCode;
    }

    public String getResourceCode() {
        return resourceCode;
    }

    public String getDimension() {
        return dimension;
    }

    public String getSelector() {
        return selector;
    }

    public String getNamespace() {
        return namespace;
    }

    public String getCustomType() {
        return customType;
    }

    public String getObjectId() {
        return objectId;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public List<PolicyLimit> getLimits() {
        return limits;
    }
}
