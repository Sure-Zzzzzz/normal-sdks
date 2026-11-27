package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.web.LicenseAccessScope;
import io.github.surezzzzzz.sdk.license.server.web.LicenseGrantAccessScopeResolver;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.Collection;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** License Grant 的 customerId/grantId 组合投影与失败关闭语义回归。 */
class LicenseGrantAccessScopeTest {
    private final GrantRepository grants = mock(GrantRepository.class);
    private final LicenseGrantAccessScopeResolver resolver = new LicenseGrantAccessScopeResolver(grants);

    @Test
    void customerIdResolvesToCurrentGrantIds() {
        when(grants.findByCustomerIdIn(anyCollection())).thenReturn(Arrays.asList(
                grant("grant-1", "cu-1"), grant("grant-2", "cu-2")));

        LicenseAccessScope scope = resolver.from(TestPlans.restrictedFor(
                TestPlans.RESOURCE, "read", "customerId", "cu-2", "cu-1"));

        assertThat(scope.isAll()).isFalse();
        assertThat(scope.restrictedIdsOrNull()).containsExactlyInAnyOrder("grant-1", "grant-2");
        verify(grants, times(1)).findByCustomerIdIn(anyCollection());
    }

    @Test
    void grantIdRemainsTheOldPrimaryKeyDimension() {
        LicenseAccessScope scope = resolver.from(TestPlans.restricted("read", "grant-1", "grant-2"));

        assertThat(scope.isAll()).isFalse();
        assertThat(scope.restrictedIdsOrNull()).containsExactlyInAnyOrder("grant-1", "grant-2");
        verify(grants, times(0)).findByCustomerIdIn(anyCollection());
    }

    @Test
    void grantIdAndCustomerIdConstraintsIntersectInsideOneGrant() {
        when(grants.findByCustomerIdIn(anyCollection())).thenReturn(Arrays.asList(
                grant("grant-2", "cu-1"), grant("grant-3", "cu-1")));

        LicenseAccessScope scope = resolver.from(restricted("read",
                constraint("grantId", "grant-1", "grant-2"),
                constraint("customerId", "cu-1")));

        assertThat(scope.restrictedIdsOrNull()).containsExactly("grant-2");
        scope.requireVisible("grant-2");
        assertThatThrownBy(() -> scope.requireVisible("grant-3"))
                .isInstanceOf(LicenseHttpException.class)
                .hasMessage("资源不存在或不可见");
    }

    @Test
    void differentGrantsRemainIndependentOrRanges() {
        when(grants.findByCustomerIdIn(anyCollection())).thenAnswer(call -> {
            Collection<?> customerIds = call.getArgument(0);
            if (customerIds.contains("cu-1")) {
                return Collections.singletonList(grant("grant-a", "cu-1"));
            }
            return Collections.singletonList(grant("grant-c", "cu-2"));
        });

        DataGrant first = new DataGrant(TestPlans.RESOURCE, Collections.singletonList("read"), false,
                Arrays.asList(constraint("grantId", "grant-a"), constraint("customerId", "cu-1")));
        DataGrant second = new DataGrant(TestPlans.RESOURCE, Collections.singletonList("read"), false,
                Collections.singletonList(constraint("customerId", "cu-2")));

        LicenseAccessScope scope = resolver.from(plan("read", first, second));

        assertThat(scope.restrictedIdsOrNull()).containsExactlyInAnyOrder("grant-a", "grant-c");
    }

    @Test
    void emptyCustomerResolutionStaysEmptyAndDoesNotWiden() {
        when(grants.findByCustomerIdIn(anyCollection())).thenReturn(Collections.emptyList());

        LicenseAccessScope scope = resolver.from(TestPlans.restrictedFor(
                TestPlans.RESOURCE, "read", "customerId", "missing-customer"));

        assertThat(scope.isAll()).isFalse();
        assertThat(scope.restrictedIdsOrNull()).isEmpty();
        assertThat(scope.contains("grant-1")).isFalse();
    }

    @Test
    void unknownDimensionAndMissingCustomerResolverFailClosed() {
        assertThatThrownBy(() -> LicenseAccessScope.fromGrantDimension(
                TestPlans.restrictedFor(TestPlans.RESOURCE, "read", "tenantId", "tenant-1"), null))
                .isInstanceOf(LicenseHttpException.class)
                .hasMessage("无权访问该资源");
        assertThatThrownBy(() -> LicenseAccessScope.fromGrantDimension(
                TestPlans.restrictedFor(TestPlans.RESOURCE, "read", "customerId", "cu-1"), null))
                .isInstanceOf(LicenseHttpException.class)
                .hasMessage("无权访问该资源");
    }

    private static GrantRevisionEntity grant(String grantId, String customerId) {
        GrantRevisionEntity entity = new GrantRevisionEntity(grantId, customerId, "product-1",
                "PERSON", "CUSTOMER_MEMBERS", null, "ONLINE", null);
        entity.setState("ENABLED");
        return entity;
    }

    private static DataConstraint constraint(String dimension, String... values) {
        return new DataConstraint(dimension, DataConstraintOperator.IN, Arrays.asList(values));
    }

    private static DataAccessPlan restricted(String action, DataConstraint... firstConstraints) {
        DataGrant firstGrant = new DataGrant(TestPlans.RESOURCE,
                Collections.singletonList(action), false, Arrays.asList(firstConstraints));
        return plan(action, firstGrant);
    }

    private static DataAccessPlan plan(String action, DataGrant... grants) {
        return DataAccessPlan.evaluate(
                new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                        SimpleDataPermissionConstant.VERSION, Arrays.asList(grants)),
                new DataPermissionRequest(TestPlans.RESOURCE, action));
    }
}
