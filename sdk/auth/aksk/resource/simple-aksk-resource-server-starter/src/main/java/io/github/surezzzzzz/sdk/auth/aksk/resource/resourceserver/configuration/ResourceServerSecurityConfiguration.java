package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.converter.AkskIntrospectionAuthenticationConverter;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support.IntrospectLocalCacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.introspection.NimbusOpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Resource Server Security Configuration
 *
 * <p>仅支持 INTROSPECT 模式，每次请求调 /oauth2/introspect 验证 token。
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class ResourceServerSecurityConfiguration {

    private final SimpleAkskResourceServerProperties properties;
    private final ApplicationEventPublisher eventPublisher;
    private final IntrospectLocalCacheHelper introspectLocalCacheHelper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        List<String> protectedPaths = properties.getSecurity().getProtectedPaths();
        List<String> permitAllPaths = properties.getSecurity().getPermitAllPaths();

        log.info("Configuring security filter chain, INTROSPECT mode");
        log.info("Protected paths: {}", protectedPaths);
        log.info("Permit all paths: {}", permitAllPaths);

        http
                .authorizeRequests(authorize -> {
                    if (!permitAllPaths.isEmpty()) {
                        authorize.antMatchers(permitAllPaths.toArray(new String[0])).permitAll();
                    }
                    if (!protectedPaths.isEmpty()) {
                        authorize.antMatchers(protectedPaths.toArray(new String[0])).authenticated();
                    }
                    authorize.anyRequest().permitAll();
                })
                .csrf().disable()
                .oauth2ResourceServer(oauth2 -> oauth2
                        .opaqueToken(opaque -> opaque
                                .introspector(opaqueTokenIntrospector())
                        )
                );

        log.info("Security filter chain configured successfully");
        return http.build();
    }

    /**
     * OpaqueTokenIntrospector 配置
     */
    private OpaqueTokenIntrospector opaqueTokenIntrospector() {
        SimpleAkskResourceServerProperties.Introspect config = properties.getIntrospect();
        String endpoint = config.getEndpoint();
        String clientId = config.getClientId();
        String clientSecret = config.getClientSecret();

        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalArgumentException(
                    "introspect.endpoint must be configured");
        }

        // 启动时校验 fallback 与 local-cache 的依赖关系
        SimpleAkskResourceServerProperties.Introspect.LocalCacheConfig localCache = config.getLocalCache();
        if (localCache.getFallback().isEnabled() && !localCache.isEnabled()) {
            log.warn("introspect.local-cache.fallback.enabled=true but local-cache.enabled=false, fallback will not take effect");
        }

        OpaqueTokenIntrospector delegate;
        if (StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret)) {
            log.info("Configuring OpaqueTokenIntrospector with credentials: endpoint={}", endpoint);
            delegate = new NimbusOpaqueTokenIntrospector(endpoint, clientId, clientSecret);
        } else {
            log.warn("Configuring OpaqueTokenIntrospector without credentials: endpoint={}", endpoint);
            log.warn("Make sure server introspect.require-authentication=false is configured");
            org.springframework.web.client.RestTemplate restTemplate =
                    new org.springframework.web.client.RestTemplate();
            delegate = new NimbusOpaqueTokenIntrospector(endpoint, restTemplate);
        }

        return new AkskIntrospectionAuthenticationConverter(delegate, eventPublisher, introspectLocalCacheHelper);
    }
}
