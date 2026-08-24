package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration.SimpleAkskResourceServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.converter.AkskIntrospectionAuthenticationConverter;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.model.IntrospectResult;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support.IntrospectLocalCacheHelper;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.web.client.RestClientException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AkskIntrospectionAuthenticationConverter 兜底降级单元测试
 *
 * <p>纯单元测试，不启动 Spring 上下文，mock delegate 和 eventPublisher，
 * 验证 introspect 端点不可用时的兜底降级行为。
 *
 * @author surezzzzzz
 */
@Slf4j
class IntrospectFallbackStrategyTest {

    private static final String CACHE_KEY = "entry-primary";
    private OpaqueTokenIntrospector delegate;

    @BeforeEach
    void setUp() {
        delegate = mock(OpaqueTokenIntrospector.class);
    }

    @Test
    @DisplayName("兜底启用：端点不可用且兜底缓存有 active=true 条目时，返回兜底结果")
    void testFallbackWhenEndpointUnavailableAndCacheHit() {
        log.info("========== 测试：兜底启用，端点不可用，兜底缓存命中 ==========");

        IntrospectLocalCacheHelper cacheHelper = buildCacheHelper(true, 1, 1000, true, 10, 1000);

        Map<String, Object> attrs = buildAttributes("test-service", "read write");
        cacheHelper.put(CACHE_KEY, new IntrospectResult(true, attrs));
        assertNotNull(cacheHelper.get(CACHE_KEY), "主缓存预热后应命中");
        assertNotNull(cacheHelper.getFallback(CACHE_KEY), "兜底缓存预热后应命中");
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待主缓存过期被中断", exception);
        }
        cacheHelper.cleanUp();
        assertNull(cacheHelper.get(CACHE_KEY), "主缓存必须先过期");
        assertNotNull(cacheHelper.getFallback(CACHE_KEY), "兜底缓存仍应保留");

        when(delegate.introspect(anyString())).thenThrow(new RestClientException("Connection refused"));

        AkskIntrospectionAuthenticationConverter converter =
                new AkskIntrospectionAuthenticationConverter(delegate, cacheHelper);

        // 主缓存已清理，走 delegate 失败，应从兜底缓存取
        // 注意：cleanUp 只清主缓存超出 maxSize 的条目，TTL 过期需等待
        // 这里直接验证兜底缓存有数据时的降级路径
        OAuth2AuthenticatedPrincipal principal = converter.introspect(CACHE_KEY);

        log.info("兜底结果: name={}", principal.getName());
        assertNotNull(principal, "兜底时应返回兜底缓存中的 principal");

