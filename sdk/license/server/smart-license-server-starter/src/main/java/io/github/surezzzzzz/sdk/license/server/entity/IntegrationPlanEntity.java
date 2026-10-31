package io.github.surezzzzzz.sdk.license.server.entity;

import java.time.Instant;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;

/**
 * IAM 受委托角色联动计划（MANUAL 形态持久化）：期望规则/部门差异清单+命令状态同行承载。
 *
 * <p>生成清单不等于写入 IAM；PENDING_EXECUTION 经人工确认转 MANUALLY_CONFIRMED（回执是
 * 人员确认，不是 IAM 服务端签收）；新期望产生时旧行整体 SUPERSEDED。AUTO 执行
 * 由 Core 协作端口复用本行的期望规则载荷。</p>
 *
 * @author surezzzzzz
 */
@Entity
@Table(name = "license_integration_plan")
public class IntegrationPlanEntity {

    /** 完整已验证的计划快照；旧行为空时不得执行或确认。 */
    @Column(name = "projection_snapshot", columnDefinition = "TEXT")
    private String projectionSnapshot;

    @Column(name = "failure_reason", length = 64)
    private String failureReason;

    @Column(name = "execution_mode", length = 16, nullable = false)
    private String executionMode = "MANUAL";

    @Id
    @Column(name = "plan_id", length = 64, nullable = false)
    private String planId;

    /** 协作命令编号（对外命令视图主键；与 planId 一一对应）。 */
    @Column(name = "command_id", length = 64, nullable = false, unique = true)
    private String commandId;

    @Column(name = "customer_id", length = 64, nullable = false)
    private String customerId;

    @Column(name = "product_id", length = 64, nullable = false)
    private String productId;

    /** 目标 IAM 应用编号（应用绑定）。 */
    @Column(name = "application_id", length = 64, nullable = false)
    private String applicationId;

    /** 期望角色规则快照（PAGE/API/DATA 批准子集，JSON 对象）。 */
    @Column(name = "desired_rules", columnDefinition = "TEXT", nullable = false)
    private String desiredRules;

    /** 部门差异（挂载/卸载列表，JSON 对象）。 */
    @Column(name = "department_diff", columnDefinition = "TEXT", nullable = false)
    private String departmentDiff;

    /** 期望修订号（同客户产品应用单调递增）。 */
    @Column(name = "desired_revision", nullable = false)
    private long desiredRevision;

    /** PENDING_EXECUTION/MANUALLY_CONFIRMED/SUPERSEDED。 */
    @javax.persistence.Enumerated(javax.persistence.EnumType.STRING)
    @Column(name = "state", length = 40, nullable = false)
    private IntegrationState state;

    /** 人工确认回执的修订号（MANUALLY_CONFIRMED 后非空）。 */
    @Column(name = "confirmed_revision")
    private Long confirmedRevision;

    /** 有效截止（epoch 秒；过期清单不可确认）。 */
    @Column(name = "valid_until", nullable = false)
    private long validUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    @Column(name = "row_version", nullable = false)
    private long rowVersion;

    protected IntegrationPlanEntity() {
    }

    public IntegrationPlanEntity(String planId, String commandId, String customerId, String productId,
                                 String applicationId, String desiredRules, String departmentDiff,
                                 long desiredRevision, long validUntilEpochSeconds) {
        this.planId = planId;
        this.commandId = commandId;
        this.customerId = customerId;
        this.productId = productId;
        this.applicationId = applicationId;
        this.desiredRules = desiredRules;
        this.departmentDiff = departmentDiff;
        this.desiredRevision = desiredRevision;
        this.state = IntegrationState.PENDING_EXECUTION;
        this.validUntil = validUntilEpochSeconds;
        this.createdAt = Instant.now();
    }

    /** 人工确认：守卫（状态/修订/有效期）由状态机承担；回执=人员确认修订号。 */
    public void confirmManually(long nowEpochSeconds, long expectedRevision) {
        this.state = state.confirm(expectedRevision, desiredRevision, nowEpochSeconds, validUntil);
        this.confirmedRevision = desiredRevision;
    }

    /** 新期望产生时旧未执行清单整体失效。 */
    public void supersede() {
        this.state = state.supersede();
    }

    /** 存储真实目标/环境/manifest/角色/目录观察，与规则和差异同事务落库。 */
    public void snapshot(String snapshot) { this.projectionSnapshot = snapshot; }

    /** 显式执行结果；未知/漂移不伪装人工成功，也不允许继续盲重发。 */
    public void execution(String mode, IntegrationState state, String reason) {
        this.executionMode = mode;
        this.state = state;
        this.failureReason = reason;
        if (state == IntegrationState.VERIFIED || state == IntegrationState.MANUALLY_CONFIRMED) {
            this.confirmedRevision = desiredRevision;
        }
    }

    public String getProjectionSnapshot() { return projectionSnapshot; }
    public String getFailureReason() { return failureReason; }
    public String getExecutionMode() { return executionMode; }
    public String getPlanId() {
        return planId;
    }

    public String getCommandId() {
        return commandId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public String getProductId() {
        return productId;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public String getDesiredRules() {
        return desiredRules;
    }

    public String getDepartmentDiff() {
        return departmentDiff;
    }

    public long getDesiredRevision() {
        return desiredRevision;
    }

    public String getState() {
        return state.getCode();
    }

    public Long getConfirmedRevision() {
        return confirmedRevision;
    }

    public long getValidUntil() {
        return validUntil;
    }

    public long getRowVersion() {
        return rowVersion;
    }
}
