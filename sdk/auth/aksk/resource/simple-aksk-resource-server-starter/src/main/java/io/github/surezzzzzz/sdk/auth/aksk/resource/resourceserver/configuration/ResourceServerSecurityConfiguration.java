package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant.SimpleAkskResourceServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant.VerificationMode;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.converter.AkskIntrospectionAuthenticationConverter;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.converter.AkskJwtAuthenticationConverter;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.support.IntrospectLocalCacheHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.introspection.NimbusOpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.util.StreamUtils;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.List;

/**
 * Resource Server Security Configuration
 *
 * <p>支持两种 Token 验证模式：
 * <ul>
 *   <li>JWT（默认）：本地验签，性能最好，不支持即时撤销感知</li>
 *   <li>INTROSPECT：调 /oauth2/introspect 端点验证，支持即时撤销感知</li>
 * </ul>
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class ResourceServerSecurityConfiguration {

    private final SimpleAkskResourceServerProperties properties;
    private final ResourceLoader resourceLoader;
    private final ApplicationEventPublisher eventPublisher;
    private final IntrospectLocalCacheHelper introspectLocalCacheHelper;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        List<String> protectedPaths = properties.getSecurity().getProtectedPaths();
        List<String> permitAllPaths = properties.getSecurity().getPermitAllPaths();
        VerificationMode mode = properties.getVerificationMode();

        log.info("Configuring security filter chain, verificationMode={}", mode);
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
                .csrf().disable();

        if (VerificationMode.INTROSPECT.equals(mode)) {
            // Introspect 模式
            http.oauth2ResourceServer(oauth2 -> oauth2
                    .opaqueToken(opaque -> opaque
                            .introspector(opaqueTokenIntrospector())
                    )
            );
        } else {
            // JWT 模式（默认）
            http.oauth2ResourceServer(oauth2 -> oauth2
                    .jwt(jwt -> jwt
                            .jwtAuthenticationConverter(new AkskJwtAuthenticationConverter(eventPublisher))
                    )
            );
        }

        log.info("Security filter chain configured successfully");
        return http.build();
    }

    @Bean
    public JwtDecoder jwtDecoder() throws Exception {
        // introspect 模式下不需要 JwtDecoder，但 bean 仍需存在避免自动配置报错
        if (VerificationMode.INTROSPECT.equals(properties.getVerificationMode())) {
            log.info("Introspect mode enabled, JwtDecoder not used for token verification");
        }

        String issuerUri = properties.getJwt().getIssuerUri();
        if (StringUtils.hasText(issuerUri)) {
            log.info("Configuring JWT Decoder with issuer-uri: {}", issuerUri);
            return JwtDecoders.fromIssuerLocation(issuerUri);
        }

        log.info("Configuring JWT Decoder with manual public key");
        RSAPublicKey publicKey = loadPublicKey();
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    /**
     * Introspect 模式下的 OpaqueTokenIntrospector
     */
    private OpaqueTokenIntrospector opaqueTokenIntrospector() {
        SimpleAkskResourceServerProperties.Introspect config = properties.getIntrospect();
        String endpoint = config.getEndpoint();
        String clientId = config.getClientId();
        String clientSecret = config.getClientSecret();

        if (!StringUtils.hasText(endpoint)) {
            throw new IllegalArgumentException(
                    "introspect.endpoint must be configured when verificationMode=INTROSPECT");
        }

        OpaqueTokenIntrospector delegate;
        if (StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret)) {
            log.info("Configuring OpaqueTokenIntrospector with credentials: endpoint={}", endpoint);
            delegate = new NimbusOpaqueTokenIntrospector(endpoint, clientId, clientSecret);
        } else {
            log.warn("Configuring OpaqueTokenIntrospector without credentials: endpoint={}", endpoint);
            log.warn("Make sure server introspect.require-authentication=false is configured");
            // 不带认证，使用自定义 RestTemplate
            org.springframework.web.client.RestTemplate restTemplate =
                    new org.springframework.web.client.RestTemplate();
            delegate = new NimbusOpaqueTokenIntrospector(endpoint, restTemplate);
        }

        // 用 AkskIntrospectionAuthenticationConverter 包装，注入上下文并发布事件
        return new AkskIntrospectionAuthenticationConverter(delegate, eventPublisher, introspectLocalCacheHelper);
    }

    private RSAPublicKey loadPublicKey() throws Exception {
        String publicKeyString = properties.getJwt().getPublicKey();
        String publicKeyLocation = properties.getJwt().getPublicKeyLocation();

        if (StringUtils.hasText(publicKeyString)) {
            log.info("Loading public key from configuration string");
            return parsePublicKey(publicKeyString);
        }

        if (StringUtils.hasText(publicKeyLocation)) {
            log.info("Loading public key from location: {}", publicKeyLocation);
            Resource resource = resourceLoader.getResource(publicKeyLocation);
            if (!resource.exists()) {
                throw new IllegalArgumentException(
                        SimpleAkskResourceServerConstant.ERROR_PUBLIC_KEY_FILE_NOT_FOUND + publicKeyLocation);
            }
            try (InputStream inputStream = resource.getInputStream()) {
                byte[] keyBytes = StreamUtils.copyToByteArray(inputStream);
                return parsePublicKey(new String(keyBytes));
            }
        }

        throw new IllegalArgumentException(SimpleAkskResourceServerConstant.ERROR_PUBLIC_KEY_NOT_CONFIGURED);
    }

    private RSAPublicKey parsePublicKey(String keyContent) throws Exception {
        String publicKeyPEM = keyContent
                .replace(SimpleAkskResourceServerConstant.PEM_PUBLIC_KEY_HEADER, "")
                .replace(SimpleAkskResourceServerConstant.PEM_PUBLIC_KEY_FOOTER, "")
                .replaceAll("\\s", "");
        byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);
        KeyFactory keyFactory = KeyFactory.getInstance(SimpleAkskResourceServerConstant.ALGORITHM_RSA);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }
}
