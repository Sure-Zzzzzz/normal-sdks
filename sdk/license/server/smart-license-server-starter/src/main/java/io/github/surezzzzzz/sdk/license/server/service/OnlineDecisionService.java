package io.github.surezzzzzz.sdk.license.server.service;

import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.core.model.Decision;
import io.github.surezzzzzz.sdk.license.core.model.DecisionBinding;
import io.github.surezzzzzz.sdk.license.core.model.EligibilityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.OnlineAuthorizationRequest;
import io.github.surezzzzzz.sdk.license.core.exception.LicenseCoreException;
import io.github.surezzzzzz.sdk.license.server.constant.LicenseValidationConstant;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.entity.GrantAggregate;
import io.github.surezzzzzz.sdk.license.server.constant.GrantState;
import java.util.Optional;

/**
 * 在线判定服务（Server 权威侧）：管理状态→时间阶段→（调用方已核）资格→规则求值链。
 *
 * <p>本类不负责认证/权限/资格事实采集（宿主端口先行）；已知否定优先于宽限；
 * 合法资格不足是 DENY，不是异常。</p>
 *
 * @author surezzzzzz
 */
@lombok.extern.slf4j.Slf4j
@SimpleLicenseServerComponent
public final class OnlineDecisionService {

    private final GrantRegistry registry;
    private final RuleEvaluationPort rulePort;
    private final SubjectAdmissionService admission;
    private final LicenseRevocationGuard revocationGuard;
    private final QuotaDecisionGuard quotas;

    /** 授权注册表端口（持久层实现）。 */
    public interface GrantRegistry {

        Optional<GrantAggregate> findById(String grantId);

        /**
         * 判定末端的授权版本围栏。跨规则/额度端口读取期间若授权被暂停、吊销或修订，必须拒绝旧快照。
         * 非持久化隔离测试默认认为快照稳定；生产仓储必须覆盖此方法。
         */
        default boolean isCurrent(GrantAggregate snapshot) {
            return true;
        }
    }

    /** 规则求值端口（模型快照装载+条件树执行）。 */
    public interface RuleEvaluationPort {

        boolean evaluate(String grantId, String action, java.util.Map<String, Object> facts);

        /** 商业宽限动作由模型明确声明；默认端口不推断宽限许可。 */
        default boolean allowsCommercialGrace(String grantId, String action) {
            return false;
        }

        /** 求值结论与实际模型版本一起返回；无模型的自备端口报告 0，不能借用商业修订号。 */
        default RuleEvaluation evaluateResult(String grantId, String action, java.util.Map<String, Object> facts) {
            return new RuleEvaluation(evaluate(grantId, action, facts), 0L);
        }
    }

    /** 同次模型装载产生的结果，避免单独查版本与求值使用不同快照。 */
    public static final class RuleEvaluation {

        private final boolean allowed;
        private final long modelVersion;

        public RuleEvaluation(boolean allowed, long modelVersion) {
            this.allowed = allowed;
            this.modelVersion = modelVersion;
        }

        public boolean isAllowed() {
            return allowed;
        }

        public long getModelVersion() {
            return modelVersion;
        }
    }

    public OnlineDecisionService(GrantRegistry registry, RuleEvaluationPort rulePort) {
        this(registry, rulePort, null, null);
    }

    public OnlineDecisionService(GrantRegistry registry, RuleEvaluationPort rulePort,
                                 SubjectAdmissionService admission) {
        this(registry, rulePort, admission, null);
    }

    /** 生产装配强制执行吊销围栏，不能因可选条件漏装而放行。 */
    public OnlineDecisionService(GrantRegistry registry, RuleEvaluationPort rulePort,
                                 @org.springframework.beans.factory.annotation.Autowired(required = false)
                                         SubjectAdmissionService admission,
                                 LicenseRevocationGuard revocationGuard) {
        this(registry, rulePort, admission, revocationGuard, null);
    }

    /** 生产装配必须有额度围栏；隔离规则测试可使用上方构造。 */
    @org.springframework.beans.factory.annotation.Autowired
    public OnlineDecisionService(GrantRegistry registry, RuleEvaluationPort rulePort,
                                 SubjectAdmissionService admission, LicenseRevocationGuard revocationGuard,
                                 QuotaDecisionGuard quotas) {
        this.registry = registry;
        this.rulePort = rulePort;
        this.admission = admission;
        this.revocationGuard = revocationGuard;
        this.quotas = quotas;
    }

    /**
     * 在线判定：请求对象种类须与授权一致；仅 ONLINE/HYBRID 的在线动作。
     *
     * @param now 权威 UTC 秒
     */
    public Decision decide(OnlineAuthorizationRequest request, long now) {
        return decide(request, null, now);
    }

    /**
     * 在线判定（带主体准入）：客户成员资格始终按绑定行裁决，缺主体不能绕过已绑定客户的准入。
     *
     * @param subjectId 公开主体标识（null=调用方未采集，绑定客户按信息不足拒绝）
     * @param now      权威 UTC 秒
     */
    public Decision decide(OnlineAuthorizationRequest request, String subjectId, long now) {
        return decide(request, null, subjectId, now);
    }

