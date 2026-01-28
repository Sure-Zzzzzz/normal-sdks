package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.constant.SimpleAkskResourceServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.converter.AkskJwtAuthenticationConverter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
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
 * <p>配置 JWT 验证和路径安全规则。
 *
 * <p>功能：
 * <ul>
 *   <li>JWT Token 验证（使用公钥）</li>
 *   <li>路径安全规则（保护路径 + 白名单路径）</li>
 *   <li>自定义 JWT 转换器（提取 claims 到上下文）</li>
 * </ul>
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class ResourceServerSecurityConfiguration {

    private final SimpleAkskResourceServerProperties properties;
    private final ResourceLoader resourceLoader;

    /**
     * 配置安全过滤链
     *
     * @param http HttpSecurity
     * @return SecurityFilterChain
     * @throws Exception 配置异常
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        List<String> protectedPaths = properties.getSecurity().getProtectedPaths();
        List<String> permitAllPaths = properties.getSecurity().getPermitAllPaths();

        log.info("Configuring security filter chain");
        log.info("Protected paths: {}", protectedPaths);
        log.info("Permit all paths: {}", permitAllPaths);

        http
                .authorizeRequests(authorize -> {
                    // 白名单路径：不需要认证
                    if (!permitAllPaths.isEmpty()) {
                        authorize.antMatchers(permitAllPaths.toArray(new String[0])).permitAll();
                    }

                    // 保护路径：需要 JWT 认证
                    if (!protectedPaths.isEmpty()) {
                        authorize.antMatchers(protectedPaths.toArray(new String[0])).authenticated();
                    }

                    // 其他路径：默认放行
                    authorize.anyRequest().permitAll();
                })
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .jwtAuthenticationConverter(new AkskJwtAuthenticationConverter())
                        )
                )
                .csrf().disable();

        log.info("Security filter chain configured successfully");
        return http.build();
    }

    /**
     * 配置 JWT Decoder
     * <p>
     * 优先级：
     * <ol>
     *   <li>issuer-uri: 从授权服务器自动获取 JWKS（推荐）</li>
     *   <li>public-key: 手动配置公钥字符串</li>
     *   <li>public-key-location: 从文件加载公钥</li>
     * </ol>
     *
     * @return JwtDecoder
     * @throws Exception 配置异常
     */
    @Bean
    public JwtDecoder jwtDecoder() throws Exception {
        String issuerUri = properties.getJwt().getIssuerUri();

        // 方式1：使用 issuer-uri（推荐）
        if (StringUtils.hasText(issuerUri)) {
            log.info("Configuring JWT Decoder with issuer-uri: {}", issuerUri);
            log.info("Will fetch JWKS from: {}/oauth2/jwks", issuerUri);
            return JwtDecoders.fromIssuerLocation(issuerUri);
        }

        // 方式2：手动配置公钥
        log.info("Configuring JWT Decoder with manual public key");
        RSAPublicKey publicKey = loadPublicKey();
        log.info("JWT Decoder configured with RSA public key");
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }

    /**
     * 加载 RSA 公钥
     *
     * @return RSAPublicKey
     * @throws Exception 加载异常
     */
    private RSAPublicKey loadPublicKey() throws Exception {
        String publicKeyString = properties.getJwt().getPublicKey();
        String publicKeyLocation = properties.getJwt().getPublicKeyLocation();

        // 方式1：从配置字符串加载
        if (StringUtils.hasText(publicKeyString)) {
            log.info("Loading public key from configuration string");
            return parsePublicKey(publicKeyString);
        }

        // 方式2：从文件加载
        if (StringUtils.hasText(publicKeyLocation)) {
            log.info("Loading public key from location: {}", publicKeyLocation);
            Resource resource = resourceLoader.getResource(publicKeyLocation);
            if (!resource.exists()) {
                throw new IllegalArgumentException(SimpleAkskResourceServerConstant.ERROR_PUBLIC_KEY_FILE_NOT_FOUND + publicKeyLocation);
            }

            try (InputStream inputStream = resource.getInputStream()) {
                byte[] keyBytes = StreamUtils.copyToByteArray(inputStream);
                String keyContent = new String(keyBytes);
                return parsePublicKey(keyContent);
            }
        }

        throw new IllegalArgumentException(SimpleAkskResourceServerConstant.ERROR_PUBLIC_KEY_NOT_CONFIGURED);
    }

    /**
     * 解析 PEM 格式的公钥字符串
     *
     * @param keyContent PEM 格式的公钥字符串
     * @return RSAPublicKey
     * @throws Exception 解析异常
     */
    private RSAPublicKey parsePublicKey(String keyContent) throws Exception {
        // 移除 PEM 头尾和换行符
        String publicKeyPEM = keyContent
                .replace(SimpleAkskResourceServerConstant.PEM_PUBLIC_KEY_HEADER, "")
                .replace(SimpleAkskResourceServerConstant.PEM_PUBLIC_KEY_FOOTER, "")
                .replaceAll("\\s", "");

        // Base64 解码
        byte[] encoded = Base64.getDecoder().decode(publicKeyPEM);

        // 生成公钥
        KeyFactory keyFactory = KeyFactory.getInstance(SimpleAkskResourceServerConstant.ALGORITHM_RSA);
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(encoded);
        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }
}
