package io.github.surezzzzzz.sdk.auth.iam.adapter.login.oidc.test.support;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import java.time.Instant;
import java.util.Date;
import java.util.List;

/**
 * 测试用 ID token 铸造支持：本地生成 RSA 密钥并按 OIDC 声明签发真签名 JWT
 *
 * @author surezzzzzz
 */
public final class OidcIdTokenMintSupport {

    private OidcIdTokenMintSupport() {
        throw new UnsupportedOperationException("测试支持类不允许实例化");
    }

    /**
     * 生成测试签名密钥
     */
    public static RSAKey generateSigningKey(String keyId) {
        try {
            return new RSAKeyGenerator(2048).keyID(keyId).generate();
        } catch (com.nimbusds.jose.JOSEException exception) {
            throw new AssertionError("生成测试 RSA 密钥失败", exception);
        }
    }

    /**
     * 铸造 ID token（RS256 真签名）
     */
    public static String mintIdToken(RSAKey signingKey, String issuer, String clientId,
                                     String subject, String username, String nonce) {
        try {
            JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .type(JOSEObjectType.JWT)
                    .keyID(signingKey.getKeyID())
                    .build();
            JWTClaimsSet claims = new JWTClaimsSet.Builder()
                    .issuer(issuer)
                    .audience(List.of(clientId))
                    .subject(subject)
                    .claim("azp", clientId)
                    .claim("preferred_username", username)
                    .claim("name", username + " 显示名")
                    .claim("email", username + "@sure-iam.test")
                    .claim("nonce", nonce)
                    .issueTime(Date.from(Instant.now()))
                    .expirationTime(Date.from(Instant.now().plusSeconds(300)))
                    .build();
            SignedJWT jwt = new SignedJWT(header, claims);
            jwt.sign(new RSASSASigner(signingKey.toRSAPrivateKey()));
            return jwt.serialize();
        } catch (com.nimbusds.jose.JOSEException exception) {
            throw new AssertionError("铸造测试 ID token 失败", exception);
        }
    }

    /**
     * 生成 JWKS 端点响应（仅公钥）
     */
    public static String toPublicJwks(RSAKey signingKey) {
        return new com.nimbusds.jose.jwk.JWKSet(signingKey).toJSONObject().toString();
    }
}
