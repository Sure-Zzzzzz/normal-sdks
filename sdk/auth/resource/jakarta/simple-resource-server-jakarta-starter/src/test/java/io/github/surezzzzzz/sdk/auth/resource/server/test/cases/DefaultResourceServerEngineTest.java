package io.github.surezzzzzz.sdk.auth.resource.server.test.cases;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.ApplicationAuthorizationSubjectType;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.constant.SimpleApplicationAuthorizationConstant;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationFailureCategory;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceAuthenticationOutcome;
import io.github.surezzzzzz.sdk.auth.resource.core.constant.ResourceSubjectType;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceCredential;
import io.github.surezzzzzz.sdk.auth.resource.core.model.VerifiedResourcePrincipal;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;
import io.github.surezzzzzz.sdk.auth.resource.server.exception.ResourceServerConfigurationException;
import io.github.surezzzzzz.sdk.auth.resource.server.support.BearerCredentialResolver;
import io.github.surezzzzzz.sdk.auth.resource.server.support.DefaultResourceAuthenticationAdapterRegistry;
import io.github.surezzzzzz.sdk.auth.resource.server.support.DefaultResourceServerEngine;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 默认资源认证编排引擎测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class DefaultResourceServerEngineTest {

    private static final ResourceAuthenticationSourceId IAM_SOURCE = new ResourceAuthenticationSourceId("iam");
    private static final ResourceAuthenticationSourceId AKSK_SOURCE = new ResourceAuthenticationSourceId("aksk");

    /**
     * 验证已选择Adapter的拒绝分类不会被公共层覆盖。
     */
    @Test
    void shouldPreserveSelectedAdapterRejectedCategoryWithoutFallback() {
        AtomicInteger iamCalls = new AtomicInteger();
        AtomicInteger akskCalls = new AtomicInteger();
        ResourceAuthenticationAdapter iam = adapter(IAM_SOURCE, iamCalls,
                ResourceAuthenticationResult.rejected(ResourceAuthenticationFailureCategory.TOKEN_EXPIRED));
        ResourceAuthenticationAdapter aksk = adapter(AKSK_SOURCE, akskCalls, authenticated(AKSK_SOURCE));
        DefaultResourceServerEngine engine = new DefaultResourceServerEngine(new BearerCredentialResolver(),
                new DefaultResourceAuthenticationAdapterRegistry(Arrays.asList(iam, aksk)));

        ResourceAuthenticationResult result = engine.authenticate(requestFor("iam"));

        log.info("已选择IAM Adapter后的结果: {}", result.getFailureCategory());
        assertEquals(ResourceAuthenticationOutcome.REJECTED, result.getOutcome(), "过期Token必须拒绝");
        assertEquals(ResourceAuthenticationFailureCategory.TOKEN_EXPIRED, result.getFailureCategory(),
                "Provider拒绝分类必须原样保留");
        assertEquals(Integer.valueOf(1), Integer.valueOf(iamCalls.get()), "仅能调用已选择IAM Adapter一次");
        assertEquals(Integer.valueOf(0), Integer.valueOf(akskCalls.get()), "不得回退调用AKSK Adapter");
    }

    /**
     * 验证唯一来源Adapter返回不适用时按认证契约拒绝。
     */
    @Test
    void shouldFailClosedWhenSelectedAdapterReturnsNotApplicable() {
        AtomicInteger calls = new AtomicInteger();
        ResourceAuthenticationAdapter iam = adapter(IAM_SOURCE, calls, ResourceAuthenticationResult.notApplicable());
        DefaultResourceServerEngine engine = new DefaultResourceServerEngine(new BearerCredentialResolver(),
                new DefaultResourceAuthenticationAdapterRegistry(Collections.singletonList(iam)));

        ResourceAuthenticationResult result = engine.authenticate(requestFor("iam"));

        log.info("已选择Adapter返回不适用时结果: {}", result.getFailureCategory());
        assertEquals(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID, result.getFailureCategory(),
                "唯一来源返回不适用必须fail-closed");
        assertEquals(Integer.valueOf(1), Integer.valueOf(calls.get()), "已选择Adapter只能调用一次");
    }

    /**
     * 验证未知来源不会调用任何认证适配器。
     */
    @Test
    void shouldRejectUnknownSourceWithoutCallingAdapter() {
        AtomicInteger iamCalls = new AtomicInteger();
        ResourceAuthenticationAdapter iam = adapter(IAM_SOURCE, iamCalls, authenticated(IAM_SOURCE));
        DefaultResourceServerEngine engine = new DefaultResourceServerEngine(new BearerCredentialResolver(),
                new DefaultResourceAuthenticationAdapterRegistry(Collections.singletonList(iam)));

        ResourceAuthenticationResult result = engine.authenticate(requestFor("unknown"));

        assertEquals(ResourceAuthenticationOutcome.REJECTED, result.getOutcome(), "未知来源必须拒绝");
        assertEquals(ResourceAuthenticationFailureCategory.SOURCE_UNRECOGNIZED, result.getFailureCategory(),
                "未知来源必须保留来源拒绝分类");
        assertEquals(Integer.valueOf(0), Integer.valueOf(iamCalls.get()), "未知来源不得调用已注册Adapter");
    }

    /**
     * 验证Adapter返回来源不一致的认证结果时失败关闭。
     */
    @Test
    void shouldFailClosedWhenAuthenticatedPrincipalSourceMismatchesSelectedAdapter() {
        AtomicInteger calls = new AtomicInteger();
        ResourceAuthenticationAdapter iam = adapter(IAM_SOURCE, calls, authenticated(AKSK_SOURCE));
        DefaultResourceServerEngine engine = new DefaultResourceServerEngine(new BearerCredentialResolver(),
                new DefaultResourceAuthenticationAdapterRegistry(Collections.singletonList(iam)));

        ResourceAuthenticationResult result = engine.authenticate(requestFor("iam"));

        assertEquals(ResourceAuthenticationOutcome.REJECTED, result.getOutcome(), "来源不一致必须拒绝");
        assertEquals(ResourceAuthenticationFailureCategory.AUTHORIZATION_INVALID, result.getFailureCategory(),
                "认证结果来源不一致必须按授权无效拒绝");
        assertEquals(Integer.valueOf(1), Integer.valueOf(calls.get()), "唯一Adapter只能调用一次");
    }

    /**
     * 验证Provider运行时不可用不会遗留异常或改走其他来源。
     */
    @Test
    void shouldRejectProviderRuntimeFailureWithoutFallback() {
        AtomicInteger iamCalls = new AtomicInteger();
        AtomicInteger akskCalls = new AtomicInteger();
        ResourceAuthenticationAdapter iam = new ResourceAuthenticationAdapter() {
            @Override
            public ResourceAuthenticationSourceId sourceId() {
                return IAM_SOURCE;
            }

            @Override
            public ResourceAuthenticationResult authenticate(ResourceCredential credential) {
                iamCalls.incrementAndGet();
                throw new IllegalStateException("provider unavailable");
            }
        };
        ResourceAuthenticationAdapter aksk = adapter(AKSK_SOURCE, akskCalls, authenticated(AKSK_SOURCE));
        DefaultResourceServerEngine engine = new DefaultResourceServerEngine(new BearerCredentialResolver(),
                new DefaultResourceAuthenticationAdapterRegistry(Arrays.asList(iam, aksk)));

        ResourceAuthenticationResult result = engine.authenticate(requestFor("iam"));

        log.info("Provider运行失败后结果: {}", result.getFailureCategory());
        assertEquals(ResourceAuthenticationFailureCategory.PROVIDER_UNAVAILABLE, result.getFailureCategory(),
                "Provider运行失败必须安全拒绝");
        assertEquals(Integer.valueOf(1), Integer.valueOf(iamCalls.get()), "已选择Adapter只能调用一次");
        assertEquals(Integer.valueOf(0), Integer.valueOf(akskCalls.get()), "Provider失败后不得回退");
    }

    /**
     * 验证无效认证适配器配置使用模块配置异常。
     */
    @Test
    void shouldRejectInvalidAdapterRegistryConfigurationWithModuleException() {
        ResourceAuthenticationAdapter adapter = adapter(IAM_SOURCE, new AtomicInteger(), authenticated(IAM_SOURCE));

        ResourceServerConfigurationException nullAdapters = assertThrows(ResourceServerConfigurationException.class,
                () -> new DefaultResourceAuthenticationAdapterRegistry(null), "空适配器集合必须抛模块配置异常");
        ResourceServerConfigurationException duplicateSource = assertThrows(ResourceServerConfigurationException.class,
                () -> new DefaultResourceAuthenticationAdapterRegistry(Arrays.asList(adapter, adapter)),
                "重复认证来源必须抛模块配置异常");

        assertEquals(SimpleResourceServerStarterConstant.ERROR_CODE_CONFIGURATION, nullAdapters.getErrorCode(),
                "配置异常必须携带统一错误码");
        assertEquals(SimpleResourceServerStarterConstant.ERROR_CODE_CONFIGURATION, duplicateSource.getErrorCode(),
                "重复来源异常必须携带统一错误码");
    }

    private ResourceAuthenticationAdapter adapter(final ResourceAuthenticationSourceId sourceId,
                                                  final AtomicInteger calls,
                                                  final ResourceAuthenticationResult result) {
        return new ResourceAuthenticationAdapter() {
            @Override
            public ResourceAuthenticationSourceId sourceId() {
                return sourceId;
            }

            @Override
            public ResourceAuthenticationResult authenticate(ResourceCredential credential) {
                calls.incrementAndGet();
                return result;
            }
        };
    }

    private ResourceAuthenticationResult authenticated(ResourceAuthenticationSourceId sourceId) {
        Instant now = Instant.now();
        ApplicationAuthorizationContext authorization = new ApplicationAuthorizationContext(
                SimpleApplicationAuthorizationConstant.PROTOCOL, SimpleApplicationAuthorizationConstant.VERSION,
                ApplicationAuthorizationSubjectType.HUMAN, "subject-a", "app-a", true,
                Collections.<String>emptyList(), Collections.<String>emptyList(), Collections.singletonList("read"),
                null, 1L, "manifest-a", "digest-a", now.minusSeconds(1L), now.plusSeconds(60L));
        return ResourceAuthenticationResult.authenticated(new VerifiedResourcePrincipal(sourceId,
                ResourceSubjectType.HUMAN, "subject-a"), authorization);
    }

    private MockHttpServletRequest requestFor(String sourceId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        String header = "{\"alg\":\"dir\",\"kid\":\"" + sourceId + "/key-a\"}";
        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", "Bearer "
                + encodedHeader + ".encrypted-key.iv.cipher-text.authentication-tag");
        return request;
    }
}
