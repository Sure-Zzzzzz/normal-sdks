package io.github.surezzzzzz.sdk.auth.aksk.server.token;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.AESDecrypter;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.server.provider.JwtKeyProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.PostConstruct;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.util.Base64;

/**
 * JWE JWT Decoder
 * <p>
 * 用于 server 侧 introspect/revoke 时解析 JWE token：
 * <ol>
 *   <li>AES-256 密钥解密 JWE → JWS compact string</li>
 *   <li>RSA 公钥验签 JWS</li>
 *   <li>解析 claims</li>
 * </ol>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class JweJwtDecoder {

    private final SimpleAkskServerProperties properties;
    private final JwtKeyProvider jwtKeyProvider;

    private OctetSequenceKey aesKey;
    private RSAKey rsaPublicKey;

    @PostConstruct
    public void init() {
        String encryptionKey = properties.getJwt().getEncryptionKey();
        if (encryptionKey == null || encryptionKey.trim().isEmpty()) {
            throw new ConfigurationException(ServerErrorMessage.AES_256_KEY_NOT_CONFIGURED);
        }

        byte[] aesKeyBytes;
        try {
            aesKeyBytes = Base64.getDecoder().decode(encryptionKey.trim());
        } catch (Exception e) {
            throw new ConfigurationException(ServerErrorMessage.AES_256_KEY_FORMAT_ERROR);
        }

        if (aesKeyBytes.length != SimpleAkskServerConstant.AES_256_KEY_LENGTH) {
            throw new ConfigurationException(
                    String.format(ServerErrorMessage.AES_256_KEY_LENGTH_ERROR,
                            SimpleAkskServerConstant.AES_256_KEY_LENGTH, aesKeyBytes.length));
        }

        JWEAlgorithm keyAlg = JWEAlgorithm.parse(SimpleAkskServerConstant.JWE_KEY_ENCRYPTION_ALGORITHM);
        this.aesKey = new OctetSequenceKey.Builder(aesKeyBytes)
                .algorithm(keyAlg)
                .build();

        RSAPublicKey publicKey = jwtKeyProvider.getPublicKey();
        this.rsaPublicKey = new RSAKey.Builder(publicKey).build();

        log.info("JWE JWT Decoder initialized, AES-256 key and RSA public key loaded");
    }

    /**
     * 解析 JWE Token，返回 JWTClaimsSet
     *
     * @param jweToken JWE compact string
     * @return JWTClaimsSet
     * @throws ConfigurationException 解密或验签失败
     */
    public JWTClaimsSet decode(String jweToken) {
        try {
            // Step 1: AES-256 解密 JWE → JWS
            JWEObject jweObject = JWEObject.parse(jweToken);

            JWEDecrypter decrypter = new AESDecrypter(this.aesKey);
            jweObject.decrypt(decrypter);

            String jwsCompact = jweObject.getPayload().toString();
            log.debug("JWE decrypted to JWS, length={}", jwsCompact.length());

            // Step 2: RSA 公钥验签 JWS
            JWSObject jwsObject = JWSObject.parse(jwsCompact);
            JWSVerifier verifier = new RSASSAVerifier(this.rsaPublicKey);

            if (!jwsObject.verify(verifier)) {
                throw new ConfigurationException(ServerErrorMessage.JWE_SIGNATURE_VERIFICATION_FAILED);
            }

            log.debug("JWS signature verified successfully");

            // Step 3: 解析 claims
            JWTClaimsSet claimsSet = JWTClaimsSet.parse(jwsObject.getPayload().toJSONObject());
            return claimsSet;

        } catch (ParseException e) {
            log.error("Failed to parse JWE token", e);
            throw new ConfigurationException(
                    String.format(ServerErrorMessage.JWE_DECODE_FAILED, e.getMessage()));
        } catch (JOSEException e) {
            log.error("Failed to decrypt JWE token", e);
            throw new ConfigurationException(
                    String.format(ServerErrorMessage.JWE_DECRYPT_FAILED, e.getMessage()));
        }
    }
}