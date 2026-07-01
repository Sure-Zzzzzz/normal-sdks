package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant.SimpleAkskResourceServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.converter.AkskIntrospectionAuthenticationConverter;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.exception.SimpleAkskResourceServerConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support.IntrospectLocalCacheHelper;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support.SecurityPathHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.server.resource.introspection.NimbusOpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

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
    private final Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        SimpleAkskResourceServerProperties.Security security = properties.getSecurity();
        String contextPath = environment.getProperty(
                SimpleAkskResourceServerConstant.PROPERTY_SERVER_SERVLET_CONTEXT_PATH);
        List<String> protectedPaths = SecurityPathHelper.normalizePaths(
                security.getProtectedPaths(), contextPath, security.isContextPathAware());
        List<String> permitAllPaths = SecurityPathHelper.normalizePaths(
                security.getPermitAllPaths(), contextPath, security.isContextPathAware());
        validateSecurityPaths(permitAllPaths, protectedPaths);

        boolean permitAllUniversal = permitAllPaths.contains(SimpleAkskResourceServerConstant.ANT_PATTERN_ALL);
        boolean protectedUniversal = protectedPaths.contains(SimpleAkskResourceServerConstant.ANT_PATTERN_ALL);
        List<String> nonUniversalPermitAllPaths = filterNonUniversalPaths(permitAllPaths);

        log.info("Configuring security filter chain, INTROSPECT mode");
        log.info("Servlet context path: {}", StringUtils.hasText(contextPath)
                ? contextPath : SimpleAkskResourceServerConstant.URL_PATH_SEPARATOR);
        log.info("Context path aware: {}", security.isContextPathAware());
        log.info("Protected paths: {} -> {}", security.getProtectedPaths(), protectedPaths);
        log.info("Permit all paths: {} -> {}", security.getPermitAllPaths(), permitAllPaths);

        http
                .authorizeRequests(authorize -> {
                    if (permitAllUniversal && protectedPaths.isEmpty()) {
                        authorize.antMatchers(SimpleAkskResourceServerConstant.ANT_PATTERN_ALL).permitAll();
                        return;
                    }
                    if (!nonUniversalPermitAllPaths.isEmpty()) {
                        authorize.antMatchers(nonUniversalPermitAllPaths.toArray(new String[0])).permitAll();
                    }
                    if (protectedUniversal) {
                        authorize.antMatchers(SimpleAkskResourceServerConstant.ANT_PATTERN_ALL).authenticated();
                        return;
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

    private void validateSecurityPaths(List<String> permitAllPaths, List<String> protectedPaths) {
        if (!protectedPaths.isEmpty()
                && permitAllPaths.contains(SimpleAkskResourceServerConstant.ANT_PATTERN_ALL)) {
            throw new SimpleAkskResourceServerConfigurationException(
                    SimpleAkskResourceServerConstant.ERROR_PERMIT_ALL_OVERRIDES_PROTECTED);
        }
    }

    private List<String> filterNonUniversalPaths(List<String> paths) {
        return paths.stream()
                .filter(path -> !SimpleAkskResourceServerConstant.ANT_PATTERN_ALL.equals(path))
                .collect(Collectors.toList());
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
