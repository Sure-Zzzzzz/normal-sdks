package io.github.surezzzzzz.sdk.auth.iam.server.configuration;

import com.fasterxml.jackson.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.github.surezzzzzz.sdk.auth.iam.core.support.IamRouteKeyHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamConsentRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamRefreshTokenFamilyRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.IamUserRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.*;
import io.github.surezzzzzz.sdk.auth.iam.server.support.JwtKeyProvider;
import io.github.surezzzzzz.sdk.auth.iam.server.token.JweJwtDecoder;
import io.github.surezzzzzz.sdk.auth.iam.server.token.JweOAuth2TokenGenerator;
import io.github.surezzzzzz.sdk.cache.manager.SmartCacheManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.jackson2.SecurityJackson2Modules;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService.OAuth2AuthorizationRowMapper;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.client.JdbcRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.jackson2.OAuth2AuthorizationServerJackson2Module;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.*;

import java.util.Collection;

/**
 * Authorization Server Configuration
 *
 * <p>1.0.0 骨架阶段使用 SAS 标准 Jdbc 实现；缓存装饰器（Cached）/ 审计装饰器（Auditable）
 * 将在数据层阶段以装饰器链方式接入。
 *
 * @author surezzzzzz
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class AuthorizationServerConfiguration {

    private final JwtKeyProvider jwtKeyProvider;
    private final JdbcTemplate jdbcTemplate;
    private final SimpleIamServerProperties properties;
    private final SmartCacheManager smartCacheManager;
    private final IamConsentRepository iamConsentRepository;
    private final IamUserRepository iamUserRepository;

    /**
     * 以配置注入的 RSA 密钥对构建单密钥 JWKSet，同时供 token 签发与 JWKS 端点发布；
     * 宿主需要多密钥轮换时可用同名 Bean 覆盖。
     */
    @Bean
    @ConditionalOnMissingBean
    public JWKSource<SecurityContext> jwkSource() {
        RSAKey rsaKey = new RSAKey.Builder(jwtKeyProvider.getPublicKey())
                .privateKey(jwtKeyProvider.getPrivateKey())
                .keyID(IamRouteKeyHelper.createRouteKey(properties.getToken().getKeyId()))
                .build();
        JWKSet jwkSet = new JWKSet(rsaKey);
        return (jwkSelector, securityContext) -> jwkSelector.select(jwkSet);
    }

    /**
     * 授权服务器设置：issuer 从配置注入，是 OIDC discovery 基地址与 token {@code iss} 的唯一来源。
     */
    @Bean
    @ConditionalOnMissingBean
    public AuthorizationServerSettings authorizationServerSettings() {
        return AuthorizationServerSettings.builder()
                .issuer(properties.getIssuer())
                .build();
    }

    /**
     * SAS 客户端注册走 JDBC（{@code oauth2_registered_client} 表），复用 mysql-route 数据源，
     * 与可信应用管理共用同一客户端存储。
     */
    @Bean
    @ConditionalOnMissingBean
    public RegisteredClientRepository registeredClientRepository() {
        return new JdbcRegisteredClientRepository(jdbcTemplate);
    }

    /**
     * 授权状态服务四层装配：JDBC 落库（{@code oauth2_authorization} 表）→
     * 二级缓存削减授权码 / token 高频读 → 审计装饰发布 Token 族事件 →
     * 族防线装饰（refresh 轮换记账 + 重放检测 + 族吊销联动，链最外层）；
     * RowMapper 换用开启 trusted typing 的 ObjectMapper（见
     * {@link TrustedSourceTypeResolverBuilder}）以对齐 SAS 写侧的 Long claims 包装。
     */
    @Bean
    @ConditionalOnMissingBean
    public OAuth2AuthorizationService authorizationService(SessionService sessionService,
                                                           IamAuthorizeContextService authorizeContextService,
                                                           ApplicationEventPublisher eventPublisher,
                                                           RefreshTokenFamilyService refreshTokenFamilyService,
                                                           IamRefreshTokenFamilyRepository refreshTokenFamilyRepository,
                                                           IamUserRepository userRepository) {
        JdbcOAuth2AuthorizationService jdbcService = new JdbcOAuth2AuthorizationService(
                jdbcTemplate, registeredClientRepository());
        OAuth2AuthorizationRowMapper rowMapper = new OAuth2AuthorizationRowMapper(registeredClientRepository());
        rowMapper.setObjectMapper(authorizationObjectMapper());
        jdbcService.setAuthorizationRowMapper(rowMapper);
        CachedOAuth2AuthorizationService cachedService = new CachedOAuth2AuthorizationService(
                jdbcService,
                smartCacheManager,
                properties.getToken().getAccessExpiresIn(),
                sessionService,
                authorizeContextService
        );
        IamAuditableOAuth2AuthorizationService auditableService = new IamAuditableOAuth2AuthorizationService(
                cachedService, eventPublisher, registeredClientRepository());
        return new IamRefreshTokenFamilyAuthorizationService(
                auditableService, refreshTokenFamilyService, refreshTokenFamilyRepository, userRepository);
    }

    private ObjectMapper authorizationObjectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModules(SecurityJackson2Modules.getModules(
                JdbcOAuth2AuthorizationService.class.getClassLoader()));
        objectMapper.registerModule(new OAuth2AuthorizationServerJackson2Module());
        objectMapper.addMixIn(IamUserDetailsSupport.class, IamUserDetailsSupportMixin.class);
        objectMapper.setDefaultTyping(new TrustedSourceTypeResolverBuilder());
        return objectMapper;
    }

    /**
     * Consent 服务双层装配：JDBC 标准 storage（{@code oauth2_authorization_consent} 表）
     * 之上叠投影装饰，把用户确认结果同步落 {@code iam_consent} 投影表。
     */
    @Bean
    @ConditionalOnMissingBean
    public OAuth2AuthorizationConsentService authorizationConsentService() {
        OAuth2AuthorizationConsentService jdbc =
                new JdbcOAuth2AuthorizationConsentService(jdbcTemplate, registeredClientRepository());
        return new IamDecoratingConsentService(jdbc, iamConsentRepository, iamUserRepository,
                registeredClientRepository());
    }

    /**
     * 本服务自用解码器（userinfo 等内部校验路径）；{@code token.format=jwe} 时
     * 外层再包 AES-256 解密，与签发侧的 JWE(JWS) 结构对称。
     */
    @Bean
    @ConditionalOnMissingBean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        JwtDecoder jwtDecoder = OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
        if (SimpleIamServerConstant.TOKEN_FORMAT_JWE.equalsIgnoreCase(properties.getToken().getFormat())) {
            return new JweJwtDecoder(properties, jwtDecoder);
        }
        return jwtDecoder;
    }

    /**
     * 按 {@code token.format} 双模式装配生成器：{@code jwe}（默认）为
     * JWE Access Token + JWS OIDC ID Token，{@code jwt} 为纯 JWS；
     * 非法取值启动期快速失败。委派生成器同时挂 opaque access / refresh 生成器
     * 以覆盖 SAS 标准签发路径。
     */
    @Bean
    @ConditionalOnMissingBean
    public OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator(
            JWKSource<SecurityContext> jwkSource,
            OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer) {

        String format = properties.getToken().getFormat();
        if (SimpleIamServerConstant.TOKEN_FORMAT_JWE.equalsIgnoreCase(format)) {
            JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
            jwtGenerator.setJwtCustomizer(tokenCustomizer);
            OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
            log.info("IAM token generator = JWE access token + JWS OIDC ID token");
            return new DelegatingOAuth2TokenGenerator(
                    new JweOAuth2TokenGenerator(properties, jwkSource, tokenCustomizer),
                    jwtGenerator, accessTokenGenerator, new OAuth2RefreshTokenGenerator());
        }

        if (!SimpleIamServerConstant.TOKEN_FORMAT_JWT.equalsIgnoreCase(format)) {
            throw new ConfigurationException(
                    String.format(ServerErrorMessage.TOKEN_FORMAT_UNSUPPORTED, format));
        }

        NimbusJwtEncoder jwtEncoder = new NimbusJwtEncoder(jwkSource);
        JwtGenerator jwtGenerator = new JwtGenerator(jwtEncoder);
        jwtGenerator.setJwtCustomizer(tokenCustomizer);
        OAuth2AccessTokenGenerator accessTokenGenerator = new OAuth2AccessTokenGenerator();
        log.info("IAM token generator = JWT (JWS RS256 access token + OIDC ID token)");
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, accessTokenGenerator, new OAuth2RefreshTokenGenerator());
    }

    /**
     * SAS 0.4.1 写侧 ParametersMapper 的 typing 无 allowlist，token claims 中 Object 位置的
     * java.lang.Long（sid / auth_time / 应用授权数值字段）会以 ["java.lang.Long",n] 包装落库；
     * SecurityJackson2Modules 读侧 allowlist 不含 Long（校验器包私有、无公开扩展点），
     * refresh 流程读回即抛 allowlist 异常。授权 JSON 列的数据源仅本库（trusted source），
     * 按 spring-security 官方指引放开为与写侧对称的 default typing。
     */
    static class TrustedSourceTypeResolverBuilder extends ObjectMapper.DefaultTypeResolverBuilder {
        TrustedSourceTypeResolverBuilder() {
            super(ObjectMapper.DefaultTyping.NON_FINAL, LaissezFaireSubTypeValidator.instance);
            init(JsonTypeInfo.Id.CLASS, null);
            inclusion(JsonTypeInfo.As.PROPERTY);
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.PROPERTY)
    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY,
            getterVisibility = JsonAutoDetect.Visibility.NONE,
            isGetterVisibility = JsonAutoDetect.Visibility.NONE)
    @JsonIgnoreProperties(ignoreUnknown = true)
    private abstract static class IamUserDetailsSupportMixin {

        @JsonCreator
        IamUserDetailsSupportMixin(@JsonProperty("userId") Long userId,
                                   @JsonProperty("username") String username,
                                   @JsonProperty("password") String password,
                                   @JsonProperty("enabled") boolean enabled,
                                   @JsonProperty("accountNonExpired") boolean accountNonExpired,
                                   @JsonProperty("credentialsNonExpired") boolean credentialsNonExpired,
                                   @JsonProperty("accountNonLocked") boolean accountNonLocked,
                                   @JsonProperty("authorities") Collection<?> authorities) {
        }
    }
}