    /** sourceId/subjectId必须为边界已核验的真实使用者，DATA范围不能替代主体或代理证明。 */
    public Decision decide(OnlineAuthorizationRequest request, String sourceId, String subjectId, long now) {
        return decide(request, sourceId, subjectId, sourceId, now);
    }

    /** 业务身份来源与实际认证来源分别传入，继承人员身份不能改写额度操作的认证事实。 */
    public Decision decide(OnlineAuthorizationRequest request, String sourceId, String subjectId,
                            String authenticationSourceId, long now) {
        Optional<GrantAggregate> found = registry.findById(request.getGrantId());
        if (!found.isPresent()) {
            return unknownGrant(request, now);
        }
        GrantAggregate grant = found.get();

        // 对象种类一致：人员授权不能被部署请求消费
        if (grant.getSubjectKind() != request.getSubjectKind()
                || !grant.getProductId().equals(request.getProductId())) {
            return unknownGrant(request, now);
        }
        if (revocationGuard != null) {
            Decision.ReasonCategory rejected = revocationGuard.onlineRejection(request, sourceId, subjectId);
            if (rejected != null) {
                return withGrantMetadata(Decision.deny(request.getAction(), 0, null,
                        Decision.ConnectivityPhase.NOT_APPLICABLE, now, rejected), grant, request,
                        sourceId, subjectId);
            }
        }
        // 客户成员资格必须有实际绑定和成员证明；无绑定不能被解释为成员准入成功。
        if (grant.getEligibilityPolicy().getKind() == EligibilityPolicy.Kind.CUSTOMER_MEMBERS) {
            if (admission == null) {
                log.debug("在线成员准入端口未装配：grantId={}", grant.getGrantId());
                throw LicenseHttpException.dependencyUnavailable(
                        LicenseValidationConstant.DECISION_DEPENDENCY_UNAVAILABLE);
            }
            SubjectAdmissionService.Admission admitted = admission.isAdmitted(grant.getCustomerId(), subjectId);
            if (admitted == SubjectAdmissionService.Admission.UNAVAILABLE) {
                throw LicenseHttpException.dependencyUnavailable(LicenseValidationConstant.DECISION_DEPENDENCY_UNAVAILABLE);
            }
            if (admitted != SubjectAdmissionService.Admission.ADMITTED) {
                return withGrantMetadata(Decision.deny(request.getAction(), 0, null,
                        Decision.ConnectivityPhase.NOT_APPLICABLE, now,
                        Decision.ReasonCategory.SUBJECT_INELIGIBLE), grant, request, sourceId, subjectId);
            }
        }
        // 在线动作仅 ONLINE/HYBRID；纯 ONLINE 不发离线文件
        if (grant.getMode() == EligibilityPolicy.Mode.OFFLINE) {
            return withGrantMetadata(Decision.deny(request.getAction(), 0, null, null, now,
                    Decision.ReasonCategory.SUBJECT_INELIGIBLE), grant, request, sourceId, subjectId);
        }
        // 管理状态：暂停/吊销优先于一切宽限
        if (!grant.isUsable()) {
            boolean revoked = grant.getState() == GrantState.REVOKED;
            return withGrantMetadata(Decision.deny(request.getAction(), 0,
                    grant.getValidity().commercialPhaseAt(now),
                    Decision.ConnectivityPhase.NOT_APPLICABLE, now,
                    revoked ? Decision.ReasonCategory.REVOKED
                            : Decision.ReasonCategory.GRANT_SUSPENDED), grant, request, sourceId, subjectId);
        }
        // 商业阶段
        Decision.CommercialPhase phase = grant.getValidity().commercialPhaseAt(now);
        boolean commercialGrace;
        try {
            commercialGrace = phase == Decision.CommercialPhase.COMMERCIAL_GRACE
                    && rulePort.allowsCommercialGrace(grant.getGrantId(), request.getAction());
        } catch (LicenseHttpException unavailable) {
            throw unavailable;
        } catch (RuntimeException dependencyFailure) {
            log.debug("在线宽限动作依赖不可用：grantId={}，异常类型={}",
                    grant.getGrantId(), dependencyFailure.getClass().getSimpleName());
            throw LicenseHttpException.dependencyUnavailable(LicenseValidationConstant.DECISION_DEPENDENCY_UNAVAILABLE);
        }
        if (phase != Decision.CommercialPhase.ACTIVE && !commercialGrace) {
            return withGrantMetadata(Decision.deny(request.getAction(), 0, phase,
                    Decision.ConnectivityPhase.NOT_APPLICABLE, now,
                    Decision.ReasonCategory.COMMERCIAL_PHASE), grant, request, sourceId, subjectId);
        }
        // 规则求值（模型快照+本次事实；缺失/类型错由端口按确定拒绝传导）
        RuleEvaluation evaluation;
        try {
            evaluation = rulePort.evaluateResult(grant.getGrantId(), request.getAction(), request.getFacts());
        } catch (LicenseCoreException | IllegalArgumentException
                 | io.github.surezzzzzz.sdk.license.core.model.FactValues.MissingFactException invalidFacts) {
            log.debug("在线规则求值失败：grantId={}，异常类型={}",
                    grant.getGrantId(), invalidFacts.getClass().getSimpleName());
            return withGrantMetadata(Decision.deny(request.getAction(), 0, phase,
                    Decision.ConnectivityPhase.NOT_APPLICABLE, now,
                    Decision.ReasonCategory.FACT_MISSING), grant, request, sourceId, subjectId);
        } catch (RuntimeException dependencyFailure) {
            log.debug("在线规则依赖不可用：grantId={}，异常类型={}",
                    grant.getGrantId(), dependencyFailure.getClass().getSimpleName());
            throw LicenseHttpException.dependencyUnavailable(LicenseValidationConstant.DECISION_DEPENDENCY_UNAVAILABLE);
        }
        if (evaluation == null || evaluation.getModelVersion() < 0L) {
            throw LicenseHttpException.dependencyUnavailable(LicenseValidationConstant.DECISION_DEPENDENCY_UNAVAILABLE);
        }
        if (!evaluation.isAllowed()) {
            return withGrantMetadata(Decision.deny(request.getAction(), evaluation.getModelVersion(), phase,
                    Decision.ConnectivityPhase.NOT_APPLICABLE, now,
                    Decision.ReasonCategory.RULE_DENIED), grant, request, sourceId, subjectId);
        }
        if (quotas != null && !quotas.satisfied(request, sourceId, subjectId, authenticationSourceId,
                grant.getGrantRevision(), grant.getGrantStateVersion(), now)) {
            return withGrantMetadata(Decision.deny(request.getAction(), evaluation.getModelVersion(), phase,
                    Decision.ConnectivityPhase.NOT_APPLICABLE, now, Decision.ReasonCategory.RULE_DENIED),
                    grant, request, sourceId, subjectId);
        }
        // 规则/额度端口可能跨仓储读取；返回 ALLOW 前必须确认最初授权快照仍是当前版本。
        if (!registry.isCurrent(grant)) {
            return withGrantMetadata(Decision.deny(request.getAction(), evaluation.getModelVersion(), phase,
                    Decision.ConnectivityPhase.NOT_APPLICABLE, now, Decision.ReasonCategory.RULE_DENIED),
                    grant, request, sourceId, subjectId);
        }
        // ONLINE 每次敏感入口仍需复核；截止只描述当前已知的商业失效界限，不授予缓存许可。
        return withGrantMetadata(Decision.allow(request.getAction(), evaluation.getModelVersion(), phase,
                Decision.ConnectivityPhase.NOT_APPLICABLE, now, commercialGrace
                ? grant.getValidity().getCommercialGraceUntil() : grant.getValidity().getServiceUntil(),
                request.getReservationReferences()), grant, request, sourceId, subjectId);
    }

