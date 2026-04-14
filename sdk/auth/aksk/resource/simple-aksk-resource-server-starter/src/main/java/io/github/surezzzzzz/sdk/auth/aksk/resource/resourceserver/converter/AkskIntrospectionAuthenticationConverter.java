package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.converter;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.event.AkskAccessEvent;
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
 *
 * @author surezzzzzz
 */
@Slf4j
@RequiredArgsConstructor
public class AkskIntrospectionAuthenticationConverter implements OpaqueTokenIntrospector {

    private final OpaqueTokenIntrospector delegate;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public OAuth2AuthenticatedPrincipal introspect(String token) {
        OAuth2AuthenticatedPrincipal principal = delegate.introspect(token);
        Map<String, Object> attributes = principal.getAttributes();

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
                principal.getName(), attributes, authorities);
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
                .map(s -> new SimpleGrantedAuthority("SCOPE_" + s))
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
                request.getHeader("User-Agent"),
                "introspect",
                context.get("traceId"),
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
