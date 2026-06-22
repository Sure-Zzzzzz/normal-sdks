package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.filter.AkskServerOAuth2LimiterFilter;
import io.github.surezzzzzz.sdk.auth.aksk.server.provider.AkskOAuth2ClientIdKeyProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterAlgorithm;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterAlgorithmFactory;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterContext;
import io.github.surezzzzzz.sdk.limiter.redis.smart.algorithm.SmartRedisLimiterResult;
import io.github.surezzzzzz.sdk.limiter.redis.smart.configuration.SmartRedisLimiterProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterContextAttribute;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

import javax.servlet.ServletException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AKSK OAuth2 限流 Filter 测试
 *
 * @author surezzzzzz
 */
@Slf4j
@ExtendWith(MockitoExtension.class)
@SuppressWarnings("unchecked")
class AkskServerOAuth2LimiterFilterTest {

    @Mock
    private SmartRedisLimiterAlgorithmFactory algorithmFactory;

    @Mock
    private SmartRedisLimiterAlgorithm algorithm;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Mock
    private ApplicationContext applicationContext;

    private SimpleAkskServerProperties properties;
    private SmartRedisLimiterProperties smartLimiterProperties;
    private AuthorizationServerSettings authorizationServerSettings;
    private AkskOAuth2ClientIdKeyProvider clientIdKeyProvider;
    private AkskServerOAuth2LimiterFilter filter;

    @BeforeEach
    void setUp() {
        properties = new SimpleAkskServerProperties();
        smartLimiterProperties = new SmartRedisLimiterProperties();
        smartLimiterProperties.setMe("test-app");
        authorizationServerSettings = AuthorizationServerSettings.builder().build();
        clientIdKeyProvider = new AkskOAuth2ClientIdKeyProvider();
        filter = new AkskServerOAuth2LimiterFilter(
                properties,
                smartLimiterProperties,
                algorithmFactory,
                authorizationServerSettings,
                applicationEventPublisher,
                applicationContext,
                clientIdKeyProvider);
    }

