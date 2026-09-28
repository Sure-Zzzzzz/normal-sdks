package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.AkskPersonalCredentialAuthorizationHelper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.DataConstraintOperator;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 继承型 AKU 对个人凭证资源追加 owner 上限的回归测试。
 */
class AkskPersonalCredentialAuthorizationHelperTest {

    private static final String OWNER = "user-1001";

    @Test
    void shouldSplitAllGrantIntoOwnerCeilingAndPlatformRow() {
        ApplicationAuthorizationContext restricted = AkskPersonalCredentialAuthorizationHelper.restrictToOwner(
                authorization(document(allGrant(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_CLIENT))), OWNER);

        // 全量授予拆两条：用户行收窄到本人（天花板不放宽）；平台行按平台类型放行（AKP 无 owner，
        // 不属于个人凭证天花板；特权管理员由此获得平台凭证的治理面）。
        assertEquals(2, restricted.getDataGrantDocument().getGrants().size());
        DataGrant userRow = restricted.getDataGrantDocument().getGrants().stream()
                .filter(grant -> grant.getConstraints().stream().anyMatch(constraint ->
                        SimpleAkskServerConstant.MANAGEMENT_DIMENSION_OWNER_USER_ID.equals(constraint.getDimension())))
                .findFirst().orElseThrow(() -> new IllegalStateException("缺少用户行 owner 收窄条目"));
        assertFalse(userRow.isAll());
        assertEquals(Collections.singletonList(OWNER), userRow.getConstraints().stream()
                .filter(constraint -> SimpleAkskServerConstant.MANAGEMENT_DIMENSION_OWNER_USER_ID
                        .equals(constraint.getDimension()))
                .findFirst().orElseThrow(() -> new IllegalStateException("缺少 owner 约束")).getValues());
        DataGrant platformRow = restricted.getDataGrantDocument().getGrants().stream()
                .filter(grant -> grant.getConstraints().stream().anyMatch(constraint ->
                        SimpleAkskServerConstant.MANAGEMENT_DIMENSION_CLIENT_TYPE.equals(constraint.getDimension())))
                .findFirst().orElseThrow(() -> new IllegalStateException("缺少平台行放行条目"));
        assertFalse(platformRow.isAll());
        assertEquals(Collections.singletonList(io.github.surezzzzzz.sdk.auth.aksk.core.constant.ClientType.PLATFORM
                .getValue()), platformRow.getConstraints().stream()
                .filter(constraint -> SimpleAkskServerConstant.MANAGEMENT_DIMENSION_CLIENT_TYPE
                        .equals(constraint.getDimension()))
                .findFirst().orElseThrow(() -> new IllegalStateException("缺少 clientType 约束")).getValues());
    }

    @Test
    void shouldKeepUnrelatedResourceAndIntersectExistingOwnerConstraint() {
        DataGrant unrelated = allGrant("otherResource");
        DataGrant personal = new DataGrant(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_TOKEN,
                Collections.singletonList(SimpleAkskServerConstant.MANAGEMENT_ACTION_READ), false,
                Arrays.asList(constraint("tenantId", "tenant-a"), constraint(
                        SimpleAkskServerConstant.MANAGEMENT_DIMENSION_OWNER_USER_ID, OWNER, "user-2002")));

        ApplicationAuthorizationContext restricted = AkskPersonalCredentialAuthorizationHelper.restrictToOwner(
                authorization(document(unrelated, personal)), OWNER);

        assertEquals(unrelated, grantByResource(restricted, unrelated.getResource()));
        DataGrant personalGrant = grantByResource(restricted, personal.getResource());
        assertEquals(2, personalGrant.getConstraints().size());
        assertEquals(Collections.singletonList(OWNER), personalGrant.getConstraints().get(0).getValues());
    }

    @Test
    void shouldDropNonMatchingPersonalGrantAndFailClosedForBlankOwner() {
        DataGrant personal = new DataGrant(SimpleAkskServerConstant.MANAGEMENT_RESOURCE_CLIENT,
                Collections.singletonList(SimpleAkskServerConstant.MANAGEMENT_ACTION_READ), false,
                Collections.singletonList(constraint(SimpleAkskServerConstant.MANAGEMENT_DIMENSION_OWNER_USER_ID,
                        "user-2002")));

        ApplicationAuthorizationContext restricted = AkskPersonalCredentialAuthorizationHelper.restrictToOwner(
                authorization(document(personal)), OWNER);

        assertNull(restricted.getDataGrantDocument());
        assertNull(AkskPersonalCredentialAuthorizationHelper.restrictToOwner(
                authorization(document(personal)), " "));
        assertNull(AkskPersonalCredentialAuthorizationHelper.restrictToOwner(
                authorization(document(personal)), "user-2002"));
    }

    private ApplicationAuthorizationContext authorization(DataGrantDocument document) {
        Instant issuedAt = Instant.parse("2026-09-23T00:00:00Z");
        return new ApplicationAuthorizationContext(SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION, ApplicationAuthorizationSubjectType.HUMAN, OWNER,
                "aksk", true, Collections.singletonList("iam_user"), Collections.<String>emptyList(),
                Collections.singletonList(SimpleAkskServerConstant.MANAGEMENT_PERMISSION_CLIENT_READ), document, 1L,
                "1", "a".repeat(64), issuedAt, issuedAt.plusSeconds(300L));
    }

    private DataGrantDocument document(DataGrant... grants) {
        return new DataGrantDocument(SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                Arrays.asList(grants));
    }

    private DataGrant allGrant(String resource) {
        return new DataGrant(resource, Collections.singletonList(SimpleAkskServerConstant.MANAGEMENT_ACTION_READ), true,
                Collections.<DataConstraint>emptyList());
    }

    private DataConstraint constraint(String dimension, String... values) {
        return new DataConstraint(dimension, DataConstraintOperator.IN, Arrays.asList(values));
    }

    private DataGrant grantByResource(ApplicationAuthorizationContext authorization, String resource) {
        return authorization.getDataGrantDocument().getGrants().stream()
                .filter(grant -> resource.equals(grant.getResource()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少数据授权资源：" + resource));
    }
}
