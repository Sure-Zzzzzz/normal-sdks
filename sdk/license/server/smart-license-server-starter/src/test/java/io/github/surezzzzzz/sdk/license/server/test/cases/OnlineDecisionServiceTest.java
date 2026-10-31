package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.license.core.model.Decision;
import io.github.surezzzzzz.sdk.license.core.model.EligibilityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.OnlineAuthorizationRequest;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import io.github.surezzzzzz.sdk.license.server.entity.GrantAggregate;
import io.github.surezzzzzz.sdk.license.server.service.OnlineDecisionService;
import io.github.surezzzzzz.sdk.license.server.service.SubjectAdmissionService;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.entity.OrganizationBindingEntity;
import io.github.surezzzzzz.sdk.license.server.repository.OrganizationBindingRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 在线判定服务断言：未知授权/对象不匹配/暂停优先/商业阶段/规则 ALLOW/DENY 传导。
 *
 * @author surezzzzzz
 */
class OnlineDecisionServiceTest {

    private static final long NOW = 1780005000L; // 授权 ACTIVE 窗口内

    private final Map<String, GrantAggregate> grants = new HashMap<String, GrantAggregate>();
    private final StringBuilder ruleLog = new StringBuilder();

    private SubjectAdmissionService admittedAdmission() {
        SubjectAdmissionService admission = Mockito.mock(SubjectAdmissionService.class);
        Mockito.when(admission.isAdmitted(Mockito.eq("cu1"), Mockito.nullable(String.class)))
                .thenReturn(SubjectAdmissionService.Admission.ADMITTED);
        return admission;
    }

    private OnlineDecisionService service(boolean ruleResult, boolean ruleThrows) {
        return new OnlineDecisionService(
                new OnlineDecisionService.GrantRegistry() {
                    @Override
                    public Optional<GrantAggregate> findById(String grantId) {
                        return Optional.ofNullable(grants.get(grantId));
                    }
                },
                new OnlineDecisionService.RuleEvaluationPort() {
                    @Override
                    public boolean evaluate(String grantId, String action, Map<String, Object> facts) {
                        if (ruleThrows) {
                            throw new IllegalArgumentException("fact missing");
                        }
                        return ruleResult;
                    }
                }, admittedAdmission());
    }

    private GrantAggregate enabledPersonGrant() {
        GrantAggregate grant = GrantAggregate.load("g1", "cu1", "p1", SubjectKind.PERSON,
                EligibilityPolicy.customerMembers(),
                ValidityPolicy.fixed(1780000000L, 1781000000L, 1782000000L, null),
                EligibilityPolicy.Mode.ONLINE,
                io.github.surezzzzzz.sdk.license.server.constant.GrantState.ENABLED, 1L, 1L);
        grants.put("g1", grant);
        return grant;
    }

    private OnlineAuthorizationRequest personRequest(String grantId) {
        return OnlineAuthorizationRequest.personOrService("p1", grantId, SubjectKind.PERSON,
                "product.enter", null, null, null);
    }

    @Test
    void unknownGrantIsDenyNotException() {
        Decision decision = service(true, false)
                .decide(personRequest("no-such"), NOW);
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
    }

    @Test
    void kindMismatchDeniedAsUnknown() {
        enabledPersonGrant();
        OnlineAuthorizationRequest deploymentRequest = OnlineAuthorizationRequest.deployment(
                "p1", "g1", "product.enter", "activation-1", null, null);
        Decision decision = service(true, false).decide(deploymentRequest, NOW);
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
    }