        log.info("✓ 兜底缓存命中降级成功");
    }

    @Test
    @DisplayName("内省响应异常：存在 active 兜底条目时不得降级放行")
    void testIntrospectionExceptionDoesNotUseFallback() {
        IntrospectLocalCacheHelper cacheHelper = buildCacheHelper(true, 1, 1000, true, 10, 1000);
        cacheHelper.put(CACHE_KEY, new IntrospectResult(true, buildAttributes("test-service", "read")));
        expirePrimaryCache(cacheHelper, CACHE_KEY);

        OAuth2IntrospectionException exception = new OAuth2IntrospectionException("invalid introspection response");
        when(delegate.introspect(anyString())).thenThrow(exception);

        AkskIntrospectionAuthenticationConverter converter =
                new AkskIntrospectionAuthenticationConverter(delegate, cacheHelper);

        assertThrows(OAuth2IntrospectionException.class, () -> converter.introspect(CACHE_KEY),
                "内省语义异常不得使用兜底缓存");
        verify(delegate).introspect(CACHE_KEY);
    }

    @Test
    @DisplayName("普通运行时异常：存在 active 兜底条目时不得降级放行")
    void testRuntimeExceptionDoesNotUseFallback() {
        IntrospectLocalCacheHelper cacheHelper = buildCacheHelper(true, 1, 1000, true, 10, 1000);
        cacheHelper.put(CACHE_KEY, new IntrospectResult(true, buildAttributes("test-service", "read")));
        expirePrimaryCache(cacheHelper, CACHE_KEY);

        RuntimeException exception = new IllegalStateException("invalid token response");
        when(delegate.introspect(anyString())).thenThrow(exception);

        AkskIntrospectionAuthenticationConverter converter =
                new AkskIntrospectionAuthenticationConverter(delegate, cacheHelper);

        assertThrows(IllegalStateException.class, () -> converter.introspect(CACHE_KEY),
                "普通运行时异常不得使用兜底缓存");
        verify(delegate).introspect(CACHE_KEY);
    }

    @Test
    @DisplayName("兜底启用：端点不可用且兜底缓存无条目时，抛出异常")
    void testFallbackWhenEndpointUnavailableAndCacheMiss() {
        log.info("========== 测试：兜底启用，端点不可用，兜底缓存未命中 ==========");

        IntrospectLocalCacheHelper cacheHelper = buildCacheHelper(true, 3, 1000, true, 10, 1000);

        when(delegate.introspect(anyString())).thenThrow(new RestClientException("Connection refused"));

        AkskIntrospectionAuthenticationConverter converter =
                new AkskIntrospectionAuthenticationConverter(delegate, cacheHelper);

        assertThrows(RuntimeException.class, () -> converter.introspect("entry-missing"),
                "兜底缓存未命中时应抛出异常");

        log.info("✓ 兜底缓存未命中时正确抛出异常");
    }

    @Test
    @DisplayName("兜底启用：兜底缓存中 active=false 的条目不兜底，抛出异常")
    void testFallbackDoesNotAcceptRevokedToken() {
        log.info("========== 测试：兜底缓存中 active=false 不兜底 ==========");

        IntrospectLocalCacheHelper cacheHelper = buildCacheHelper(true, 1, 1000, true, 10, 1000);

        String revokedKey = "entry-inactive";
        cacheHelper.put(revokedKey, new IntrospectResult(false, buildAttributes("test-service", "read")));
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待主缓存过期被中断", exception);
        }
        cacheHelper.cleanUp();
        assertNull(cacheHelper.get(revokedKey), "主缓存必须先过期");
        assertNotNull(cacheHelper.getFallback(revokedKey), "兜底缓存应有失效条目");

        when(delegate.introspect(anyString())).thenThrow(new RestClientException("Connection refused"));
        AkskIntrospectionAuthenticationConverter converter =
                new AkskIntrospectionAuthenticationConverter(delegate, cacheHelper);

        assertThrows(OAuth2IntrospectionException.class, () -> converter.introspect(revokedKey),
                "失效条目不得通过兜底缓存放行");
        verify(delegate).introspect(revokedKey);

        log.info("失效兜底条目已被 converter 拒绝");
    }

    @Test
    @DisplayName("兜底禁用：端点不可用时直接抛出异常")
    void testNoFallbackWhenDisabled() {
        log.info("========== 测试：兜底禁用，端点不可用 ==========");

        IntrospectLocalCacheHelper cacheHelper = buildCacheHelper(true, 3, 1000, false, 10, 1000);

        when(delegate.introspect(anyString())).thenThrow(new RestClientException("Connection refused"));

        AkskIntrospectionAuthenticationConverter converter =
                new AkskIntrospectionAuthenticationConverter(delegate, cacheHelper);

        assertThrows(RuntimeException.class, () -> converter.introspect(CACHE_KEY),
                "兜底禁用时端点不可用应直接抛出异常");

        log.info("✓ 兜底禁用时正确拒绝");
    }

    @Test
    @DisplayName("正常路径：端点可用时写入主缓存和兜底缓存")
    void testNormalPathWritesBothCaches() {
        log.info("========== 测试：正常路径写主缓存和兜底缓存 ==========");

        IntrospectLocalCacheHelper cacheHelper = buildCacheHelper(true, 3, 1000, true, 10, 1000);

        Map<String, Object> attrs = buildAttributes("AKP456", "read");
        OAuth2AuthenticatedPrincipal mockPrincipal = mock(OAuth2AuthenticatedPrincipal.class);
        when(mockPrincipal.getAttributes()).thenReturn(attrs);
        when(mockPrincipal.getName()).thenReturn("AKP456");
        when(delegate.introspect(anyString())).thenReturn(mockPrincipal);

        AkskIntrospectionAuthenticationConverter converter =
                new AkskIntrospectionAuthenticationConverter(delegate, cacheHelper);

        converter.introspect(CACHE_KEY);

        assertNotNull(cacheHelper.get(CACHE_KEY), "正常路径应写入主缓存");
        assertNotNull(cacheHelper.getFallback(CACHE_KEY), "正常路径应写入兜底缓存");
        assertTrue(cacheHelper.get(CACHE_KEY).isActive(), "主缓存 active 应为 true");
        assertTrue(cacheHelper.getFallback(CACHE_KEY).isActive(), "兜底缓存 active 应为 true");

        log.info("✓ 正常路径主缓存和兜底缓存均写入成功");
    }

    @Test
    @DisplayName("inactive令牌无主体字段时仍返回拒绝主体")
    void testInactiveTokenWithoutSubjectReturnsRejectedPrincipal() {
        IntrospectLocalCacheHelper cacheHelper = buildCacheHelper(true, 3, 1000, false, 10, 1000);
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("active", false);
        OAuth2AuthenticatedPrincipal principal = mock(OAuth2AuthenticatedPrincipal.class);
        when(principal.getAttributes()).thenReturn(attributes);
        when(delegate.introspect(anyString())).thenReturn(principal);
        AkskIntrospectionAuthenticationConverter converter =
                new AkskIntrospectionAuthenticationConverter(delegate, cacheHelper);

        OAuth2AuthenticatedPrincipal rejected = converter.introspect(CACHE_KEY);

        assertEquals("inactive", rejected.getName());
        assertEquals(Boolean.FALSE, rejected.getAttributes().get("active"));
    }

    @Test
    @DisplayName("inactive令牌写入缓存以传播撤销状态")
    void testInactiveTokenIsCached() {
        log.info("========== 测试：inactive令牌缓存撤销状态 ==========");

        IntrospectLocalCacheHelper cacheHelper = buildCacheHelper(true, 3, 1000, true, 10, 1000);
        Map<String, Object> attributes = new HashMap<String, Object>();
        attributes.put("active", false);
        OAuth2AuthenticatedPrincipal principal = mock(OAuth2AuthenticatedPrincipal.class);
        when(principal.getAttributes()).thenReturn(attributes);
        when(delegate.introspect(anyString())).thenReturn(principal);
        AkskIntrospectionAuthenticationConverter converter =
                new AkskIntrospectionAuthenticationConverter(delegate, cacheHelper);

        converter.introspect(CACHE_KEY);
        assertNotNull(cacheHelper.get(CACHE_KEY), "inactive令牌应写入主缓存传播撤销状态");
        assertFalse(cacheHelper.get(CACHE_KEY).isActive(), "主缓存必须保留inactive状态");
        assertNotNull(cacheHelper.getFallback(CACHE_KEY), "inactive令牌应写入兜底缓存");
        assertFalse(cacheHelper.getFallback(CACHE_KEY).isActive(), "兜底缓存不得把inactive改为active");

        log.info("✓ inactive令牌已缓存且保持拒绝状态");
    }

    // ==================== 工具方法 ====================

    private IntrospectLocalCacheHelper buildCacheHelper(
            boolean cacheEnabled, int expireSeconds, int maxSize,
            boolean fallbackEnabled, int staleTtlMultiplier, int staleMaxSize) {

        SimpleAkskResourceServerProperties props = new SimpleAkskResourceServerProperties();
        SimpleAkskResourceServerProperties.Introspect introspect =
                new SimpleAkskResourceServerProperties.Introspect();
        SimpleAkskResourceServerProperties.Introspect.LocalCacheConfig cacheConfig =
                new SimpleAkskResourceServerProperties.Introspect.LocalCacheConfig();
        cacheConfig.setEnabled(cacheEnabled);
        cacheConfig.setExpireSeconds(expireSeconds);
        cacheConfig.setMaxSize(maxSize);

        SimpleAkskResourceServerProperties.Introspect.LocalCacheConfig.FallbackConfig fallbackConfig =
                new SimpleAkskResourceServerProperties.Introspect.LocalCacheConfig.FallbackConfig();
        fallbackConfig.setEnabled(fallbackEnabled);
        fallbackConfig.setStaleTtlMultiplier(staleTtlMultiplier);
        fallbackConfig.setStaleMaxSize(staleMaxSize);
        cacheConfig.setFallback(fallbackConfig);

        introspect.setLocalCache(cacheConfig);
        props.setIntrospect(introspect);

        IntrospectLocalCacheHelper helper = new IntrospectLocalCacheHelper(props);
        helper.init();
        return helper;
    }

    private void expirePrimaryCache(IntrospectLocalCacheHelper cacheHelper, String token) {
        try {
            TimeUnit.SECONDS.sleep(2);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("等待主缓存过期被中断", exception);
        }
        cacheHelper.cleanUp();
        assertNull(cacheHelper.get(token), "主缓存必须先过期");
        assertNotNull(cacheHelper.getFallback(token), "兜底缓存仍应保留");
    }

    private Map<String, Object> buildAttributes(String clientId, String scope) {
        Map<String, Object> attrs = new HashMap<>();
        attrs.put("sub", clientId);
        attrs.put("client_id", clientId);
        attrs.put("scope", scope);
        attrs.put("active", true);
        return attrs;
    }
}
