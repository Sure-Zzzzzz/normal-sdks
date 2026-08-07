package io.github.surezzzzzz.sdk.auth.authorization.application.core.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationDecision;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.support.DefaultApplicationAuthorizationEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 默认应用 API 授权判定器测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultApplicationAuthorizationEvaluatorTest {

    private static final Instant NOW = Instant.ofEpochSecond(150L);

    @Test
    void shouldAllowOnlyExactCaseSensitiveApiPermissionForAdmittedApplication() {
        DefaultApplicationAuthorizationEvaluator evaluator = evaluator();
        ApplicationAuthorizationContext context = context(Arrays.asList("api.read", "api.write"),
                Collections.singletonList("page.read"), Collections.singletonList("role-admin"),
                Instant.ofEpochSecond(100L), Instant.ofEpochSecond(200L));

        ApplicationAuthorizationDecision decision = evaluator.evaluateApi(context, "application-a", "api.read");

        log.info("精确API权限判定结果：{}", decision);
        assertEquals(ApplicationAuthorizationDecision.ALLOW, decision, "已准入应用的精确API权限必须放行");
        assertEquals(ApplicationAuthorizationDecision.DENY, evaluator.evaluateApi(context, "application-a", "API.READ"),
                "API权限必须区分大小写");
        assertEquals(ApplicationAuthorizationDecision.DENY, evaluator.evaluateApi(context, "application-b", "api.read"),
                "错误应用标识必须拒绝");
    }

    @Test
    void shouldDenyWhenOnlyPagePermissionRoleOrSimilarApiPermissionExists() {
        DefaultApplicationAuthorizationEvaluator evaluator = evaluator();
        ApplicationAuthorizationContext context = context(Collections.singletonList("api.read.detail"),
                Collections.singletonList("api.read"), Collections.singletonList("api.read"),
                Instant.ofEpochSecond(100L), Instant.ofEpochSecond(200L));

        ApplicationAuthorizationDecision decision = evaluator.evaluateApi(context, "application-a", "api.read");

        log.info("PAGE、角色和相似权限判定结果：{}", decision);
        assertEquals(ApplicationAuthorizationDecision.DENY, decision,
                "PAGE、角色和相似API权限都不得推导精确API授权");
    }

    @Test
    void shouldDenyExpiredAndNotYetIssuedContexts() {
        DefaultApplicationAuthorizationEvaluator evaluator = evaluator();
        ApplicationAuthorizationContext expired = context(Collections.singletonList("api.read"),
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Instant.ofEpochSecond(100L), Instant.ofEpochSecond(150L));
        ApplicationAuthorizationContext future = context(Collections.singletonList("api.read"),
                Collections.<String>emptyList(), Collections.<String>emptyList(),
                Instant.ofEpochSecond(151L), Instant.ofEpochSecond(200L));

        log.info("过期、未签发上下文均应拒绝");
        assertEquals(ApplicationAuthorizationDecision.DENY, evaluator.evaluateApi(expired, "application-a", "api.read"),
                "到期时刻及之后必须拒绝");
        assertEquals(ApplicationAuthorizationDecision.DENY, evaluator.evaluateApi(future, "application-a", "api.read"),
                "签发时刻之前必须拒绝");
    }

    private DefaultApplicationAuthorizationEvaluator evaluator() {
        return new DefaultApplicationAuthorizationEvaluator(Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private ApplicationAuthorizationContext context(java.util.Collection<String> apiPermissions,
                                                    java.util.Collection<String> pagePermissions,
                                                    java.util.Collection<String> roles, Instant issuedAt, Instant expiresAt) {
        return new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.SERVICE,
                "service-a",
                "application-a",
                true,
                roles,
                pagePermissions,
                apiPermissions,
                null,
                1L,
                "manifest-1",
                "digest-a",
                issuedAt,
                expiresAt);
    }
}
