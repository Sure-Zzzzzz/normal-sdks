package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.model;

import java.util.List;

/**
 * 类型化规则变更命令（幂等 upsert 输入）
 *
 * <p>身份字段（serviceCode/resourceCode/dimension/selector/namespace/customType/objectId）
 * 组成规则完整策略键；License 履约联动场景固定 dimension=CUSTOMER、selector=EXACT、
 * objectId=customerId。limits 为整体替换语义（1–16 档），enabled 为目标启停状态。</p>
 *
 * @author surezzzzzz
 */
public final class PolicyUpsertCommand {

    private final String serviceCode;
    private final String resourceCode;
    private final String dimension;
    private final String selector;
    private final String namespace;
    private final String customType;
    private final String objectId;
    private final boolean enabled;
    private final List<PolicyLimit> limits;

    private PolicyUpsertCommand(Builder builder) {
        this.serviceCode = builder.serviceCode;
        this.resourceCode = builder.resourceCode;
        this.dimension = builder.dimension;
        this.selector = builder.selector;
        this.namespace = builder.namespace;
        this.customType = builder.customType;
        this.objectId = builder.objectId;
        this.enabled = builder.enabled;
        this.limits = builder.limits;
    }

    public static Builder builder() {
        return new Builder();
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

    /**
     * 构造器（身份字段必填校验由编排层统一承担）
     */
    public static final class Builder {
        private String serviceCode;
        private String resourceCode;
        private String dimension;
        private String selector;
        private String namespace;
        private String customType;
        private String objectId;
        private boolean enabled = true;
        private List<PolicyLimit> limits;

        public Builder serviceCode(String serviceCode) {
            this.serviceCode = serviceCode;
            return this;
        }

        public Builder resourceCode(String resourceCode) {
            this.resourceCode = resourceCode;
            return this;
        }

        public Builder dimension(String dimension) {
            this.dimension = dimension;
            return this;
        }

        public Builder selector(String selector) {
            this.selector = selector;
            return this;
        }

        public Builder namespace(String namespace) {
            this.namespace = namespace;
            return this;
        }

        public Builder customType(String customType) {
            this.customType = customType;
            return this;
        }

        public Builder objectId(String objectId) {
            this.objectId = objectId;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder limits(List<PolicyLimit> limits) {
            this.limits = limits;
            return this;
        }

        public PolicyUpsertCommand build() {
            return new PolicyUpsertCommand(this);
        }
    }
}
