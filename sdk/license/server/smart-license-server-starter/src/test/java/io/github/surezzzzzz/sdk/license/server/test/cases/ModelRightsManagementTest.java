package io.github.surezzzzzz.sdk.license.server.test.cases;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.controller.LicenseGrantController;
import io.github.surezzzzzz.sdk.license.server.controller.LicensePlanController;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.PlanVersionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.repository.*;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 管理面权益回归：四类型按模型存储，非法模板或不完整商业授权不得先落库再等待签发失败。
 *
 * @author surezzzzzz
 */
@Slf4j
class ModelRightsManagementTest {

    private static final String SNAPSHOT = LicenseTestModels.publishedRightsSnapshot();

    private GrantRepository grants;
    private PlanVersionRepository plans;
    private LicenseGrantController grantController;
    private LicensePlanController planController;

    @BeforeEach
    void setUp() {
        grants = mock(GrantRepository.class);
        plans = mock(PlanVersionRepository.class);
        ModelVersionRepository models = mock(ModelVersionRepository.class);
        ProductRepository products = mock(ProductRepository.class);
        ModelVersionEntity model = new ModelVersionEntity("model-version", "model", "product", 1L, SNAPSHOT);
        model.publish();
        when(models.findById("model-version")).thenReturn(Optional.of(model));
        when(products.findById("product")).thenReturn(Optional.of(new ProductEntity(
                "product", "issuer", "product-code", "示例产品", "[\"use\"]", "[]")));
        when(grants.save(any(GrantRevisionEntity.class))).thenAnswer(call -> call.getArgument(0));
        when(plans.save(any(PlanVersionEntity.class))).thenAnswer(call -> call.getArgument(0));
        grantController = new LicenseGrantController(grants, products, mock(SigningProfileRepository.class),
                mock(SigningOperationRepository.class), null, models, null, null);
        planController = new LicensePlanController(plans, models);
    }

    @Test
    void grantKeepsAllFourRightTypes() throws Exception {
        JsonNode created = StrictJson.mapper().readTree(grantController.createGrant(grantBody(
                        "{\"count\":7,\"enabled\":false,\"tier\":\"pro\",\"regions\":[\"east\",\"west\"]}"),
                "rights-create", LicenseIdentityHttpTest.TestRequests.authenticated()).getBody());
        assertThat(created.path("rights").path("count").asInt()).isEqualTo(7);
        assertThat(created.path("rights").path("enabled").isBoolean()).isTrue();
        assertThat(created.path("rights").path("enabled").asBoolean()).isFalse();
        assertThat(created.path("rights").path("tier").asText()).isEqualTo("pro");
        assertThat(created.path("rights").path("regions").size()).isEqualTo(2);
    }

    @Test
    void grantNormalizesLimiterStableCodesAndAcceptsLongBoundary() throws Exception {
        String policies = "[{\"serviceCode\":\" orders.v1 \",\"resourceCode\":\" create-item \","
                + "\"limits\":[{\"count\":9223372036854775807,\"window\":1,\"unit\":\"seconds\"}]}]";
        JsonNode created = StrictJson.mapper().readTree(grantController.createGrant(
                grantBodyWithLimiter("{\"count\":7,\"enabled\":false,\"tier\":\"pro\",\"regions\":[\"east\",\"west\"]}",
                        policies), "limiter-valid",
                LicenseIdentityHttpTest.TestRequests.authenticated()).getBody());

        JsonNode policy = created.path("limiterPolicies").get(0);
        assertThat(policy.path("serviceCode").asText()).isEqualTo("orders.v1");
        assertThat(policy.path("resourceCode").asText()).isEqualTo("create-item");
        assertThat(policy.path("limits").get(0).path("count").longValue()).isEqualTo(Long.MAX_VALUE);
        assertThat(policy.path("limits").get(0).path("unit").asText()).isEqualTo("SECONDS");
    }

