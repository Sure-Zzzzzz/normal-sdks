package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ManagementAccessDeniedException;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.CrossResourceDataPlanHelper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.data.permission.core.constant.SimpleDataPermissionConstant;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.spring.mvc.support.DataPermissionFacade;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 跨资源数据计划 helper 的双形态应用编码回归测试。
 *
 * <p>SERVICE 本地授权 applicationCode=aksk-server，HUMAN console 令牌的 IAM 投影
 * applicationCode=可信应用编码（如 aksk）；API 评估必须按上下文自身编码进行，
 * 否则 HUMAN 形态在删 Client 等跨资源端点会被应用编码比对误拒（403）。</p>
 */
class CrossResourceDataPlanHelperTest {

    private final DataPermissionFacade facade = Mockito.mock(DataPermissionFacade.class);
    private final CrossResourceDataPlanHelper helper = new CrossResourceDataPlanHelper(facade);

    @BeforeEach
    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAllowHumanProjectionWithTrustedApplicationCode() {
        SecurityContextHolder.getContext().setAuthentication(context("aksk"));
        DataAccessPlan plan = DataAccessPlan.deny();
        when(facade.require("akskToken", "update")).thenReturn(plan);

        assertSame(plan, helper.require("akskToken", "update", "akskToken:update"));
    }

    @Test
    void shouldAllowServiceAuthorizationWithServerApplicationCode() {
        SecurityContextHolder.getContext().setAuthentication(context("aksk-server"));
        DataAccessPlan plan = DataAccessPlan.deny();
        when(facade.require("akskToken", "update")).thenReturn(plan);

        assertSame(plan, helper.require("akskToken", "update", "akskToken:update"));
    }

    @Test
    void shouldDenyWhenApiPermissionMissing() {
        SecurityContextHolder.getContext().setAuthentication(context("aksk", false));

        assertThrows(ManagementAccessDeniedException.class,
                () -> helper.require("akskToken", "update", "akskToken:update"));
    }

    private org.springframework.security.core.Authentication context(String applicationCode) {
        return context(applicationCode, true);
    }

    private org.springframework.security.core.Authentication context(String applicationCode, boolean withTokenUpdate) {
        ApplicationAuthorizationContext authorization = withTokenUpdate
                ? authorization(applicationCode) : authorizationWithoutTokenUpdate();
        io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal principal =
                new io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal(
                        new io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId("iam"),
                        io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType.HUMAN,
                        authorization.getSubjectId());
        return new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                new io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext(
                        principal, authorization, "req-cross-resource-test"),
                null, Collections.emptyList());
    }

    private ApplicationAuthorizationContext authorization(String applicationCode) {
        return new ApplicationAuthorizationContext("simple-application-authorization", "1.0",
                ApplicationAuthorizationSubjectType.HUMAN, "usr_admin", applicationCode, true,
                Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList("akskToken:update"),
                new io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument(
                        SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                        Collections.singletonList(new io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant(
                                "akskToken", Collections.singletonList("update"), true, Collections.emptyList()))),
                1L, "1", "digest", Instant.now(), Instant.now().plusSeconds(600L));
    }

    private ApplicationAuthorizationContext authorizationWithoutTokenUpdate() {
        return new ApplicationAuthorizationContext("simple-application-authorization", "1.0",
                ApplicationAuthorizationSubjectType.HUMAN, "usr_admin", "aksk", true,
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                new io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrantDocument(
                        SimpleDataPermissionConstant.PROTOCOL, SimpleDataPermissionConstant.VERSION,
                        Collections.singletonList(new io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataGrant(
                                "akskToken", Collections.singletonList("update"), true, Collections.emptyList()))),
                1L, "1", "digest", Instant.now(), Instant.now().plusSeconds(600L));
    }
}