    @Test
    void testNonOAuth2EndpointSkipsLimiter() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/test");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        log.info("非 OAuth2 端点响应状态: {}", response.getStatus());
        assertNotNull(chain.getRequest(), "非 OAuth2 端点应继续执行过滤器链");
        verifyNoInteractions(algorithmFactory, algorithm);
    }

    @Test
    void testTokenEndpointConvertsCoreConfigToSmartLimiterRule() throws ServletException, IOException {
        when(algorithmFactory.getAlgorithm(SimpleAkskServerConstant.DEFAULT_LIMITER_ALGORITHM)).thenReturn(algorithm);
        when(algorithm.tryAcquireWithResult(any(), anyList(), eq(SimpleAkskServerConstant.DEFAULT_LIMITER_KEY_STRATEGY),
                eq(SimpleAkskServerConstant.DEFAULT_LIMITER_TOKEN_FALLBACK)))
                .thenReturn(SmartRedisLimiterResult.builder()
                        .passed(true)
                        .limit(SimpleAkskServerConstant.DEFAULT_LIMITER_TOKEN_COUNT)
                        .remaining(SimpleAkskServerConstant.DEFAULT_LIMITER_TOKEN_COUNT - 1)
                        .resetAt(1L)
                        .build());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", authorizationServerSettings.getTokenEndpoint());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        ArgumentCaptor<List> rulesCaptor = ArgumentCaptor.forClass(List.class);
        verify(algorithm).tryAcquireWithResult(any(), rulesCaptor.capture(),
                eq(SimpleAkskServerConstant.DEFAULT_LIMITER_KEY_STRATEGY),
                eq(SimpleAkskServerConstant.DEFAULT_LIMITER_TOKEN_FALLBACK));
        List<SmartRedisLimiterProperties.SmartLimitRule> rules = rulesCaptor.getValue();
        SmartRedisLimiterProperties.SmartLimitRule rule = rules.get(0);

        log.info("Token 限流规则转换结果: count={}, window={}, unit={}",
                rule.getCount(), rule.getWindow(), rule.getUnit());
        assertEquals(SimpleAkskServerConstant.DEFAULT_LIMITER_TOKEN_COUNT, rule.getCount(),
                "Token count 应转换为 smart-limiter 规则");
        assertEquals(SimpleAkskServerConstant.DEFAULT_LIMITER_WINDOW, rule.getWindow(),
                "Token window 应转换为 smart-limiter 规则");
        assertEquals(SimpleAkskServerConstant.DEFAULT_LIMITER_WINDOW_UNIT, rule.getUnit(),
                "Token unit 应转换为 smart-limiter 规则");
        assertEquals(String.valueOf(SimpleAkskServerConstant.DEFAULT_LIMITER_TOKEN_COUNT),
                response.getHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_LIMIT),
                "通过时应写入限流响应头");
        assertNotNull(chain.getRequest(), "限流通过后应继续执行过滤器链");
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    void testTokenEndpointUsesClientIdProviderWhenBasicAuthPresent() throws ServletException, IOException {
        when(algorithmFactory.getAlgorithm(SimpleAkskServerConstant.DEFAULT_LIMITER_ALGORITHM)).thenReturn(algorithm);
        when(algorithm.tryAcquireWithResult(any(), anyList(), anyString(), anyString()))
                .thenReturn(SmartRedisLimiterResult.builder()
                        .passed(true)
                        .limit(1L)
                        .remaining(0L)
                        .resetAt(1L)
                        .build());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", authorizationServerSettings.getTokenEndpoint());
        request.addHeader("Authorization", basicAuth("AKP-client-001", "SK-secret-001"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<SmartRedisLimiterContext> contextCaptor = ArgumentCaptor.forClass(SmartRedisLimiterContext.class);
        verify(algorithm).tryAcquireWithResult(contextCaptor.capture(), anyList(),
                eq(SmartRedisLimiterConstant.EVENT_KEY_STRATEGY_CUSTOM_PREFIX
                        + AkskOAuth2ClientIdKeyProvider.BEAN_NAME),
                eq(SimpleAkskServerConstant.DEFAULT_LIMITER_TOKEN_FALLBACK));
        SmartRedisLimiterContext context = contextCaptor.getValue();
        String keyPart = context.getAttribute(SmartRedisLimiterContextAttribute.PRECOMPUTED_KEY_PART);

        log.info("OAuth2 clientId provider key: {}", keyPart);
        assertEquals("client:AKP-client-001", keyPart, "Basic Auth clientId 应由 provider 预计算为限流 key");
    }

    @Test
    void testTokenEndpointFallsBackToConfiguredKeyStrategyWhenClientIdMissing() throws ServletException, IOException {
        when(algorithmFactory.getAlgorithm(SimpleAkskServerConstant.DEFAULT_LIMITER_ALGORITHM)).thenReturn(algorithm);
        when(algorithm.tryAcquireWithResult(any(), anyList(), anyString(), anyString()))
                .thenReturn(SmartRedisLimiterResult.builder()
                        .passed(true)
                        .limit(1L)
                        .remaining(0L)
                        .resetAt(1L)
                        .build());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", authorizationServerSettings.getTokenEndpoint());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<SmartRedisLimiterContext> contextCaptor = ArgumentCaptor.forClass(SmartRedisLimiterContext.class);
        verify(algorithm).tryAcquireWithResult(contextCaptor.capture(), anyList(),
                eq(SimpleAkskServerConstant.DEFAULT_LIMITER_KEY_STRATEGY),
                eq(SimpleAkskServerConstant.DEFAULT_LIMITER_TOKEN_FALLBACK));
        SmartRedisLimiterContext context = contextCaptor.getValue();

        log.info("OAuth2 token 端点未携带 clientId 时回退 keyStrategy={}",
                SimpleAkskServerConstant.DEFAULT_LIMITER_KEY_STRATEGY);
        assertNull(context.getAttribute(SmartRedisLimiterContextAttribute.PRECOMPUTED_KEY_PART),
                "未携带 Basic Auth/client_id 时不应生成 clientId 预计算 key");
    }

    @Test
    void testIntrospectEndpointUsesOwnFallback() throws ServletException, IOException {
        when(algorithmFactory.getAlgorithm(SimpleAkskServerConstant.DEFAULT_LIMITER_ALGORITHM)).thenReturn(algorithm);
        when(algorithm.tryAcquireWithResult(any(), anyList(), eq(SimpleAkskServerConstant.DEFAULT_LIMITER_KEY_STRATEGY),
                eq(SimpleAkskServerConstant.DEFAULT_LIMITER_INTROSPECT_FALLBACK)))
                .thenReturn(SmartRedisLimiterResult.builder()
                        .passed(true)
                        .limit(SimpleAkskServerConstant.DEFAULT_LIMITER_INTROSPECT_COUNT)
                        .remaining(1L)
                        .resetAt(1L)
                        .build());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", authorizationServerSettings.getTokenIntrospectionEndpoint());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        log.info("Introspect 限流 fallback: {}", SimpleAkskServerConstant.DEFAULT_LIMITER_INTROSPECT_FALLBACK);
        verify(algorithm).tryAcquireWithResult(any(), anyList(),
                eq(SimpleAkskServerConstant.DEFAULT_LIMITER_KEY_STRATEGY),
                eq(SimpleAkskServerConstant.DEFAULT_LIMITER_INTROSPECT_FALLBACK));
    }

    @Test
    void testRevokeEndpointUsesOwnFallback() throws ServletException, IOException {
        when(algorithmFactory.getAlgorithm(SimpleAkskServerConstant.DEFAULT_LIMITER_ALGORITHM)).thenReturn(algorithm);
        when(algorithm.tryAcquireWithResult(any(), anyList(), eq(SimpleAkskServerConstant.DEFAULT_LIMITER_KEY_STRATEGY),
                eq(SimpleAkskServerConstant.DEFAULT_LIMITER_REVOKE_FALLBACK)))
                .thenReturn(SmartRedisLimiterResult.builder()
                        .passed(true)
                        .limit(SimpleAkskServerConstant.DEFAULT_LIMITER_REVOKE_COUNT)
                        .remaining(1L)
                        .resetAt(1L)
                        .build());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", authorizationServerSettings.getTokenRevocationEndpoint());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        log.info("Revoke 限流 fallback: {}", SimpleAkskServerConstant.DEFAULT_LIMITER_REVOKE_FALLBACK);
        verify(algorithm).tryAcquireWithResult(any(), anyList(),
                eq(SimpleAkskServerConstant.DEFAULT_LIMITER_KEY_STRATEGY),
                eq(SimpleAkskServerConstant.DEFAULT_LIMITER_REVOKE_FALLBACK));
    }

    @Test
    void testLimitExceededWrites429AndPublishesEvent() throws ServletException, IOException {
        when(algorithmFactory.getAlgorithm(SimpleAkskServerConstant.DEFAULT_LIMITER_ALGORITHM)).thenReturn(algorithm);
        when(algorithm.tryAcquireWithResult(any(), anyList(), anyString(), anyString()))
                .thenReturn(SmartRedisLimiterResult.builder()
                        .passed(false)
                        .limit(1L)
                        .remaining(0L)
                        .resetAt(123L)
                        .build());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", authorizationServerSettings.getTokenEndpoint());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        ArgumentCaptor<SmartRedisLimiterEvent> eventCaptor = ArgumentCaptor.forClass(SmartRedisLimiterEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());
        SmartRedisLimiterEvent event = eventCaptor.getValue();

        log.info("限流响应: status={}, body={}", response.getStatus(), response.getContentAsString());
        log.info("限流事件: source={}, passed={}, requestUri={}",
                event.getSource(), event.isPassed(), event.getRequestUri());
        assertEquals(SmartRedisLimiterConstant.HTTP_STATUS_TOO_MANY_REQUESTS, response.getStatus(),
                "超限时应返回 429");
        assertEquals(String.valueOf(TimeUnit.MINUTES.toSeconds(1)),
                response.getHeader(SmartRedisLimiterConstant.HEADER_RETRY_AFTER),
                "超限时应写入 Retry-After");
        assertTrue(response.getContentAsString().contains(SmartRedisLimiterConstant.HTTP_MESSAGE_TOO_MANY_REQUESTS),
                "响应体应包含限流消息");
        assertEquals(SimpleAkskServerConstant.LIMITER_SOURCE_OAUTH2_FILTER, event.getSource(),
                "事件来源应标识为 OAuth2 Filter");
        assertFalse(event.isPassed(), "超限事件 passed 应为 false");
        assertNull(chain.getRequest(), "超限时不应继续执行过滤器链");
    }

    @Test
    void testLogOnPassPublishesPassedEvent() throws ServletException, IOException {
        smartLimiterProperties.setLogOnPass(true);
        when(algorithmFactory.getAlgorithm(SimpleAkskServerConstant.DEFAULT_LIMITER_ALGORITHM)).thenReturn(algorithm);
        when(algorithm.tryAcquireWithResult(any(), anyList(), anyString(), anyString()))
                .thenReturn(SmartRedisLimiterResult.builder()
                        .passed(true)
                        .limit(1L)
                        .remaining(1L)
                        .resetAt(123L)
                        .build());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", authorizationServerSettings.getTokenEndpoint());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<SmartRedisLimiterEvent> eventCaptor = ArgumentCaptor.forClass(SmartRedisLimiterEvent.class);
        verify(applicationEventPublisher).publishEvent(eventCaptor.capture());

        log.info("logOnPass=true 通过事件: passed={}", eventCaptor.getValue().isPassed());
        assertTrue(eventCaptor.getValue().isPassed(), "logOnPass=true 时通过请求应发布事件");
    }

    @Test
    void testEmptyLimitRulesSkipsLimiter() throws ServletException, IOException {
        properties.getLimiter().getOauth2().getToken().getLimits().clear();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", authorizationServerSettings.getTokenEndpoint());
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        log.info("空规则响应状态: {}", response.getStatus());
        assertNotNull(chain.getRequest(), "空规则时应继续执行过滤器链");
        verifyNoInteractions(algorithmFactory, algorithm);
    }

    @Test
    void testCustomAuthorizationServerEndpointPathMatches() throws ServletException, IOException {
        AuthorizationServerSettings customSettings = AuthorizationServerSettings.builder()
                .tokenEndpoint("/custom/token")
                .build();
        filter = new AkskServerOAuth2LimiterFilter(
                properties,
                smartLimiterProperties,
                algorithmFactory,
                customSettings,
                applicationEventPublisher,
                applicationContext,
                clientIdKeyProvider);
        when(algorithmFactory.getAlgorithm(SimpleAkskServerConstant.DEFAULT_LIMITER_ALGORITHM)).thenReturn(algorithm);
        when(algorithm.tryAcquireWithResult(any(), anyList(), anyString(), anyString()))
                .thenReturn(SmartRedisLimiterResult.builder()
                        .passed(true)
                        .limit(1L)
                        .remaining(1L)
                        .resetAt(1L)
                        .build());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/custom/token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        log.info("自定义 token endpoint 响应头 {}={}",
                SmartRedisLimiterConstant.HEADER_X_RATELIMIT_LIMIT,
                response.getHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_LIMIT));
        verify(algorithm).tryAcquireWithResult(any(), anyList(), anyString(), anyString());
        assertEquals("1", response.getHeader(SmartRedisLimiterConstant.HEADER_X_RATELIMIT_LIMIT),
                "自定义 token endpoint 应命中限流 filter");
    }

    @Test
    void testCustomCoreRuleConversion() throws ServletException, IOException {
        SimpleAkskServerProperties.LimiterConfig.LimitRuleConfig ruleConfig =
                properties.getLimiter().getOauth2().getToken().getLimits().get(0);
        ruleConfig.setCount(7);
        ruleConfig.setWindow(2);
        ruleConfig.setUnit(TimeUnit.SECONDS);
        when(algorithmFactory.getAlgorithm(SimpleAkskServerConstant.DEFAULT_LIMITER_ALGORITHM)).thenReturn(algorithm);
        when(algorithm.tryAcquireWithResult(any(), anyList(), anyString(), anyString()))
                .thenReturn(SmartRedisLimiterResult.builder()
                        .passed(true)
                        .limit(7L)
                        .remaining(6L)
                        .resetAt(1L)
                        .build());

        MockHttpServletRequest request = new MockHttpServletRequest("POST", authorizationServerSettings.getTokenEndpoint());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        ArgumentCaptor<List> rulesCaptor = ArgumentCaptor.forClass(List.class);
        verify(algorithm).tryAcquireWithResult(any(), rulesCaptor.capture(), anyString(), anyString());
        List<SmartRedisLimiterProperties.SmartLimitRule> rules = rulesCaptor.getValue();
        SmartRedisLimiterProperties.SmartLimitRule rule = rules.get(0);

        log.info("自定义限流规则转换结果: count={}, window={}, unit={}",
                rule.getCount(), rule.getWindow(), rule.getUnit());
        assertEquals(7, rule.getCount(), "自定义 count 应转换为 smart-limiter 规则");
        assertEquals(2, rule.getWindow(), "自定义 window 应转换为 smart-limiter 规则");
        assertEquals(TimeUnit.SECONDS, rule.getUnit(), "自定义 unit 应转换为 smart-limiter 规则");
    }

    private String basicAuth(String clientId, String clientSecret) {
        String value = clientId + ":" + clientSecret;
        return "Basic " + Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }
}