    @Test
    void suspendedGrantDeniedBeforePhaseAndRules() {
        GrantAggregate grant = enabledPersonGrant();
        grant.suspendOrResume(true);
        Decision decision = service(true, false).decide(personRequest("g1"), NOW);
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.GRANT_SUSPENDED);
    }

    @Test
    void commercialPhaseDeniedOutsideActive() {
        GrantAggregate grant = GrantAggregate.load("g1", "cu1", "p1", SubjectKind.PERSON,
                EligibilityPolicy.customerMembers(),
                ValidityPolicy.fixed(1780000000L, 1780001000L, 1780009000L, null),
                EligibilityPolicy.Mode.ONLINE,
                io.github.surezzzzzz.sdk.license.server.constant.GrantState.ENABLED, 1L, 1L);
        grants.put("g1", grant);
        Decision decision = service(true, false).decide(personRequest("g1"), NOW); // NOW 在 serviceUntil 与宽限截止之间
        assertThat(decision.getCommercialPhase()).isEqualTo(Decision.CommercialPhase.COMMERCIAL_GRACE);
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.COMMERCIAL_PHASE);
    }

    @Test
    void commercialGraceOnlyAllowsDeclaredGraceActionsAndStillEvaluatesRules() {
        grants.put("g1", GrantAggregate.load("g1", "cu1", "p1", SubjectKind.PERSON,
                EligibilityPolicy.customerMembers(), ValidityPolicy.fixed(1000L, 2000L, 3000L, null),
                EligibilityPolicy.Mode.ONLINE,
                io.github.surezzzzzz.sdk.license.server.constant.GrantState.ENABLED, 1L, 1L));
        OnlineDecisionService.RuleEvaluationPort rules = new OnlineDecisionService.RuleEvaluationPort() {
            @Override
            public boolean evaluate(String grantId, String action, Map<String, Object> facts) {
                return !"blocked".equals(action);
            }

            @Override
            public boolean allowsCommercialGrace(String grantId, String action) {
                return "read".equals(action) || "blocked".equals(action);
            }
        };
        OnlineDecisionService decisionService = new OnlineDecisionService(
                id -> Optional.ofNullable(grants.get(id)), rules, admittedAdmission());
        OnlineAuthorizationRequest read = OnlineAuthorizationRequest.personOrService("p1", "g1",
                SubjectKind.PERSON, "read", null, null, null);
        Decision allowed = decisionService.decide(read, 2000L);
        assertThat(allowed.isAllowed()).isTrue();
        assertThat(allowed.getCommercialPhase()).isEqualTo(Decision.CommercialPhase.COMMERCIAL_GRACE);
        assertThat(allowed.getRecheckAt()).isEqualTo(3000L);
        assertThat(decisionService.decide(personRequest("g1"), 2000L).getReasonCategory())
                .isEqualTo(Decision.ReasonCategory.COMMERCIAL_PHASE);
        OnlineAuthorizationRequest blocked = OnlineAuthorizationRequest.personOrService("p1", "g1",
                SubjectKind.PERSON, "blocked", null, null, null);
        assertThat(decisionService.decide(blocked, 2000L).getReasonCategory())
                .isEqualTo(Decision.ReasonCategory.RULE_DENIED);
        assertThat(decisionService.decide(read, 3000L).isAllowed()).isFalse();
    }

    @Test
    void commercialGraceDependencyFailureIsUnavailableNotBusinessDeny() {
        grants.put("g1", GrantAggregate.load("g1", "cu1", "p1", SubjectKind.PERSON,
                EligibilityPolicy.customerMembers(), ValidityPolicy.fixed(1000L, 2000L, 3000L, null),
                EligibilityPolicy.Mode.ONLINE,
                io.github.surezzzzzz.sdk.license.server.constant.GrantState.ENABLED, 1L, 1L));
        OnlineDecisionService.RuleEvaluationPort rules = new OnlineDecisionService.RuleEvaluationPort() {
            @Override
            public boolean evaluate(String grantId, String action, Map<String, Object> facts) {
                return true;
            }

            @Override
            public boolean allowsCommercialGrace(String grantId, String action) {
                throw new org.springframework.dao.DataAccessResourceFailureException("fixture database unavailable");
            }
        };
        OnlineDecisionService decisionService = new OnlineDecisionService(
                id -> Optional.ofNullable(grants.get(id)), rules, admittedAdmission());
        assertThatThrownBy(() -> decisionService.decide(personRequest("g1"), 2000L))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(503);
    }

    @Test
    void ruleResultAndFailurePropagate() {
        enabledPersonGrant();
        assertThat(service(true, false).decide(personRequest("g1"), NOW).isAllowed()).isTrue();
        assertThat(service(false, false).decide(personRequest("g1"), NOW).getReasonCategory())
                .isEqualTo(Decision.ReasonCategory.RULE_DENIED);
        assertThat(service(true, true).decide(personRequest("g1"), NOW).getReasonCategory())
                .isEqualTo(Decision.ReasonCategory.FACT_MISSING);
    }

    @Test
    void authorizationRevisionChangingDuringEvaluationCannotReturnAllow() {
        GrantAggregate initial = enabledPersonGrant();
        GrantAggregate revised = GrantAggregate.load("g1", "cu1", "p1", SubjectKind.PERSON,
                EligibilityPolicy.customerMembers(),
                ValidityPolicy.fixed(1780000000L, 1781000000L, 1782000000L, null),
                EligibilityPolicy.Mode.ONLINE,
                io.github.surezzzzzz.sdk.license.server.constant.GrantState.ENABLED, 2L, 1L);
        OnlineDecisionService.GrantRegistry registry = new OnlineDecisionService.GrantRegistry() {
            @Override
            public Optional<GrantAggregate> findById(String grantId) {
                return Optional.of(initial);
            }

            @Override
            public boolean isCurrent(GrantAggregate snapshot) {
                return snapshot.getGrantRevision() == revised.getGrantRevision()
                        && snapshot.getGrantStateVersion() == revised.getGrantStateVersion();
            }
        };
        OnlineDecisionService guarded = new OnlineDecisionService(registry,
                (grantId, action, facts) -> true, admittedAdmission());

        Decision decision = guarded.decide(personRequest("g1"), NOW);

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.RULE_DENIED);
    }

    @Test
    void knownCommercialDeadlineIsRecheckBoundAndExclusiveAtCutoff() {
        enabledPersonGrant();
        Decision allowed = service(true, false).decide(personRequest("g1"), 1780999999L);
        assertThat(allowed.isAllowed()).isTrue();
        assertThat(allowed.getModelVersion()).isZero();
        assertThat(allowed.getRecheckAt()).isEqualTo(1781000000L);
        Decision cutoff = service(true, false).decide(personRequest("g1"), 1781000000L);
        assertThat(cutoff.isAllowed()).isFalse();
        assertThat(cutoff.getCommercialPhase()).isEqualTo(Decision.CommercialPhase.COMMERCIAL_GRACE);
        assertThat(cutoff.getRecheckAt()).isNull();
    }

    @Test
    void unavailableDirectoryOrRulesCannotMasqueradeAsBusinessDeny() {
        enabledPersonGrant();
        SubjectAdmissionService admission = Mockito.mock(SubjectAdmissionService.class);
        Mockito.when(admission.isAdmitted("cu1", "person-1"))
                .thenReturn(SubjectAdmissionService.Admission.UNAVAILABLE);
        OnlineDecisionService admissionFailure = new OnlineDecisionService(
                id -> Optional.ofNullable(grants.get(id)), (id, action, facts) -> true, admission);
        assertThatThrownBy(() -> admissionFailure.decide(personRequest("g1"), "person-1", NOW))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(503);
        OnlineDecisionService ruleFailure = new OnlineDecisionService(
                id -> Optional.ofNullable(grants.get(id)), (id, action, facts) -> {
            throw new org.springframework.dao.DataAccessResourceFailureException("fixture database unavailable");
        }, admittedAdmission());
        assertThatThrownBy(() -> ruleFailure.decide(personRequest("g1"), NOW))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(503);
    }

    @Test
    void customerMembersWithoutAdmissionDependencyFailClosed() {
        enabledPersonGrant();
        OnlineDecisionService service = new OnlineDecisionService(
                id -> Optional.ofNullable(grants.get(id)), (id, action, facts) -> true,
                (SubjectAdmissionService) null);

        assertThatThrownBy(() -> service.decide(personRequest("g1"), "person-1", NOW))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(503);
    }

    @Test
    void offlineModeGrantRejectedForOnlineCall() {
        GrantAggregate offline = GrantAggregate.load("g2", "cu1", "p1", SubjectKind.DEPLOYMENT,
                EligibilityPolicy.allocatedDeployments(),
                ValidityPolicy.fixed(1780000000L, 1781000000L, 1782000000L, null),
                EligibilityPolicy.Mode.OFFLINE,
                io.github.surezzzzzz.sdk.license.server.constant.GrantState.ENABLED, 1L, 1L);
        grants.put("g2", offline);
        OnlineAuthorizationRequest request = OnlineAuthorizationRequest.deployment(
                "p1", "g2", "product.enter", "activation-1", null, null);
        Decision decision = service(true, false).decide(request, NOW);
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
    }

    @Test
    void boundCustomerCannotBypassAdmissionByOmittingSubject() {
        enabledPersonGrant();
        OrganizationBindingRepository bindings = Mockito.mock(OrganizationBindingRepository.class);
        Mockito.when(bindings.findById("cu1")).thenReturn(Optional.of(
                new OrganizationBindingEntity("cu1", "org-1", 9L)));
        io.github.surezzzzzz.sdk.license.core.spi.IamDirectoryPort directory =
                Mockito.mock(io.github.surezzzzzz.sdk.license.core.spi.IamDirectoryPort.class);
        OnlineDecisionService.RuleEvaluationPort rules =
                Mockito.mock(OnlineDecisionService.RuleEvaluationPort.class);
        OnlineDecisionService service = new OnlineDecisionService(
                id -> Optional.ofNullable(grants.get(id)), rules,
                new SubjectAdmissionService(bindings, directory));

        Decision decision = service.decide(personRequest("g1"), NOW);

        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
        Mockito.verifyNoInteractions(directory, rules);
    }

    @Test
    void unboundCustomerCannotSupplyMembershipProof() {
        enabledPersonGrant();
        OrganizationBindingRepository bindings = Mockito.mock(OrganizationBindingRepository.class);
        Mockito.when(bindings.findById("cu1")).thenReturn(Optional.empty());
        OnlineDecisionService service = new OnlineDecisionService(
                id -> Optional.ofNullable(grants.get(id)), (id, action, facts) -> true,
                new SubjectAdmissionService(bindings, null));

        Decision decision = service.decide(personRequest("g1"), NOW);
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getReasonCategory()).isEqualTo(Decision.ReasonCategory.SUBJECT_INELIGIBLE);
    }
}
