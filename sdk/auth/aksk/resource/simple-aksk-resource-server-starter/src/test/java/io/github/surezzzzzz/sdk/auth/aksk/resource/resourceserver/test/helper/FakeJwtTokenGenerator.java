package io.github.surezzzzzz.sdk.auth.aksk.resource.resourceserver.test.helper;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.util.Date;

/**
 * 假的 JWT Token 生成器
 *
 * <p>用于测试：生成使用不同私钥签名的 JWT token，验证资源服务器会拒绝这些 token
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
public class FakeJwtTokenGenerator {

    private final RSAPrivateKey fakePrivateKey;

    /**
     * 构造函数 - 生成一个假的 RSA 密钥对
     */
    public FakeJwtTokenGenerator() {
        try {
            log.info("Generating fake RSA key pair for testing...");
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            KeyPair keyPair = keyPairGenerator.generateKeyPair();
            this.fakePrivateKey = (RSAPrivateKey) keyPair.getPrivate();
            log.info("Fake RSA key pair generated successfully");
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate fake RSA key pair", e);
        }
    }

    /**
     * 生成一个假的 JWT token
     * <p>
     * 这个 token 使用不同的私钥签名，因此无法通过资源服务器的验证
     *
     * @return 假的 JWT token
     */
    public String generateFakeToken() {
        try {
            log.info("Generating fake JWT token with different private key...");

            // 构造 JWT Claims（模拟真实 token 的结构）
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject("fake-user")
                    .issuer("http://fake-issuer.com")
                    .audience("fake-audience")
                    .claim("client_id", "fake-client-id")
                    .claim("client_type", "user")
                    .claim("user_id", "fake-user-123")
                    .claim("username", "fake-user")
                    .claim("scope", "read write")
                    .issueTime(new Date())
                    .expirationTime(new Date(System.currentTimeMillis() + 3600000)) // 1小时后过期
                    .build();

            // 使用假的私钥签名
            JWSSigner signer = new RSASSASigner(fakePrivateKey);
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                    claimsSet
            );
            signedJWT.sign(signer);

            String fakeToken = signedJWT.serialize();
            log.info("Fake JWT token generated successfully");
            log.debug("Fake token: {}", fakeToken);

            return fakeToken;

        } catch (Exception e) {
            log.error("Failed to generate fake JWT token", e);
            throw new RuntimeException("Failed to generate fake JWT token", e);
        }
    }

    /**
     * 生成一个过期的假 JWT token
     *
     * @return 过期的假 JWT token
     */
    public String generateExpiredFakeToken() {
        try {
            log.info("Generating expired fake JWT token...");

            // 构造过期的 JWT Claims
            JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                    .subject("fake-user")
                    .issuer("http://fake-issuer.com")
                    .audience("fake-audience")
                    .claim("client_id", "fake-client-id")
                    .claim("user_id", "fake-user-123")
                    .issueTime(new Date(System.currentTimeMillis() - 7200000)) // 2小时前签发
                    .expirationTime(new Date(System.currentTimeMillis() - 3600000)) // 1小时前过期
                    .build();

            // 使用假的私钥签名
            JWSSigner signer = new RSASSASigner(fakePrivateKey);
            SignedJWT signedJWT = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).build(),
                    claimsSet
            );
            signedJWT.sign(signer);

            String expiredToken = signedJWT.serialize();
            log.info("Expired fake JWT token generated successfully");

            return expiredToken;

        } catch (Exception e) {
            log.error("Failed to generate expired fake JWT token", e);
            throw new RuntimeException("Failed to generate expired fake JWT token", e);
        }
    }
}
