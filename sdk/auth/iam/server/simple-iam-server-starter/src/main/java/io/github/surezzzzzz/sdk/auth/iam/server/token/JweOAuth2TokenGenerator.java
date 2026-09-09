package io.github.surezzzzzz.sdk.auth.iam.server.token;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.AESEncrypter;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import io.github.surezzzzzz.sdk.auth.iam.core.support.IamRouteKeyHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.ConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.ClaimAccessor;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.*;

import java.time.Instant;
import java.util.Base64;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * JWE OAuth2 Token Generator
 *
 * <p>组合模式：内部委托 {@link JwtGenerator} 生成 JWS（携带 claims + 签名），
 * 再用 AES-256 GCM 对 JWS 加密为 JWE 后返回。
 *
 * <p>最终 token 形态：JWE(JWS(payload))，既保证完整性（签名）又保证机密性（加密）。
 *
 * @author surezzzzzz
 */
@Slf4j
public class JweOAuth2TokenGenerator implements OAuth2TokenGenerator<OAuth2AccessToken> {

    private final SimpleIamServerProperties properties;
    private final JWKSource<SecurityContext> jwkSource;
    private final OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer;

    private JwtGenerator delegate;
    private OctetSequenceKey aesKey;

    public JweOAuth2TokenGenerator(SimpleIamServerProperties properties,
                                   JWKSource<SecurityContext> jwkSource,
                                   OAuth2TokenCustomizer<JwtEncodingContext> tokenCustomizer) {
        this.properties = properties;
        this.jwkSource = jwkSource;
        this.tokenCustomizer = tokenCustomizer;
        init();
    }

    private void init() {
        SimpleIamServerProperties.TokenConfig tokenConfig = properties.getToken();
        String encryptionKey = tokenConfig.getEncryptionKey();
        if (encryptionKey == null || encryptionKey.trim().isEmpty()) {
            throw new ConfigurationException(ServerErrorMessage.AES_256_KEY_NOT_CONFIGURED);
        }

        byte[] aesKeyBytes;
        try {
            aesKeyBytes = Base64.getDecoder().decode(encryptionKey.trim());
        } catch (Exception e) {
            throw new ConfigurationException(ServerErrorMessage.AES_256_KEY_FORMAT_ERROR);
        }

        if (aesKeyBytes.length != SimpleIamServerConstant.AES_256_KEY_LENGTH) {
            throw new ConfigurationException(
                    String.format(ServerErrorMessage.AES_256_KEY_LENGTH_ERROR,
                            SimpleIamServerConstant.AES_256_KEY_LENGTH, aesKeyBytes.length));
        }

        JWEAlgorithm keyAlg = JWEAlgorithm.parse(SimpleIamServerConstant.JWE_KEY_ENCRYPTION_ALGORITHM);
        this.aesKey = new OctetSequenceKey.Builder(aesKeyBytes)
                .algorithm(keyAlg)
                .build();

        this.delegate = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
        this.delegate.setJwtCustomizer(tokenCustomizer);

        log.info("IAM JweOAuth2TokenGenerator initialized, AES-256 key loaded ({} bytes)", aesKeyBytes.length);
    }

    /**
     * 生成 JWE(JWS) 结构的 Access Token：内层 JWS 经定制器注入 claims，外层 AES 加密
     */
    @Override
    public OAuth2AccessToken generate(OAuth2TokenContext context) {
        if (!OAuth2TokenType.ACCESS_TOKEN.equals(context.getTokenType())) {
            return null;
        }
        Jwt jwt = delegate.generate(context);
        if (jwt == null) {
            return null;
        }

        String jweTokenValue = encryptToJwe(jwt.getTokenValue());

        return new JweOAuth2AccessToken(jweTokenValue, jwt.getIssuedAt(), jwt.getExpiresAt(),
                context.getAuthorizedScopes(), jwt.getClaims());
    }

    private String encryptToJwe(String jwsCompact) {
        try {
            JWEEncrypter encrypter = new AESEncrypter(this.aesKey);

            JWEAlgorithm keyAlg = JWEAlgorithm.parse(SimpleIamServerConstant.JWE_KEY_ENCRYPTION_ALGORITHM);
            EncryptionMethod encAlg = EncryptionMethod.parse(SimpleIamServerConstant.JWE_CONTENT_ENCRYPTION_ALGORITHM);
            JWEHeader header = new JWEHeader.Builder(keyAlg, encAlg)
                    .contentType(SimpleIamServerConstant.JWE_CONTENT_TYPE_JWT)
                    .keyID(IamRouteKeyHelper.createRouteKey(properties.getToken().getKeyId()))
                    .build();

            JWEObject jweObject = new JWEObject(header, new Payload(jwsCompact));
            jweObject.encrypt(encrypter);

            log.debug("JWE token generated, length={}", jweObject.serialize().length());
            return jweObject.serialize();

        } catch (JOSEException e) {
            log.error("Failed to encrypt JWS to JWE", e);
            throw new ConfigurationException(
                    String.format(ServerErrorMessage.JWE_GENERATE_FAILED, e.getMessage()));
        }
    }

    /**
     * JWE 格式的 OAuth2AccessToken，携带原始 JWT claims 供后续使用
     */
    private static class JweOAuth2AccessToken extends OAuth2AccessToken implements ClaimAccessor {

        private final Map<String, Object> claims;

        JweOAuth2AccessToken(String jweTokenValue, Instant issuedAt, Instant expiresAt,
                             Set<String> scopes, Map<String, Object> claims) {
            super(TokenType.BEARER, jweTokenValue, issuedAt, expiresAt, scopes);
            this.claims = claims;
        }

        /**
         * 返回内层 JWS claims（只读视图）
         */
        @Override
        public Map<String, Object> getClaims() {
            return Collections.unmodifiableMap(this.claims);
        }
    }
}
