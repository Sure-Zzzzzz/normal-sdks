package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.converter;

import io.github.surezzzzzz.sdk.auth.aksk.core.constant.JwtClaimConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.AkskResourceIntrospectionClaimConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.exception.SimpleAkskResourceServerConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.model.IntrospectResult;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support.IntrospectLocalCacheHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestOperations;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * AKSK内省缓存适配器。
 *
 * @author surezzzzzz
 */
@Slf4j
public class AkskIntrospectionAuthenticationConverter implements OpaqueTokenIntrospector {

    private final IntrospectionClient client;
    private final IntrospectLocalCacheHelper cacheHelper;

    /**
     * 创建AKSK内省缓存适配器。
     *
     * @param endpoint       内省端点
     * @param restOperations 已配置客户端认证的HTTP客户端
     * @param cacheHelper    本地缓存
     */
    public AkskIntrospectionAuthenticationConverter(URI endpoint, RestOperations restOperations,
                                                    IntrospectLocalCacheHelper cacheHelper) {
        this(new HttpIntrospectionClient(endpoint, restOperations), cacheHelper);
    }

    /**
     * 创建AKSK内省缓存适配器。
     *
     * @param delegate    内省器
     * @param cacheHelper 本地缓存
     */
    public AkskIntrospectionAuthenticationConverter(OpaqueTokenIntrospector delegate,
                                                    IntrospectLocalCacheHelper cacheHelper) {
        this((IntrospectionClient) token -> {
            try {
                OAuth2AuthenticatedPrincipal principal = delegate.introspect(token);
                if (principal == null || principal.getAttributes() == null) {
                    throw new OAuth2IntrospectionException("AKSK内省响应为空");
                }
                Map<String, Object> attributes = principal.getAttributes();
                return new IntrospectResult(Boolean.TRUE.equals(
                        attributes.get(AkskResourceIntrospectionClaimConstant.ACTIVE)), attributes);
            } catch (org.springframework.web.client.RestClientException exception) {
                throw new IntrospectionEndpointUnavailableException("AKSK内省端点不可用", exception);
            }
        }, cacheHelper);
    }

    private AkskIntrospectionAuthenticationConverter(IntrospectionClient client, IntrospectLocalCacheHelper cacheHelper) {
        if (client == null || cacheHelper == null) {
            throw new SimpleAkskResourceServerConfigurationException("AKSK内省依赖不能为null");
        }
        this.client = client;
        this.cacheHelper = cacheHelper;
    }

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        if (cacheHelper.isEnabled()) {
            IntrospectResult cached = cacheHelper.get(token);
            if (cached != null) {
                log.debug("AKSK内省主缓存命中");
                return buildPrincipal(cached);
            }
            cacheHelper.logStatsIfNeeded();
        }
        try {
            IntrospectResult result = client.introspect(token);
            if (cacheHelper.isEnabled()) {
                cacheHelper.put(token, result);
            }
            log.debug("AKSK内省完成，结果状态={}", result != null && result.isActive());
            return buildPrincipal(result);
        } catch (IntrospectionEndpointUnavailableException exception) {
            log.warn("AKSK内省端点调用失败，进入故障降级判断，异常类型={}",
                    exception.getClass().getName());
            return fallback(token, exception);
        }
    }

    private OAuth2AuthenticatedPrincipal fallback(String token, IntrospectionEndpointUnavailableException exception) {
        if (!cacheHelper.isFallbackEnabled()) {
            log.debug("AKSK内省故障降级未启用，拒绝认证");
            throw exception;
        }
        IntrospectResult cached = cacheHelper.getFallback(token);
        if (cached == null || !cached.isActive()) {
            log.debug("AKSK内省故障降级未命中有效条目，拒绝认证");
            throw exception;
        }
        cacheHelper.incrementFallbackHit();
        log.warn("AKSK内省端点不可用，使用显式开启的兜底缓存");
        return buildPrincipal(cached);
    }

    private OAuth2AuthenticatedPrincipal buildPrincipal(IntrospectResult result) {
        if (result == null || result.getAttributes() == null) {
            throw new OAuth2IntrospectionException("AKSK内省响应为空");
        }
        if (!result.isActive()) {
            return new DefaultOAuth2AuthenticatedPrincipal("inactive",
                    result.getAttributes(), Collections.emptyList());
        }
        Object subject = result.getAttributes().get(JwtClaimConstant.SUB);
        if (!(subject instanceof String)) {
            throw new OAuth2IntrospectionException("AKSK内省响应缺少主体");
        }
        return new DefaultOAuth2AuthenticatedPrincipal((String) subject,
                result.getAttributes(), Collections.emptyList());
    }

    @FunctionalInterface
    private interface IntrospectionClient {

        IntrospectResult introspect(String token);
    }

    private static final class HttpIntrospectionClient implements IntrospectionClient {

        private final URI endpoint;
        private final RestOperations restOperations;

        private HttpIntrospectionClient(URI endpoint, RestOperations restOperations) {
            if (endpoint == null || restOperations == null) {
                throw new SimpleAkskResourceServerConfigurationException("AKSK内省HTTP依赖不能为null");
            }
            this.endpoint = endpoint;
            this.restOperations = restOperations;
        }

        @Override
        public IntrospectResult introspect(String token) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> form = new LinkedMultiValueMap<String, String>();
            form.add("token", token);
            try {
                ResponseEntity<?> response = restOperations.exchange(endpoint, HttpMethod.POST,
                        new HttpEntity<MultiValueMap<String, String>>(form, headers), Object.class);
                if (!(response.getBody() instanceof Map)) {
                    throw new OAuth2IntrospectionException("AKSK内省响应非法");
                }
                Map<?, ?> responseBody = (Map<?, ?>) response.getBody();
                Object active = responseBody.get(AkskResourceIntrospectionClaimConstant.ACTIVE);
                if (!(active instanceof Boolean)) {
                    throw new OAuth2IntrospectionException("AKSK内省响应缺少active");
                }
                Map<String, Object> attributes = new HashMap<String, Object>();
                for (Map.Entry<?, ?> entry : responseBody.entrySet()) {
                    if (entry.getKey() instanceof String) {
                        attributes.put((String) entry.getKey(), entry.getValue());
                    }
                }
                return new IntrospectResult(Boolean.TRUE.equals(active), attributes);
            } catch (RestClientException exception) {
                throw new IntrospectionEndpointUnavailableException("AKSK内省端点不可用", exception);
            }
        }
    }

    private static final class IntrospectionEndpointUnavailableException extends OAuth2IntrospectionException {

        private static final long serialVersionUID = 1L;

        private IntrospectionEndpointUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
