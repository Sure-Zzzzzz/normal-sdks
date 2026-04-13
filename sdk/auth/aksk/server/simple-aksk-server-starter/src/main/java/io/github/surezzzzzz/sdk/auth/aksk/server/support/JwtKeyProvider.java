package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ConfigurationException;
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
 * 提供JWT签名所需的RSA密钥对
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class JwtKeyProvider {

    private final SimpleAkskServerProperties properties;
    private final ResourceLoader resourceLoader;

    private RSAPublicKey publicKey;
    private RSAPrivateKey privateKey;

    @PostConstruct
    public void init() {
        try {
            validateConfiguration();
            loadKeys();
            log.info("JWT keys loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load JWT keys", e);
            throw new ConfigurationException(
                    String.format(ServerErrorMessage.JWT_CONFIG_ERROR, e.getMessage()),
                    e
            );
        }
    }

    private void validateConfiguration() {
        SimpleAkskServerProperties.JwtConfig jwtConfig = properties.getJwt();

        if (jwtConfig.getPublicKey() == null || jwtConfig.getPublicKey().trim().isEmpty()) {
            throw new ConfigurationException(ServerErrorMessage.JWT_PUBLIC_KEY_NOT_CONFIGURED);
        }

        if (jwtConfig.getPrivateKey() == null || jwtConfig.getPrivateKey().trim().isEmpty()) {
            throw new ConfigurationException(ServerErrorMessage.JWT_PRIVATE_KEY_NOT_CONFIGURED);
        }
    }

    private void loadKeys() throws Exception {
        SimpleAkskServerProperties.JwtConfig jwtConfig = properties.getJwt();

        // 加载主密钥对
        this.publicKey = loadPublicKey(jwtConfig.getPublicKey());
        this.privateKey = loadPrivateKey(jwtConfig.getPrivateKey());
    }

    private RSAPublicKey loadPublicKey(String keyConfig) throws Exception {
        String keyContent = resolveKeyContent(keyConfig);
        byte[] keyBytes = decodeKey(keyContent);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(SimpleAkskServerConstant.KEY_ALGORITHM_RSA);
        return (RSAPublicKey) keyFactory.generatePublic(spec);
    }

    private RSAPrivateKey loadPrivateKey(String keyConfig) throws Exception {
        String keyContent = resolveKeyContent(keyConfig);
        byte[] keyBytes = decodeKey(keyContent);
        PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(SimpleAkskServerConstant.KEY_ALGORITHM_RSA);
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

        // 1. 检测是否是文件路径
        if (trimmed.startsWith(SimpleAkskServerConstant.KEY_PATH_PREFIX_CLASSPATH)
                || trimmed.startsWith(SimpleAkskServerConstant.KEY_PATH_PREFIX_FILE)
                || trimmed.startsWith(SimpleAkskServerConstant.KEY_PATH_PREFIX_UNIX)) {
            return loadKeyFromFile(trimmed);
        }

        // 2. 检测是否是PEM格式内容
        if (trimmed.contains(SimpleAkskServerConstant.PEM_BEGIN_MARKER)) {
            return trimmed;
        }

        // 3. 否则当作Base64处理
        return trimmed;
    }

    /**
     * 从文件加载密钥内容
     */
    private String loadKeyFromFile(String path) throws Exception {
        try {
            Resource resource = resourceLoader.getResource(path);
            if (!resource.exists()) {
                throw new ConfigurationException(String.format(ServerErrorMessage.JWT_KEY_FILE_NOT_FOUND, path));
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining(SimpleAkskServerConstant.LINE_SEPARATOR));
            }
        } catch (Exception e) {
            throw new ConfigurationException(String.format(ServerErrorMessage.JWT_KEY_FILE_LOAD_FAILED, path), e);
        }
    }

    /**
     * 解码密钥内容为字节数组
     * 支持PEM格式和Base64格式
     */
    private byte[] decodeKey(String keyContent) {
        // 移除PEM格式的头尾标记和换行符
        String base64Key = keyContent
                .replaceAll(SimpleAkskServerConstant.PEM_REGEX_BEGIN, SimpleAkskServerConstant.EMPTY_STRING)
                .replaceAll(SimpleAkskServerConstant.PEM_REGEX_END, SimpleAkskServerConstant.EMPTY_STRING)
                .replaceAll(SimpleAkskServerConstant.REGEX_WHITESPACE, SimpleAkskServerConstant.EMPTY_STRING);

        return Base64.getDecoder().decode(base64Key);
    }

    public RSAPublicKey getPublicKey() {
        return publicKey;
    }

    public RSAPrivateKey getPrivateKey() {
        return privateKey;
    }
}
