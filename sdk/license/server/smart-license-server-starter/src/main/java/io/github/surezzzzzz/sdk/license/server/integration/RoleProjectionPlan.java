package io.github.surezzzzzz.sdk.license.server.integration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 角色投影清单（MANUAL 模式核心产物）：IAM 管理员按清单在既有界面执行后确认。
 *
 * <p>生成清单不等于写入 IAM；状态 PENDING_EXECUTION，人工确认后 MANUALLY_CONFIRMED，
 * 不能展示为机器核验通过。清单不携带人员名单、Cookie、CSRF 秘密或原始 Token。</p>
 *
 * @author surezzzzzz
 */
public final class RoleProjectionPlan {

    /** 清单状态。 */
    public enum State {
        PENDING_EXECUTION("PENDING_EXECUTION"),
        MANUALLY_CONFIRMED("MANUALLY_CONFIRMED"),
        SUPERSEDED("SUPERSEDED");

        private final String code;

        State(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }
    }

    private final String planId;
    private final long revision;
    private final String organizationInstanceId;
    private final int bindingGeneration;
    private final String customerId;
    private final String productId;
    private final long targetApplicationId;
    private final String roleCode;
    private final Map<String, Object> desiredRules;
    private final List<Long> targetDepartmentIds;
    private final List<Long> departmentsToMount;
    private final List<Long> departmentsToUnmount;
    private final long manifestVersion;
    private final long planValidUntil;
    private State state;

    private RoleProjectionPlan(Builder builder) {
        this.planId = builder.planId;
        this.revision = builder.revision;
        this.organizationInstanceId = builder.organizationInstanceId;
        this.bindingGeneration = builder.bindingGeneration;
        this.customerId = builder.customerId;
        this.productId = builder.productId;
        this.targetApplicationId = builder.targetApplicationId;
        this.roleCode = builder.roleCode;
        this.desiredRules = Collections.unmodifiableMap(
                new LinkedHashMap<String, Object>(builder.desiredRules));
        this.targetDepartmentIds = Collections.unmodifiableList(
                new java.util.ArrayList<Long>(builder.targetDepartmentIds));
        this.departmentsToMount = Collections.unmodifiableList(
                new java.util.ArrayList<Long>(builder.departmentsToMount));
        this.departmentsToUnmount = Collections.unmodifiableList(
                new java.util.ArrayList<Long>(builder.departmentsToUnmount));
        this.manifestVersion = builder.manifestVersion;
        this.planValidUntil = builder.planValidUntil;
        this.state = State.PENDING_EXECUTION;
    }

    public static Builder builder(String planId, long revision) {
        return new Builder(planId, revision);
    }

    /** 人工确认：仅 PENDING_EXECUTION 可确认；过期或已替代的清单拒绝。 */
    public boolean confirmManually(long now) {
        if (state != State.PENDING_EXECUTION) {
            return false;
        }
        if (now >= planValidUntil) {
            return false;
        }
        state = State.MANUALLY_CONFIRMED;
        return true;
    }

    /** 新期望产生时旧清单整体失效。 */
    public void supersede() {
        if (state == State.PENDING_EXECUTION) {
            state = State.SUPERSEDED;
        }
    }

    public String getPlanId() {
        return planId;
    }

    public long getRevision() {
        return revision;
    }

    public String getOrganizationInstanceId() {
        return organizationInstanceId;
    }

    public int getBindingGeneration() {
        return bindingGeneration;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductId() {
        return productId;
    }

    public long getTargetApplicationId() {
        return targetApplicationId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    /** 期望规则快照（目标应用 PAGE/API/DATA 批准子集）。 */
    public Map<String, Object> getDesiredRules() {
        return desiredRules;
    }

    public List<Long> getTargetDepartmentIds() {
        return targetDepartmentIds;
    }

    public List<Long> getDepartmentsToMount() {
        return departmentsToMount;
    }

    public List<Long> getDepartmentsToUnmount() {
        return departmentsToUnmount;
    }

    public long getManifestVersion() {
        return manifestVersion;
    }

    public long getPlanValidUntil() {
        return planValidUntil;
    }

    public State getState() {
        return state;
    }

    /** Builder。 */
    public static final class Builder {

        private final String planId;
        private final long revision;
        private String organizationInstanceId = "";
        private int bindingGeneration = 1;
        private String customerId = "";
        private String productId = "";
        private long targetApplicationId;
        private String roleCode = "";
        private Map<String, Object> desiredRules = new LinkedHashMap<String, Object>();
        private List<Long> targetDepartmentIds = new java.util.ArrayList<Long>();
        private List<Long> departmentsToMount = new java.util.ArrayList<Long>();
        private List<Long> departmentsToUnmount = new java.util.ArrayList<Long>();
        private long manifestVersion;
        private long planValidUntil;

        private Builder(String planId, long revision) {
            this.planId = planId;
            this.revision = revision;
        }

        public Builder environment(String organizationInstanceId, int bindingGeneration) {
            this.organizationInstanceId = organizationInstanceId;
            this.bindingGeneration = bindingGeneration;
            return this;
        }

        public Builder target(String customerId, String productId, long applicationId, String roleCode) {
            this.customerId = customerId;
            this.productId = productId;
            this.targetApplicationId = applicationId;
            this.roleCode = roleCode;
            return this;
        }

        public Builder desiredRules(Map<String, Object> rules, long manifestVersion) {
            this.desiredRules = rules;
            this.manifestVersion = manifestVersion;
            return this;
        }

        public Builder departments(List<Long> targetIds, List<Long> toMount, List<Long> toUnmount) {
            this.targetDepartmentIds = targetIds;
            this.departmentsToMount = toMount;
            this.departmentsToUnmount = toUnmount;
            return this;
        }

        public Builder validUntil(long epochSeconds) {
            this.planValidUntil = epochSeconds;
            return this;
        }

        public RoleProjectionPlan build() {
            return new RoleProjectionPlan(this);
        }
    }
}