    @Test
    void invalidLimiterStableCodesAndOverflowingLongsAreRejectedBeforePersistence() {
        for (String code : new String[]{"has space", "service:api", "-service", "服务"}) {
            assertInvalidLimiterPolicy("[{\"serviceCode\":\"" + code + "\",\"resourceCode\":\"resource\","
                    + "\"limits\":[{\"count\":1,\"window\":1,\"unit\":\"SECONDS\"}]}]");
            assertInvalidLimiterPolicy("[{\"serviceCode\":\"service\",\"resourceCode\":\"" + code + "\","
                    + "\"limits\":[{\"count\":1,\"window\":1,\"unit\":\"SECONDS\"}]}]");
        }
        assertInvalidLimiterPolicy("[{\"serviceCode\":\"service\",\"resourceCode\":\"resource\","
                + "\"limits\":[{\"count\":18446744073709551617,\"window\":1,\"unit\":\"SECONDS\"}]}]");
        assertInvalidLimiterPolicy("[{\"serviceCode\":\"service\",\"resourceCode\":\"resource\","
                + "\"limits\":[{\"count\":1,\"window\":18446744073709551617,\"unit\":\"SECONDS\"}]}]");
        verify(grants, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"count\":1,\"tier\":\"unknown\"}",
            "{\"count\":1,\"tier\":\"overlong\"}", "{\"count\":1,\"regions\":[\"a\",\"b\",\"c\"]}",
            "{\"count\":9223372036854775808}", "{\"count\":1,\"enabled\":\"false\"}"})
    void invalidGrantRightsAreRejectedBeforePersistence(String rights) {
        assertThatThrownBy(() -> grantController.createGrant(grantBody(rights), "rights-invalid",
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(422);
        verify(grants, never()).save(any());
    }

    @Test
    void planAllowsPartialTypedPrefillAndRejectsInvalidValues() {
        planController.createPlan(planBody("{\"enabled\":false,\"tier\":\"basic\"}"), "prefill-valid",
                LicenseIdentityHttpTest.TestRequests.authenticated());
        clearInvocations(plans);
        assertThatThrownBy(() -> planController.createPlan(planBody("{\"tier\":\"other\"}"), "prefill-invalid",
                LicenseIdentityHttpTest.TestRequests.authenticated())).isInstanceOf(LicenseHttpException.class);
        verifyNoInteractions(plans);
    }

    @Test
    void planDetailHidesForeignAndMissingProductsWithTheSameStatus() {
        when(plans.findById("pv-hidden")).thenReturn(Optional.of(new PlanVersionEntity(
                "pv-hidden", "plan-hidden", "product-hidden", "model-version", 1L, "{}")));
        assertThatThrownBy(() -> planController.getVersion("pv-hidden",
                TestPlans.restrictedFor("license-product", "read", "productId", "product-visible"),
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(404);
        assertThatThrownBy(() -> planController.getVersion("pv-missing",
                TestPlans.restrictedFor("license-product", "read", "productId", "product-visible"),
                LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(404);
    }

    @Test
    void draftUpdateRejectsInvalidTypedPrefillBeforeSaving() {
        PlanVersionEntity draft = new PlanVersionEntity("plan-version", "plan", "product",
                "model-version", 1L, "{\"tier\":\"basic\"}");
        when(plans.findById("plan-version")).thenReturn(Optional.of(draft));
        assertThatThrownBy(() -> planController.patchDraft("plan-version",
                "{\"prefillRights\":{\"regions\":[1]},\"expectedRowVersion\":0}",
                TestPlans.allFor("license-product", "manage"),
                LicenseIdentityHttpTest.TestRequests.authenticated())).isInstanceOf(LicenseHttpException.class);
        assertThat(draft.getPrefillRights()).isEqualTo("{\"tier\":\"basic\"}");
        verify(plans, never()).save(any());
    }

    private static String grantBody(String rights) {
        return "{\"customerId\":\"customer\",\"productId\":\"product\",\"subjectKind\":\"PERSON\","
                + "\"eligibilityKind\":\"CUSTOMER_MEMBERS\",\"mode\":\"HYBRID\","
                + "\"offlineProofProfile\":\"iam\","
                + "\"modelVersionId\":\"model-version\",\"rights\":" + rights + "}";
    }

    private void assertInvalidLimiterPolicy(String policies) {
        assertThatThrownBy(() -> grantController.createGrant(grantBodyWithLimiter("{}", policies),
                "limiter-invalid", LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(422);
    }

    private static String grantBodyWithLimiter(String rights, String policies) {
        return grantBody(rights).replace("\"rights\":" + rights,
                "\"rights\":" + rights + ",\"limiterPolicies\":" + policies);
    }

    private static String planBody(String rights) {
        return "{\"productId\":\"product\",\"modelVersionId\":\"model-version\",\"prefillRights\":" + rights + "}";
    }
}
