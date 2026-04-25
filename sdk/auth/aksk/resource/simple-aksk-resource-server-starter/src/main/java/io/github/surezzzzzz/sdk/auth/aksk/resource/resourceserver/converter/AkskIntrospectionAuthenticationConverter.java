package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.converter;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.event.AkskAccessEvent;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant.SimpleAkskResourceServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.model.IntrospectResult;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support.IntrospectLocalCacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Aksk Introspection Authenticator
 *
 * <p>包装 OpaqueTokenIntrospector，在 introspect 验证通过后提取 claims 到安全上下文并发布 AkskAccessEvent。
 * 若启用本地缓存（{@link IntrospectLocalCacheHelper}），命中缓存时跳过 HTTP 调用。
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class AkskIntrospectionAuthenticationConverter implements OpaqueTokenIntrospector {

    private final OpaqueTokenIntrospector delegate;
    private final ApplicationEventPublisher eventPublisher;
    private final IntrospectLocalCacheHelper cacheHelper;

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        // 查本地缓存
        if (cacheHelper.isEnabled()) {
            IntrospectResult cached = cacheHelper.get(token);
            if (cached != null) {
                log.debug("Introspect local cache hit");
                return buildPrincipalFromCache(cached, token);
            }
        }

        // 缓存未命中，发起 HTTP 调用
        OAuth2AuthenticatedPrincipal principal = delegate.introspect(token);
        Map<String, Object> attributes = principal.getAttributes();

        // 写入缓存
        if (cacheHelper.isEnabled()) {
            Object activeObj = attributes.get(SimpleAkskResourceServerConstant.INTROSPECT_CLAIM_ACTIVE);
            boolean active = activeObj instanceof Boolean ? (Boolean) activeObj : Boolean.parseBoolean(String.valueOf(activeObj));
            cacheHelper.put(token, new IntrospectResult(active, attributes));
        }

        return buildPrincipal(attributes, token);
    }

    private OAuth2AuthenticatedPrincipal buildPrincipalFromCache(IntrospectResult cached, String token) {
        return buildPrincipal(cached.getAttributes(), token);
    }

    private OAuth2AuthenticatedPrincipal buildPrincipal(Map<String, Object> attributes, String token) {
        // 提取 claims 到上下文 Map
        Map<String, String> context = extractContext(attributes);

        // 注入到 Request Attribute
        HttpServletRequest request = getCurrentRequest();
        if (request != null) {
            request.setAttribute(SimpleAkskResourceConstant.CONTEXT_ATTRIBUTE, context);
            log.debug("Introspect context injected: {} fields", context.size());

            try {
                AkskAccessEvent event = buildAccessEvent(context, request);
                eventPublisher.publishEvent(event);
            } catch (Exception e) {
                log.warn("Failed to publish AkskAccessEvent", e);
            }
        }

        // 提取权限（从 scope）
        Collection<GrantedAuthority> authorities = extractAuthorities(attributes);

        return new DefaultOAuth2AuthenticatedPrincipal(
                (String) attributes.get(SimpleAkskResourceServerConstant.JWT_CLAIM_SUB), attributes, authorities);
    }

    private Map<String, String> extractContext(Map<String, Object> attributes) {
        Map<String, String> context = new HashMap<>();
        SimpleAkskResourceConstant.JWT_CLAIM_TO_FIELD.forEach((claimName, fieldName) -> {
            Object value = attributes.get(claimName);
            if (value != null) {
                context.put(fieldName, claimValueToString(value));
            }
        });
        return context;
    }

    @SuppressWarnings("unchecked")
    private String claimValueToString(Object value) {
        if (value instanceof java.util.List) {
            return String.join(" ", (java.util.List<String>) value);
        }
        return value.toString();
    }

    private Collection<GrantedAuthority> extractAuthorities(Map<String, Object> attributes) {
        Object scopeObj = attributes.get(SimpleAkskResourceConstant.JWT_CLAIM_SCOPE);
        if (scopeObj == null || scopeObj.toString().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(scopeObj.toString().split(" "))
                .filter(s -> !s.isEmpty())
                .map(s -> new SimpleGrantedAuthority(SimpleAkskResourceServerConstant.AUTHORITY_SCOPE_PREFIX + s))
                .collect(Collectors.toList());
    }

    private AkskAccessEvent buildAccessEvent(Map<String, String> context, HttpServletRequest request) {
        return new AkskAccessEvent(
                this,
                context.get(SimpleAkskResourceConstant.FIELD_CLIENT_ID),
                context.get(SimpleAkskResourceConstant.FIELD_CLIENT_TYPE),
                context.get(SimpleAkskResourceConstant.FIELD_USER_ID),
                context.get(SimpleAkskResourceConstant.FIELD_USERNAME),
                context.get(SimpleAkskResourceConstant.FIELD_ROLES),
                context.get(SimpleAkskResourceConstant.FIELD_SCOPE),
                request.getRequestURI(),
                request.getMethod(),
                request.getRemoteAddr(),
                request.getHeader(SimpleAkskResourceServerConstant.HEADER_USER_AGENT),
                SimpleAkskResourceServerConstant.ACCESS_SOURCE_INTROSPECT,
                context.get(SimpleAkskResourceServerConstant.FIELD_TRACE_ID),
                context
        );
    }

    private HttpServletRequest getCurrentRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attrs).getRequest();
        }
        return null;
    }
}

