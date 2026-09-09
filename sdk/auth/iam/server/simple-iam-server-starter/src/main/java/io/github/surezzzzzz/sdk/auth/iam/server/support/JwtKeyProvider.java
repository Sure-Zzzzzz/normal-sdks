package io.github.surezzzzzz.sdk.auth.iam.server.support;

import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.ConfigurationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.stream.Collectors;

/**
 * JWT Key Provider
 * 提供 JWT 签名所需的 RSA 密钥对（JWS RS256）
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class JwtKeyProvider {

    private final SimpleIamServerProperties properties;
    private final ResourceLoader resourceLoader;

    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;

    /**
     * 启动期加载并校验 RSA 密钥对（PEM 解析，失败快速终止）
     */
    @PostConstruct
    public void init() {
        try {
            validateConfiguration();
            loadKeys();
            log.info("IAM JWT keys loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load IAM JWT keys", e);
            throw new ConfigurationException(
                    String.format(ServerErrorMessage.JWT_CONFIG_ERROR, e.getMessage()),
                    e
            );
        }
    }

    private void validateConfiguration() {
        SimpleIamServerProperties.TokenConfig tokenConfig = properties.getToken();

        if (tokenConfig.getPublicKey() == null || tokenConfig.getPublicKey().trim().isEmpty()) {
            throw new ConfigurationException(ServerErrorMessage.JWT_PUBLIC_KEY_NOT_CONFIGURED);
        }

        if (tokenConfig.getPrivateKey() == null || tokenConfig.getPrivateKey().trim().isEmpty()) {
            throw new ConfigurationException(ServerErrorMessage.JWT_PRIVATE_KEY_NOT_CONFIGURED);
        }
    }

    private void loadKeys() throws Exception {
        SimpleIamServerProperties.TokenConfig tokenConfig = properties.getToken();
        this.publicKey = loadPublicKey(tokenConfig.getPublicKey());
        this.privateKey = loadPrivateKey(tokenConfig.getPrivateKey());
    }

    private RSAPublicKey loadPublicKey(String keyConfig) throws Exception {
        String keyContent = resolveKeyContent(keyConfig);
        byte[] keyBytes = decodeKey(keyContent);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(SimpleIamServerConstant.KEY_ALGORITHM_RSA);
        return (RSAPublicKey) keyFactory.generatePublic(spec);
    }

    private RSAPrivateKey loadPrivateKey(String keyConfig) throws Exception {
        String keyContent = resolveKeyContent(keyConfig);
        byte[] keyBytes = decodeKey(keyContent);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(SimpleIamServerConstant.KEY_ALGORITHM_RSA);
        return (RSAPrivateKey) keyFactory.generatePrivate(spec);
    }

    /**
     * 解析密钥配置内容
     * 支持三种格式：
     * 1. 文件路径：classpath:keys/public.pem 或 file:/etc/keys/public.pem
     * 2. PEM内容：-----BEGIN PUBLIC KEY-----...
     * 3. Base64编码：MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8A...
     */
    private String resolveKeyContent(String keyConfig) throws Exception {
        if (keyConfig == null || keyConfig.trim().isEmpty()) {
            throw new ConfigurationException(ServerErrorMessage.JWT_KEY_CONFIG_EMPTY);
        }

        String trimmed = keyConfig.trim();

        if (trimmed.startsWith(SimpleIamServerConstant.KEY_PATH_PREFIX_CLASSPATH)
                || trimmed.startsWith(SimpleIamServerConstant.KEY_PATH_PREFIX_FILE)
                || trimmed.startsWith(SimpleIamServerConstant.KEY_PATH_PREFIX_UNIX)) {
            return loadKeyFromFile(trimmed);
        }

        if (trimmed.contains(SimpleIamServerConstant.PEM_BEGIN_MARKER)) {
            return trimmed;
        }

        return trimmed;
    }

    private String loadKeyFromFile(String path) throws Exception {
        try {
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                throw new ConfigurationException(String.format(ServerErrorMessage.JWT_KEY_FILE_NOT_FOUND, path));
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining(SimpleIamServerConstant.LINE_SEPARATOR));
            }
        } catch (Exception e) {
            throw new ConfigurationException(String.format(ServerErrorMessage.JWT_KEY_FILE_LOAD_FAILED, path), e);
        }
    }

    private byte[] decodeKey(String keyContent) {
        String base64Key = keyContent
                .replaceAll(SimpleIamServerConstant.PEM_REGEX_BEGIN, SimpleIamServerConstant.EMPTY_STRING)
                .replaceAll(SimpleIamServerConstant.PEM_REGEX_END, SimpleIamServerConstant.EMPTY_STRING)
                .replaceAll(SimpleIamServerConstant.REGEX_WHITESPACE, SimpleIamServerConstant.EMPTY_STRING);

        return Base64.getDecoder().decode(base64Key);
    }

    /**
     * 获取 RSA 公钥
     *
     * @return RSA 公钥
     */
    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    /**
     * 获取 RSA 私钥
     *
     * @return RSA 私钥
     */
    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }
}
