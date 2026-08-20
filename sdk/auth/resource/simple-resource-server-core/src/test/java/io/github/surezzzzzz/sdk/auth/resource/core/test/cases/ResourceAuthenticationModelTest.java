package io.github.surezzzzzz.sdk.auth.resource.core.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationFailureCategory;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationOutcome;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.exception.ResourceAuthenticationException;
import io.github.surezzzzzz.sdk.auth.resource.core.model.BearerResourceCredential;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourceContext;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import io.github.surezzzzzz.sdk.auth.resource.core.support.ResourceAuthenticationContextHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 资源认证模型测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class ResourceAuthenticationModelTest {

    @Test
    void shouldCreateVerifiedContextOnlyForBoundPrincipalAndAuthorization() {
        VerifiedResourcePrincipal principal = new VerifiedResourcePrincipal(new ResourceAuthenticationSourceId("provider-a"),
                ResourceSubjectType.HUMAN, "subject-a");
        ResourceAuthenticationResult result = ResourceAuthenticationResult.authenticated(principal, authorization("subject-a"));

        VerifiedResourceContext context = ResourceAuthenticationContextHelper.createVerifiedContext(result, "request-a");

        log.info("已验证资源上下文创建成功，认证来源：{}，认证状态：{}", principal.getSourceId().getValue(),
                result.getOutcome());
        assertEquals(principal, context.getPrincipal(), "上下文必须保留已验证主体");
        assertEquals("application-a", context.getApplicationAuthorization().getApplicationCode(),
                "上下文必须保留认证通过的应用授权快照");
        assertThrows(ResourceAuthenticationException.class,
                () -> ResourceAuthenticationContextHelper.createVerifiedContext(
                        ResourceAuthenticationResult.authenticated(principal, authorization("subject-b")), "request-a"),
                "主体标识与授权快照不匹配必须拒绝");
        assertThrows(ResourceAuthenticationException.class,
                () -> ResourceAuthenticationContextHelper.createVerifiedContext(
                        ResourceAuthenticationResult.authenticated(principal, authorizationForService("subject-a")), "request-a"),
                "主体类型与授权快照不匹配必须拒绝");
    }

    @Test
    void shouldKeepAuthenticationResultOutcomesMutuallyExclusive() {
        VerifiedResourcePrincipal principal = new VerifiedResourcePrincipal(new ResourceAuthenticationSourceId("provider-a"),
                ResourceSubjectType.SERVICE, "service-a");
        ResourceAuthenticationResult authenticated = ResourceAuthenticationResult.authenticated(principal,
                authorizationForService("service-a"));
        ResourceAuthenticationResult rejected = ResourceAuthenticationResult.rejected(
                ResourceAuthenticationFailureCategory.TOKEN_INACTIVE);
        ResourceAuthenticationResult notApplicable = ResourceAuthenticationResult.notApplicable();

        log.info("认证结果状态：{}、{}、{}", authenticated.getOutcome(), rejected.getOutcome(), notApplicable.getOutcome());
        assertEquals(ResourceAuthenticationOutcome.AUTHENTICATED, authenticated.getOutcome(), "成功结果必须标记为已认证");
        assertNull(authenticated.getFailureCategory(), "成功结果不得携带失败分类");
        assertEquals(ResourceAuthenticationOutcome.REJECTED, rejected.getOutcome(), "拒绝结果必须标记为拒绝");
        assertNull(rejected.getPrincipal(), "拒绝结果不得遗留主体");
        assertEquals(ResourceAuthenticationOutcome.NOT_APPLICABLE, notApplicable.getOutcome(), "不适用结果必须明确标记");
        assertNull(notApplicable.getApplicationAuthorization(), "不适用结果不得携带授权快照");
        assertThrows(ResourceAuthenticationException.class,
                () -> ResourceAuthenticationResult.rejected(null), "空失败分类不得创建拒绝结果");
        assertThrows(ResourceAuthenticationException.class,
                () -> ResourceAuthenticationContextHelper.createVerifiedContext(rejected, "request-a"),
                "拒绝结果不得创建已验证上下文");
        assertThrows(ResourceAuthenticationException.class,
                () -> ResourceAuthenticationContextHelper.createVerifiedContext(notApplicable, "request-a"),
                "不适用结果不得创建已验证上下文");
    }

    @Test
    void shouldKeepBearerCredentialReadableAndRedacted() {
        ResourceAuthenticationSourceId sourceId = new ResourceAuthenticationSourceId("provider-a");
        String credentialText = "credential-value";
        BearerResourceCredential credential = new BearerResourceCredential(sourceId, credentialText);

        assertEquals(sourceId, credential.getSourceId(), "Provider必须取得已路由来源");
        assertEquals(credentialText, credential.getToken(), "Provider必须取得待验证凭据");
        assertEquals("BearerResourceCredential[REDACTED]", credential.toString(),
                "凭据日志必须保持固定脱敏文本");
        assertFalse(credential.toString().contains(sourceId.getValue()), "凭据日志不得暴露来源");
        assertFalse(credential.toString().contains(credentialText), "凭据日志不得暴露凭据文本");
    }

    @Test
    void shouldRejectUnsafeSourceIdentifiers() {
        assertThrows(ResourceAuthenticationException.class, () -> new ResourceAuthenticationSourceId(" provider-a"),
                "含首尾空白的来源标识必须拒绝");
        assertThrows(ResourceAuthenticationException.class, () -> new ResourceAuthenticationSourceId("provider/*"),
                "含路由模式字符的来源标识必须拒绝");
        assertThrows(ResourceAuthenticationException.class, () -> new ResourceAuthenticationSourceId("${provider-a}"),
                "动态表达式来源标识必须拒绝");
        assertEquals("provider-a", new ResourceAuthenticationSourceId("provider-a").getValue(),
                "合法来源标识必须保持原值");
    }

    private ApplicationAuthorizationContext authorization(String subjectId) {
        return new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.HUMAN,
                subjectId,
                "application-a",
                true,
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                Collections.singletonList("api.read"),
                null,
                1L,
                "manifest-1",
                "digest-a",
                Instant.ofEpochSecond(100L),
                Instant.ofEpochSecond(200L));
    }

    private ApplicationAuthorizationContext authorizationForService(String subjectId) {
        return new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL,
                SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.SERVICE,
                subjectId,
                "application-a",
                true,
                Collections.<String>emptyList(),
                Collections.<String>emptyList(),
                Collections.singletonList("api.read"),
                null,
                1L,
                "manifest-1",
                "digest-a",
                Instant.ofEpochSecond(100L),
                Instant.ofEpochSecond(200L));
    }
}
