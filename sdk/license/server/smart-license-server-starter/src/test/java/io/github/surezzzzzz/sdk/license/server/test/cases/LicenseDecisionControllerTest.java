package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import io.github.surezzzzzz.sdk.license.core.model.EligibilityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.DecisionBinding;
import io.github.surezzzzzz.sdk.license.core.model.OnlineAuthorizationRequest;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.client.model.ClientDecision;
import io.github.surezzzzzz.sdk.license.client.spi.ClientResponseValidator;
import io.github.surezzzzzz.sdk.license.server.constant.GrantState;
import io.github.surezzzzzz.sdk.license.server.controller.LicenseDecisionController;
import io.github.surezzzzzz.sdk.license.server.entity.GrantAggregate;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository;
import io.github.surezzzzzz.sdk.license.server.service.LicenseRevocationGuard;
import io.github.surezzzzzz.sdk.license.server.service.OnlineDecisionService;
import io.github.surezzzzzz.sdk.license.server.service.ModelBackedRulePort;
import io.github.surezzzzzz.sdk.license.server.service.SubjectAdmissionService;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * 在线HTTP契约回归：事实类型保持、真实主体绑定与无证明代理拒绝。
 *
 * @author surezzzzzz
 */
@Slf4j
class LicenseDecisionControllerTest {

    private final LicenseRevocationGuard guard = mock(LicenseRevocationGuard.class);
    private final AtomicReference<Map<String, Object>> receivedFacts = new AtomicReference<>();
    private final AtomicReference<GrantAggregate> currentGrant = new AtomicReference<>();
    private SubjectAdmissionService admission;
    private ProductRepository products;
    private LicenseDecisionController controller;