    /**
     * 对已识别授权但未声明动作返回带身份元数据的确定拒绝；未知授权仍不回显任何身份事实。
     */
    public Decision denyUndeclaredAction(OnlineAuthorizationRequest request, String sourceId,
                                         String subjectId, long now) {
        Optional<GrantAggregate> found = registry.findById(request.getGrantId());
        if (!found.isPresent() || found.get().getSubjectKind() != request.getSubjectKind()
                || !found.get().getProductId().equals(request.getProductId())) {
            return unknownGrant(request, now);
        }
        return withGrantMetadata(Decision.deny(request.getAction(), 0L, null, null, now,
                Decision.ReasonCategory.RULE_DENIED), found.get(), request, sourceId, subjectId);
    }

    /** 为已识别授权附加服务端核实的真实对象绑定；不能用请求编号伪造代数。 */
    private Decision withGrantMetadata(Decision decision, GrantAggregate grant,
                                       OnlineAuthorizationRequest request,
                                       String sourceId, String subjectId) {
        if (revocationGuard == null) {
            // 隔离领域构造不具备持久化对象事实，保留领域测试结果但不伪造在线关联元数据。
            return decision;
        }
        DecisionBinding binding = revocationGuard.onlineBinding(request, sourceId, subjectId).orElse(null);
        if (binding == null) {
            // 生产在线 ALLOW 必须带真实对象绑定；实体在复核期间消失/改绑时宁可拒绝，
            // 不能把无绑定的允许结论交给客户端再猜测。
            if (decision.isAllowed()) {
                return Decision.deny(decision.getAction(), decision.getModelVersion(),
                        decision.getCommercialPhase(), decision.getConnectivityPhase(),
                        decision.getEvaluatedAt(), Decision.ReasonCategory.SUBJECT_INELIGIBLE);
            }
            return decision;
        }
        return decision.withAuthorizationMetadata(grant.getProductId(), grant.getGrantId(), binding,
                grant.getGrantRevision(), grant.getGrantStateVersion());
    }

    private Decision unknownGrant(OnlineAuthorizationRequest request, long now) {
        return Decision.deny(request.getAction(), 0L, null, null, now,
                Decision.ReasonCategory.SUBJECT_INELIGIBLE);
    }

}
