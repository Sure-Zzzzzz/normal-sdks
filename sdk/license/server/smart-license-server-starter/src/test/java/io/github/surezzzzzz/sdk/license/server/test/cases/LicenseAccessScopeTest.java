package io.github.surezzzzzz.sdk.license.server.test.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataPermissionRequest;
import io.github.surezzzzzz.sdk.license.server.web.LicenseAccessScope;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * DATA 范围投影断言（KmsOwnerAccessScopeTest 对位）：全量/受限 IN/未知维度/deny 的
 * 翻译语义——列表、详情、写入共用同一范围。
 *
 * @author surezzzzzz
 */
class LicenseAccessScopeTest {

    @Test
    void allPlanMapsToUnrestrictedScope() {
        LicenseAccessScope scope = LicenseAccessScope.from(plan(
                new DataGrant("license-grant", Collections.singletonList("read"), true,
                        Collections.<DataConstraint>emptyList())), "grantId");
        assertThat(scope.isAll()).isTrue();
        assertThat(scope.restrictedIdsOrNull()).isNull();
        assertThatCode(() -> scope.requireAllowed("gr-any")).doesNotThrowAnyException();
    }

    @Test
    void restrictedPlanCollectsInValues() {
        LicenseAccessScope scope = LicenseAccessScope.from(plan(
                new DataGrant("license-grant", Collections.singletonList("read"), false,
                        Collections.singletonList(new DataConstraint("grantId",
                                DataConstraintOperator.IN, Arrays.asList("gr-1", "gr-2"))))), "grantId");
        assertThat(scope.isAll()).isFalse();
        assertThat(scope.restrictedIdsOrNull()).containsExactlyInAnyOrder("gr-1", "gr-2");
        assertThatCode(() -> scope.requireAllowed("gr-1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> scope.requireAllowed("gr-3"))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus())
                .isEqualTo(403);
    }

    @Test
    void childResourceOutsideDataScopeIsHiddenAsNotFound() {
        LicenseAccessScope scope = LicenseAccessScope.from(plan(
                new DataGrant("license-grant", Collections.singletonList("read"), false,
                        Collections.singletonList(new DataConstraint("grantId",
                                DataConstraintOperator.IN, Collections.singletonList("gr-1"))))), "grantId");
        assertThatCode(() -> scope.requireVisible("gr-1")).doesNotThrowAnyException();
        assertThatThrownBy(() -> scope.requireVisible("gr-2"))
                .isInstanceOf(LicenseHttpException.class)
                .extracting(ex -> ((LicenseHttpException) ex).getStatus()).isEqualTo(404);
    }

    @Test
    void unknownDimensionOrForeignOperatorRejectedClosed() {
        // 未知维度：不忽略约束，整体 403
        assertThatThrownBy(() -> LicenseAccessScope.from(plan(
                new DataGrant("license-grant", Collections.singletonList("read"), false,
                        Collections.singletonList(new DataConstraint("region",
                                DataConstraintOperator.IN, Collections.singletonList("east"))))), "grantId"))
                .isInstanceOf(LicenseHttpException.class);
        // deny 计划：无可执行范围
        assertThatThrownBy(() -> LicenseAccessScope.from(DataAccessPlan.deny(), "grantId"))
                .isInstanceOf(LicenseHttpException.class);
    }

    private DataAccessPlan plan(DataGrant grant) {
        return DataAccessPlan.evaluate(
                new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL,
                        SimpleDataPermissionConstant.VERSION, Collections.singletonList(grant)),
                new DataPermissionRequest("license-grant", "read"));
    }
}
