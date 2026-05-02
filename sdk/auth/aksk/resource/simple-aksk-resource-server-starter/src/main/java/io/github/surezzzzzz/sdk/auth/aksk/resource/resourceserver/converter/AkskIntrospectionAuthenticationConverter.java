package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.converter;

import io.github.surezzzzzz.sdk.auth.aksk.resource.core.constant.SimpleAkskResourceConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.core.event.AkskAccessEvent;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant.SimpleAkskResourceServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.model.IntrospectResult;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support.ConverterHelper;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support.IntrospectLocalCacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;

import javax.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Aksk Introspection Authenticator
 *
 * <p>包装 OpaqueTokenIntrospector，在 introspect 验证通过后提取 claims 到安全上下文并发布 AkskAccessEvent。
 * 若启用本地缓存（{@link IntrospectLocalCacheHelper}），命中缓存时跳过 HTTP 调用。
 * 若启用兜底缓存，端点不可用时使用兜底缓存放行（仅 active=true 的条目）。
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
        String tokenPrefix = token.length() > 8 ? token.substring(0, 8) : token;

        // 查主缓存
        if (cacheHelper.isEnabled()) {
            IntrospectResult cached = cacheHelper.get(token);
            if (cached != null) {
                log.info("Introspect local cache hit: token={}...", tokenPrefix);
                return buildPrincipal(cached.getAttributes(), token);
            }
            log.debug("Introspect local cache miss: token={}...", tokenPrefix);
            cacheHelper.logStatsIfNeeded();
        }

        // 缓存未命中，发起 HTTP 调用
        try {
            OAuth2AuthenticatedPrincipal principal = delegate.introspect(token);
            Map<String, Object> attributes = principal.getAttributes();

            // 写主缓存 + 兜底缓存（含 active=false，使撤销信息尽快传播到兜底层）
            if (cacheHelper.isEnabled()) {
                Object activeObj = attributes.get(SimpleAkskResourceServerConstant.INTROSPECT_CLAIM_ACTIVE);
                boolean active = activeObj instanceof Boolean
                        ? (Boolean) activeObj
                        : Boolean.parseBoolean(String.valueOf(activeObj));
                cacheHelper.put(token, new IntrospectResult(active, attributes));
            }

            return buildPrincipal(attributes, token);
        } catch (Exception e) {
            // 降级处理
            if (cacheHelper.isFallbackEnabled()) {
                IntrospectResult fallback = cacheHelper.getFallback(token);
                if (fallback != null && fallback.isActive()) {
                    cacheHelper.incrementFallbackHit();
                    log.warn("Introspect endpoint unavailable, falling back to fallback cache: token={}...", tokenPrefix);
                    return buildPrincipal(fallback.getAttributes(), token);
                }
                log.warn("Introspect endpoint unavailable, no valid fallback entry, rejecting: token={}...", tokenPrefix);
            } else {
                log.error("Introspect failed, fallback disabled, rejecting: token={}...", tokenPrefix, e);
            }
            throw e;
        }
    }

    private OAuth2AuthenticatedPrincipal buildPrincipal(Map<String, Object> attributes, String token) {
        Map<String, String> context = extractContext(attributes);

        HttpServletRequest request = ConverterHelper.getCurrentRequest();
        if (request != null) {
            request.setAttribute(SimpleAkskResourceConstant.CONTEXT_ATTRIBUTE, context);
            log.debug("Introspect context injected: {} fields", context.size());

            try {
                AkskAccessEvent event = ConverterHelper.buildAccessEvent(
                        this, SimpleAkskResourceServerConstant.ACCESS_SOURCE_INTROSPECT, context, request);
                eventPublisher.publishEvent(event);
            } catch (Exception e) {
                log.warn("Failed to publish AkskAccessEvent", e);
            }
        }

        Collection<GrantedAuthority> authorities = ConverterHelper.extractAuthorities(
                context.get(SimpleAkskResourceConstant.FIELD_SCOPE));

        return new DefaultOAuth2AuthenticatedPrincipal(
                (String) attributes.get(SimpleAkskResourceServerConstant.JWT_CLAIM_SUB), attributes, authorities);
    }

    private Map<String, String> extractContext(Map<String, Object> attributes) {
        Map<String, String> context = new HashMap<>();
        SimpleAkskResourceConstant.JWT_CLAIM_TO_FIELD.forEach((claimName, fieldName) -> {
            Object value = attributes.get(claimName);
            if (value != null) {
                context.put(fieldName, ConverterHelper.claimValueToString(value));
            }
        });
        return context;
    }
}
