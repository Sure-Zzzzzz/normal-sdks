package io.github.surezzzzzz.sdk.license.server.test.cases;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.controller.LicenseIntegrationController;
import io.github.surezzzzzz.sdk.license.server.entity.IntegrationPlanEntity;
import io.github.surezzzzzz.sdk.license.server.integration.RoleProjectionService;
import io.github.surezzzzzz.sdk.license.server.repository.IntegrationPlanRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Collections;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** 管理HTTP权限、严格请求与协作形态；真实角色业务另由服务测试覆盖。 */
@Slf4j
class LicenseIntegrationHttpTest {
    private final IntegrationPlanRepository plans = Mockito.mock(IntegrationPlanRepository.class);
    private final RoleProjectionService projections = Mockito.mock(RoleProjectionService.class);
    private final LicenseIntegrationController controller = new LicenseIntegrationController(plans, projections);

    @Test
    void createDelegatesExplicitApprovedRulesRatherThanBusinessActions() {
        IntegrationPlanEntity entity = entity();
        when(projections.create(eq("cu-1"), eq("prod-1"), eq(9L), any(ObjectNode.class), anyString(), anyString())).thenReturn(entity);
        String body = "{\"customerId\":\"cu-1\",\"applicationId\":\"9\",\"desiredRules\":{\"pagePermissions\":[],\"apiPermissions\":[\"app.read\"],\"dataGrantTemplate\":null}}";
        log.debug("核验批准规则精确委托且不读取产品商业动作");
        assertThat(controller.createPlan("prod-1", body, "idem", LicenseIdentityHttpTest.TestRequests.authenticated())
                .getStatusCodeValue()).isEqualTo(201);
        verify(projections).create(eq("cu-1"), eq("prod-1"), eq(9L), argThat(rules ->
                "app.read".equals(rules.path("apiPermissions").get(0).asText())), anyString(), anyString());
    }

    @Test
    void deniedGovernanceTouchesNoPlanOrRemoteService() {
        assertThatThrownBy(() -> controller.attempt("foreign", "idem", null,
                LicenseIdentityHttpTest.TestRequests.noDataGrant())).isInstanceOf(LicenseHttpException.class)
                .extracting(failure -> ((LicenseHttpException) failure).getStatus()).isEqualTo(403);
        verifyNoInteractions(plans, projections);
    }

    @Test
    void manualIsDefaultAndAutoRequiresExplicitSelection() {
        when(projections.attempt(eq("ic-1"), eq("MANUAL"), anyString(), anyString())).thenReturn(entity());
        when(projections.attempt(eq("ic-1"), eq("AUTO"), anyString(), anyString())).thenReturn(entity());
        controller.attempt("ic-1", "i1", null, LicenseIdentityHttpTest.TestRequests.authenticated());
        controller.attempt("ic-1", "i2", "{\"executionMode\":\"AUTO\"}", LicenseIdentityHttpTest.TestRequests.authenticated());
        verify(projections).attempt(eq("ic-1"), eq("MANUAL"), anyString(), anyString());
        verify(projections).attempt(eq("ic-1"), eq("AUTO"), anyString(), anyString());
    }

    @Test
    void confirmRequiresTypedEnvironmentAndBindingGeneration() {
        when(plans.findById("p1")).thenReturn(Optional.of(entity()));
        assertThatThrownBy(() -> controller.confirm("p1", "{\"expectedRevision\":\"1\"}",
                TestPlans.allFor("license-integration", "manage"), LicenseIdentityHttpTest.TestRequests.authenticated()))
                .isInstanceOf(LicenseHttpException.class);
        verifyNoInteractions(projections);
        when(projections.confirm("p1", 1L, "env-1", 1, "role-1")).thenReturn(entity());
        controller.confirm("p1", "{\"expectedRevision\":1,\"organizationInstanceId\":\"env-1\",\"bindingGeneration\":1,\"openRoleId\":\"role-1\"}",
                TestPlans.allFor("license-integration", "manage"), LicenseIdentityHttpTest.TestRequests.authenticated());
        verify(projections).confirm("p1", 1L, "env-1", 1, "role-1");
    }

    @Test
    void restrictedListsReadOnlyTheirCustomerRange() {
        when(plans.findByCustomerIdIn(any())).thenReturn(Collections.singletonList(entity()));
        assertThat(controller.listCommands(LicenseIdentityHttpTest.TestRequests.authenticated(),
                TestPlans.restrictedFor("license-integration", "read", "customerId", "cu-1"), 1, 20)
                .getBody()).contains("cu-1");
        verify(plans, never()).findAll();
        verify(plans).findByCustomerIdIn(argThat(ids -> {
            java.util.List<String> list = new java.util.ArrayList<>(); ids.forEach(list::add);
            return list.equals(Collections.singletonList("cu-1"));
        }));
    }

    @Test
    void planDetailHidesForeignAndMissingCustomersWithTheSameStatus() {
        when(plans.findById("p1")).thenReturn(Optional.of(entity()));
        int foreignStatus = status(() -> controller.getPlan("p1",
                TestPlans.restrictedFor("license-integration", "read", "customerId", "cu-visible"),
                LicenseIdentityHttpTest.TestRequests.authenticated()));
        int missingStatus = status(() -> controller.getPlan("missing",
                TestPlans.restrictedFor("license-integration", "read", "customerId", "cu-visible"),
                LicenseIdentityHttpTest.TestRequests.authenticated()));
        assertThat(foreignStatus).isEqualTo(404);
        assertThat(missingStatus).isEqualTo(foreignStatus);
    }

    @Test
    void pageOverflowAndInvalidSizeDoNotOverflowOrReadTheRepository() {
        when(plans.findAll()).thenReturn(Collections.singletonList(entity()));
        assertThat(controller.listCommands(LicenseIdentityHttpTest.TestRequests.authenticated(),
                TestPlans.allFor("license-integration", "read"), Integer.MAX_VALUE, 200).getBody()).contains("\"data\":[]");
        assertThatThrownBy(() -> controller.listCommands(LicenseIdentityHttpTest.TestRequests.authenticated(),
                TestPlans.allFor("license-integration", "read"), 1, 0)).isInstanceOf(LicenseHttpException.class);
        verify(plans, times(1)).findAll();
    }

    private static IntegrationPlanEntity entity() {
        return new IntegrationPlanEntity("p1", "ic-1", "cu-1", "prod-1", "9",
                "{\"pagePermissions\":[],\"apiPermissions\":[],\"dataGrantTemplate\":null}",
                "{\"toMount\":[],\"toUnmount\":[]}", 1, Long.MAX_VALUE);
    }

    private static int status(org.assertj.core.api.ThrowableAssert.ThrowingCallable call) {
        try {
            call.call();
            throw new AssertionError("预期 LicenseHttpException");
        } catch (Throwable failure) {
            assertThat(failure).isInstanceOf(LicenseHttpException.class);
            return ((LicenseHttpException) failure).getStatus();
        }
    }
}
