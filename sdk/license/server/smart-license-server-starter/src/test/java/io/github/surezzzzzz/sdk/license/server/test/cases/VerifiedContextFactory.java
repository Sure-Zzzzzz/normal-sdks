package io.github.surezzzzzz.sdk.license.server.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 测试用公共资源链结论工厂：构造 zhangshuo 人员主体 + 六资源 ALLOW_ALL DATA 投影
 * （与走查角色 license-walk-admin 的授权形态一致）。
 *
 * @author surezzzzzz
 */
public final class VerifiedContextFactory {

    public static VerifiedResourceContext create(boolean withData) {
        VerifiedResourcePrincipal principal = new VerifiedResourcePrincipal(
                new ResourceAuthenticationSourceId("iam"), ResourceSubjectType.HUMAN, "zhangshuo");
        List<String> apis = Arrays.asList(
                "license.me.read",
                "license.issuer.read", "license.issuer.manage",
                "license.customer.read", "license.customer.manage",
                "license.product.read", "license.product.manage",
                "license.signing-profile.read", "license.signing-profile.manage",
                "license.grant.read", "license.grant.manage", "license.grant.issue", "license.grant.deliver",
                "license.subject-assignment.read", "license.subject-assignment.manage",
                "license.activation.read", "license.activation.manage",
                "license.revocation.read", "license.revocation.manage",
                "license.operation.read", "license.operation.recover",
                "license.integration.read", "license.integration.manage");
        DataGrantDocument data = withData
                ? new DataGrantDocument("simple-data-permission", "1.0", Arrays.asList(
                        new DataGrant("license-issuer", Arrays.asList("read", "manage"), true, java.util.Collections.<io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint>emptyList()),
                        new DataGrant("license-customer", Arrays.asList("read", "manage"), true, java.util.Collections.<io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint>emptyList()),
                        new DataGrant("license-product", Arrays.asList("read", "manage"), true, java.util.Collections.<io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint>emptyList()),
                        new DataGrant("license-grant", Arrays.asList("read", "manage", "issue", "revoke", "deliver", "use"), true,
                        java.util.Collections.<io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint>emptyList()),
                new DataGrant("license-signing-profile", Arrays.asList("read", "manage"), true, java.util.Collections.<io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint>emptyList()),
                        new DataGrant("license-integration", Arrays.asList("read", "manage"), true, java.util.Collections.<io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint>emptyList())))
                : new DataGrantDocument("simple-data-permission", "1.0",
                        Collections.singletonList(new DataGrant("unrelated-resource",
                                Collections.singletonList("read"), true,
                                Collections.<io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataConstraint>emptyList())));
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                "simple-application-authorization", "1.0", ApplicationAuthorizationSubjectType.HUMAN,
                "zhangshuo", "license", true,
                Collections.singletonList("license-walk-admin"), Collections.singletonList("license.page.issuers"),
                apis, data, 1L, "1", "digest", Instant.now(), Instant.now().plusSeconds(3600));
        return new VerifiedResourceContext(principal, authorization, "req-test-0001");
    }

    private VerifiedContextFactory() {
    }
}
