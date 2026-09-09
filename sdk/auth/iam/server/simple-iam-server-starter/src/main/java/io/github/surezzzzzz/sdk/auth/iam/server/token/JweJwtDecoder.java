package io.github.surezzzzzz.sdk.auth.iam.server.token;

import com.nimbusds.jose.JWEObject;
import com.nimbusds.jose.crypto.AESDecrypter;
import io.github.surezzzzzz.sdk.auth.iam.core.support.IamRouteKeyHelper;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.ConfigurationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.util.Base64;

/**
 * JWE Access Token 解码器。
 *
 * @author surezzzzzz
 */
public class JweJwtDecoder implements JwtDecoder {

    private final JwtDecoder delegate;
    private final byte[] encryptionKey;
    private final String expectedRouteKey;

    public JweJwtDecoder(SimpleIamServerProperties properties, JwtDecoder delegate) {
        this.delegate = delegate;
        this.encryptionKey = decodeEncryptionKey(properties.getToken().getEncryptionKey());
        this.expectedRouteKey = IamRouteKeyHelper.createRouteKey(properties.getToken().getKeyId());
    }

    /**
     * 先 AES-256 解密外层，再交内层 JWS 验签解码
     */
    @Override
    public Jwt decode(String token) throws JwtException {
        try {
            JWEObject jweObject = JWEObject.parse(token);
            if (!SimpleIamServerConstant.JWE_KEY_ENCRYPTION_ALGORITHM.equals(jweObject.getHeader().getAlgorithm().getName())
                    || !SimpleIamServerConstant.JWE_CONTENT_ENCRYPTION_ALGORITHM.equals(jweObject.getHeader().getEncryptionMethod().getName())
                    || !SimpleIamServerConstant.JWE_CONTENT_TYPE_JWT.equals(jweObject.getHeader().getContentType())
                    || !expectedRouteKey.equals(jweObject.getHeader().getKeyID())) {
                throw new JwtException("JWE Access Token 协议无效");
            }
            jweObject.decrypt(new AESDecrypter(encryptionKey));
            Jwt jwt = delegate.decode(jweObject.getPayload().toString());
            return new Jwt(token, jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getHeaders(), jwt.getClaims());
        } catch (JwtException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new JwtException("JWE Access Token 无效", ex);
        }
    }

    private byte[] decodeEncryptionKey(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new ConfigurationException(ServerErrorMessage.AES_256_KEY_NOT_CONFIGURED);
        }
        try {
            byte[] key = Base64.getDecoder().decode(value.trim());
            if (key.length != SimpleIamServerConstant.AES_256_KEY_LENGTH) {
                throw new ConfigurationException(String.format(ServerErrorMessage.AES_256_KEY_LENGTH_ERROR,
                        SimpleIamServerConstant.AES_256_KEY_LENGTH, key.length));
            }
            return key;
        } catch (IllegalArgumentException ex) {
            throw new ConfigurationException(ServerErrorMessage.AES_256_KEY_FORMAT_ERROR);
        }
    }
}