    @BeforeEach
    void setUp() {
        products = mock(ProductRepository.class);
        when(products.findById("prod-1")).thenReturn(Optional.of(new ProductEntity(
                "prod-1", "iss-1", "product-test", "示例产品", "[\"read\"]", "[]")));
        GrantAggregate grant = GrantAggregate.load("gr-1", "cu-1", "prod-1", SubjectKind.PERSON,
                EligibilityPolicy.customerMembers(), ValidityPolicy.perpetual(1L, null, Collections.emptyList()),
                EligibilityPolicy.Mode.ONLINE, GrantState.ENABLED, 1L, 1L);
        currentGrant.set(grant);
        admission = mock(SubjectAdmissionService.class);
        when(admission.isAdmitted(eq("cu-1"), org.mockito.ArgumentMatchers.nullable(String.class)))
                .thenReturn(SubjectAdmissionService.Admission.ADMITTED);
        when(guard.onlineBinding(any(), any(), any())).thenAnswer(invocation -> {
            OnlineAuthorizationRequest request = invocation.getArgument(0);
            String sourceId = invocation.getArgument(1);
            String subjectId = invocation.getArgument(2);
            if (request.getSubjectKind() == SubjectKind.DEPLOYMENT) {
                return Optional.of(DecisionBinding.deployment(request.getActivationId(), 1L));
            }
            return Optional.of(DecisionBinding.personService(request.getSubjectKind(), sourceId, subjectId,
                    null, null));
        });
        OnlineDecisionService service = new OnlineDecisionService(id -> Optional.ofNullable(currentGrant.get()), (id, action, facts) -> {
            receivedFacts.set(facts);
            return true;
        }, admission, guard);
        controller = new LicenseDecisionController(service, products);
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void structuredFactsRetainStringSetNumericAndBooleanTypes() {
        String result = controller.decide(body(
                ",\"facts\":{\"departments\":[\"one\",\"two\"],\"count\":2,\"large\":2147483648,\"active\":true}"),
                TestPlans.all("use"), human()).getBody();
        log.info("在线事实保留集合、整数、长整数和布尔类型");
        assertThat(result).contains("\"allowed\":true");
        assertThat(receivedFacts.get().get("departments")).isEqualTo(Arrays.asList("one", "two"));
        assertThat(receivedFacts.get().get("count")).isInstanceOf(Integer.class).isEqualTo(2);
        assertThat(receivedFacts.get().get("large")).isInstanceOf(Long.class).isEqualTo(2147483648L);
        assertThat(receivedFacts.get().get("active")).isEqualTo(true);
    }

    @Test
    void nonObjectFactsRejectNullArrayAndString() {
        for (String facts : Arrays.asList("null", "[]", "\"text\"")) {
            log.info("非对象facts必须422：fixtureKind={}", facts);
            assertStatus(() -> controller.decide(body(",\"facts\":" + facts), TestPlans.all("use"), human()), 422);
        }
    }

    @Test
    void quotaReferencesRetainExplicitIdsAndRejectDuplicatesOrMalformedShapes() throws Exception {
        String response = controller.decide(body(",\"reservationReferences\":[\"reservation-1\",\"reservation-2\"]"),
                TestPlans.all("use"), human()).getBody();
        com.fasterxml.jackson.databind.JsonNode fences = StrictJson.mapper().readTree(response)
                .path("reservationFenceReferences");
        assertThat(fences.isArray()).isTrue();
        assertThat(fences.size()).isEqualTo(2);
        assertThat(fences.get(0).asText()).isEqualTo("reservation-1");
        assertThat(fences.get(1).asText()).isEqualTo("reservation-2");
        org.mockito.ArgumentCaptor<io.github.surezzzzzz.sdk.license.core.model.OnlineAuthorizationRequest> captured =
                org.mockito.ArgumentCaptor.forClass(io.github.surezzzzzz.sdk.license.core.model.OnlineAuthorizationRequest.class);
        verify(guard).onlineRejection(captured.capture(), eq("iam"), eq("zhangshuo"));
        assertThat(captured.getValue().getReservationReferences()).containsExactly("reservation-1", "reservation-2");
        for (String references : Arrays.asList("null", "\"reservation-1\"", "[1]", "[\"\"]",
                "[\"reservation-1\",\"reservation-1\"]")) {
            assertStatus(() -> controller.decide(body(",\"reservationReferences\":" + references),
                    TestPlans.all("use"), human()), 422);
        }
    }

    @Test
    void humanUsesAuthenticatedSubjectAndCannotSubstituteOtherPersonOrSource() {
        controller.decide(body(""), TestPlans.all("use"), human());
        verify(guard).onlineRejection(any(), eq("iam"), eq("zhangshuo"));
        log.info("人员目标取认证主体，不能借用别人的subjectId或来源");
        assertStatus(() -> controller.decide(body(",\"subjectId\":\"other-person\""),
                TestPlans.all("use"), human()), 403);
        assertStatus(() -> controller.decide(body(",\"sourceId\":\"another-source\""),
                TestPlans.all("use"), human()), 403);
    }

    @Test
    void serviceUsesVerifiedClientAndSourceWhenBodyOmitsIdentity() {
        currentGrant.set(GrantAggregate.load("gr-1", "cu-1", "prod-1", SubjectKind.SERVICE,
                EligibilityPolicy.assignedSubjects(null), ValidityPolicy.perpetual(1L, null, Collections.emptyList()),
                EligibilityPolicy.Mode.ONLINE, GrantState.ENABLED, 1L, 1L));
        String result = controller.decide(body("").replace("PERSON", "SERVICE"),
                TestPlans.all("use"), service()).getBody();
        log.info("机器固定资格取本次官方认证client和source，不能从body猜身份");
        assertThat(result).contains("\"allowed\":true");
        verify(guard).onlineRejection(any(), eq("aksk"), eq("product-machine"));
    }

    @Test
    void serviceCannotRepresentPersonWithoutTrustedProxyProofEvenWithAllData() {
        log.info("DATA全量不是身份代授权，机器不得凭裸body代理人员");
        assertStatus(() -> controller.decide(body(""), TestPlans.all("use"), service()), 403);
        assertStatus(() -> controller.decide(body(",\"subjectId\":\"target-person\",\"sourceId\":\"iam\""),
                TestPlans.all("use"), service()), 403);
        assertThat(receivedFacts.get()).isNull();
    }

    @Test
    void serviceCannotSubstituteAnotherServiceOrAuthenticationSource() {
        log.info("机器不得指定另一client或伪造身份来源，规则求值不会发生");
        assertStatus(() -> controller.decide(body(",\"subjectId\":\"other-machine\"").replace("PERSON", "SERVICE"),
                TestPlans.all("use"), service()), 403);
        assertStatus(() -> controller.decide(body(",\"sourceId\":\"iam\"").replace("PERSON", "SERVICE"),
                TestPlans.all("use"), service()), 403);
        assertThat(receivedFacts.get()).isNull();
    }

    @Test
    void verifiedSourceIsPreservedAndExplicitMatchingIdentityIsAllowed() {
        currentGrant.set(GrantAggregate.load("gr-1", "cu-1", "prod-1", SubjectKind.SERVICE,
                EligibilityPolicy.assignedSubjects(null), ValidityPolicy.perpetual(1L, null, Collections.emptyList()),
                EligibilityPolicy.Mode.ONLINE, GrantState.ENABLED, 1L, 1L));
        String result = controller.decide(body(",\"subjectId\":\"product-machine\",\"sourceId\":\"registered-source\"")
                .replace("PERSON", "SERVICE"), TestPlans.all("use"), service("registered-source")).getBody();
        log.info("已验证来源直接透传，不强制把不同来源改写为aksk");
        assertThat(result).contains("\"allowed\":true");
        verify(guard).onlineRejection(any(), eq("registered-source"), eq("product-machine"));
    }

    @Test
    void deploymentUsesAuthenticatedServiceCallerAndCannotSelfReportAnotherBinding() {
        javax.servlet.http.HttpServletRequest request = service();
        currentGrant.set(GrantAggregate.load("gr-1", "cu-1", "prod-1", SubjectKind.DEPLOYMENT,
                EligibilityPolicy.allocatedDeployments(), ValidityPolicy.perpetual(1L, null, Collections.emptyList()),
                EligibilityPolicy.Mode.ONLINE, GrantState.ENABLED, 1L, 1L));
        String input = body(",\"activationId\":\"act-1\"").replace("PERSON", "DEPLOYMENT");
        String result = controller.decide(input, TestPlans.all("use"), request).getBody();
        log.info("部署caller核官方认证，不从body回写或假借另一服务绑定");
        assertThat(result).contains("\"allowed\":true");
        verify(guard).onlineRejection(any(), eq("aksk"), eq("product-machine"));
        assertStatus(() -> controller.decide(input.replace("}", ",\"subjectId\":\"another-machine\"}"),
                TestPlans.all("use"), request), 403);
        assertStatus(() -> controller.decide(input.replace("}", ",\"sourceId\":\"another-source\"}"),
                TestPlans.all("use"), request), 403);
        assertStatus(() -> controller.decide(input.replace("}", ",\"deviceProof\":\"self-report\"}"),
                TestPlans.all("use"), request), 422);
    }

    @Test
    void evaluatorApiPermissionIsRequiredEvenWhenCallerHasFullGrantData() {
        log.info("DATA全量不替代精确decision.evaluate API许可，缺码不触发规则求值");
        assertStatus(() -> controller.decide(body(""), TestPlans.all("use"), human(false)), 403);
        assertThat(receivedFacts.get()).isNull();
    }

    @Test
    void unknownKindAndForeignObjectReferenceAreUnprocessable() {
        log.info("未知种类与人员夹带激活编号拒绝，不产生500或忽略字段");
        assertStatus(() -> controller.decide(body("").replace("PERSON", "UNKNOWN"),
                TestPlans.all("use"), human()), 422);
        assertStatus(() -> controller.decide(body(",\"activationId\":\"act-1\""),
                TestPlans.all("use"), human()), 422);
        assertStatus(() -> controller.decide(body(",\"assignmentId\":123"),
                TestPlans.all("use"), human()), 422);
        assertStatus(() -> controller.decide(body(",\"businessOperationId\":[]"),
                TestPlans.all("use"), human()), 422);
    }

    @Test
    void undeclaredActionIsACompleteDenyUnderOfficialClientContract() throws Exception {
        String response = controller.decide(body("").replace("read", "undeclared"),
                TestPlans.all("use"), human()).getBody();
        ClientDecision decision = officialClientDecision(response, "undeclared");
        assertThat(decision.isAllowed()).isFalse();
        assertThat(decision.getModelVersion()).isZero();
        assertThat(decision.getCommercialPhase()).isNull();
        assertThat(decision.getReasonCategory()).isEqualTo("RULE_DENIED");
        assertThat(StrictJson.mapper().readTree(response).get("evaluatedAt").asLong()).isPositive();
        assertThat(StrictJson.mapper().readTree(response).get("requestId").asText()).isNotEmpty();
        assertThat(receivedFacts.get()).isNull();
    }

    @Test
    void unknownGrantAndKindMismatchRemainLegalDenyRatherThanProtocolFailure() throws Exception {
        currentGrant.set(null);
        ClientDecision unknown = officialClientDecision(controller.decide(body(""),
                TestPlans.all("use"), human()).getBody(), "read");
        assertThat(unknown.isAllowed()).isFalse();
        assertThat(unknown.getCommercialPhase()).isNull();
        assertThat(unknown.getReasonCategory()).isEqualTo("SUBJECT_INELIGIBLE");
        currentGrant.set(GrantAggregate.load("gr-1", "cu-1", "prod-1", SubjectKind.DEPLOYMENT,
                EligibilityPolicy.allocatedDeployments(), ValidityPolicy.perpetual(1L, null, Collections.emptyList()),
                EligibilityPolicy.Mode.ONLINE, GrantState.ENABLED, 2L, 1L));
        ClientDecision mismatch = officialClientDecision(controller.decide(body(""),
                TestPlans.all("use"), human()).getBody(), "read");
        assertThat(mismatch.isAllowed()).isFalse();
        assertThat(mismatch.getCommercialPhase()).isNull();
        assertThat(mismatch.getReasonCategory()).isEqualTo("SUBJECT_INELIGIBLE");
        assertThat(receivedFacts.get()).isNull();
    }

    @Test
    void suspendedGrantAndPerpetualAllowKeepTheirActualCommercialFacts() throws Exception {
        currentGrant.get().suspendOrResume(true);
        ClientDecision suspended = officialClientDecision(controller.decide(body(""),
                TestPlans.all("use"), human()).getBody(), "read");
        assertThat(suspended.isAllowed()).isFalse();
        assertThat(suspended.getCommercialPhase()).isEqualTo("ACTIVE");
        assertThat(suspended.getReasonCategory()).isEqualTo("GRANT_SUSPENDED");
        currentGrant.get().suspendOrResume(false);
        ClientDecision perpetual = officialClientDecision(controller.decide(body(""),
                TestPlans.all("use"), human()).getBody(), "read");
        assertThat(perpetual.isAllowed()).isTrue();
        assertThat(perpetual.getRecheckAt()).isNull();
        assertThat(perpetual.getModelVersion()).isZero();
    }

    @Test
    void modelVersionComesFromTheEvaluatedModelAndCommercialRevisionIsIndependent() throws Exception {
        GrantRepository grants = mock(GrantRepository.class);
        ModelVersionRepository models = mock(ModelVersionRepository.class);
        GrantRevisionEntity persisted = new GrantRevisionEntity("gr-1", "cu-1", "prod-1", "PERSON",
                "CUSTOMER_MEMBERS", null, "ONLINE", null, "model-version-7", "{}");
        persisted.setGrantRevision(2L);
        when(grants.findById("gr-1")).thenReturn(Optional.of(persisted));
        String snapshot = "{\"fields\":{\"rights\":{},\"facts\":{}},\"actions\":{\"read\":{\"bool\":true}}}";
        ModelVersionEntity model = new ModelVersionEntity("model-version-7", "model-1", "prod-1", 7L, snapshot);
        model.publish();
        when(models.findById("model-version-7")).thenReturn(Optional.of(model));
        long serviceUntil = Instant.now().getEpochSecond() + 3600L;
        currentGrant.set(GrantAggregate.load("gr-1", "cu-1", "prod-1", SubjectKind.PERSON,
                EligibilityPolicy.customerMembers(), ValidityPolicy.fixed(1L, serviceUntil, serviceUntil + 600L, null),
                EligibilityPolicy.Mode.ONLINE, GrantState.ENABLED, 2L, 1L));
        OnlineDecisionService realModel = new OnlineDecisionService(id -> Optional.of(currentGrant.get()),
                new ModelBackedRulePort(grants, models), admission, guard);
        LicenseDecisionController realController = new LicenseDecisionController(realModel, products);
        ClientDecision allowed = officialClientDecision(realController.decide(body(""),
                TestPlans.all("use"), human()).getBody(), "read");
        assertThat(allowed.isAllowed()).isTrue();
        assertThat(allowed.getModelVersion()).isEqualTo(7L);
        assertThat(allowed.getRecheckAt()).isEqualTo(serviceUntil);
        ModelVersionEntity deniedModel = new ModelVersionEntity("model-version-7", "model-1", "prod-1", 7L,
                snapshot.replace("true", "false"));
        deniedModel.publish();
        when(models.findById("model-version-7")).thenReturn(Optional.of(deniedModel));
        ClientDecision denied = officialClientDecision(realController.decide(body(""),
                TestPlans.all("use"), human()).getBody(), "read");
        assertThat(denied.isAllowed()).isFalse();
        assertThat(denied.getModelVersion()).isEqualTo(7L);
        assertThat(denied.getReasonCategory()).isEqualTo("RULE_DENIED");
    }

    /** 使用官方 client-core 解析真实 Controller JSON，不复制服务端字段规则作为测试解析器。 */
    @SuppressWarnings("unchecked")
    private ClientDecision officialClientDecision(String response, String action) throws Exception {
        Map<String, Object> wire = StrictJson.mapper().readValue(response, Map.class);
        return ClientResponseValidator.validateDecision(ClientResponseValidator.parseDecision(wire), action);
    }

    private String body(String extra) {
        return "{\"productId\":\"prod-1\",\"grantId\":\"gr-1\",\"subjectKind\":\"PERSON\",\"action\":\"read\""
                + extra + "}";
    }

    private HttpServletRequest human() {
        return human(true);
    }

    /** 管理台admin fixture不隐含在线evaluate权；本组显式装配真实人员运行调用的精确API投影。 */
    private HttpServletRequest human(boolean canEvaluate) {
        VerifiedResourceContext baseline = VerifiedContextFactory.create(true);
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                "simple-application-authorization", "1.0", ApplicationAuthorizationSubjectType.HUMAN,
                "zhangshuo", "license", true, Collections.emptyList(), Collections.emptyList(),
                canEvaluate ? Collections.singletonList("license.decision.evaluate") : Collections.emptyList(),
                baseline.getApplicationAuthorization().getDataGrantDocument(),
                1L, "1", "digest", Instant.now(), Instant.now().plusSeconds(3600));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new VerifiedResourceContext(baseline.getPrincipal(), authorization, "req-test-human-evaluator"), null));
        return new MockHttpServletRequest();
    }

    private HttpServletRequest service() {
        return service("aksk");
    }

    private HttpServletRequest service(String sourceId) {
        currentGrant.set(GrantAggregate.load("gr-1", "cu-1", "prod-1", SubjectKind.SERVICE,
                EligibilityPolicy.assignedSubjects(null), ValidityPolicy.perpetual(1L, null, Collections.emptyList()),
                EligibilityPolicy.Mode.ONLINE, GrantState.ENABLED, 1L, 1L));
        ApplicationAuthorizationContext baseline = VerifiedContextFactory.create(true).getApplicationAuthorization();
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                "simple-application-authorization", "1.0", ApplicationAuthorizationSubjectType.SERVICE,
                "product-machine", "license", true, Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList("license.decision.evaluate"), baseline.getDataGrantDocument(),
                1L, "1", "digest", Instant.now(), Instant.now().plusSeconds(3600));
        VerifiedResourcePrincipal principal = new VerifiedResourcePrincipal(new ResourceAuthenticationSourceId(sourceId),
                ResourceSubjectType.SERVICE, "product-machine");
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(
                new VerifiedResourceContext(principal, authorization, "req-test-service"), null));
        return new MockHttpServletRequest();
    }

    private void assertStatus(Runnable action, int status) {
        assertThatThrownBy(action::run).isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(status);
    }
}
