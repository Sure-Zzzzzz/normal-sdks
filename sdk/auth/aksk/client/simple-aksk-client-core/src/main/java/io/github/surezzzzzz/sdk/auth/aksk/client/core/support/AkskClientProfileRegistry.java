package io.github.surezzzzzz.sdk.auth.aksk.client.core.support;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorCode;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.ClientErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.model.AkskClientProfile;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * AKP Profile 只读注册表
 *
 * <p>对象不可变；构造时完成命名 Profile 校验，default 凭据允许缺省，
 * 只有真正解析 default 时才失败。
 *
 * @author surezzzzzz
 */
public final class AkskClientProfileRegistry {

    private static final Pattern PROFILE_PATTERN = Pattern.compile(SimpleAkskClientCoreConstant.PROFILE_NAME_PATTERN);
    private static final char CREDENTIAL_SEPARATOR = '\n';
    private static final int HASH_BYTES = 16;

    private final AkskClientProfile defaultProfile;
    private final Map<String, AkskClientProfile> namedProfiles;
    private final Map<String, String> credentialHashes;

    public AkskClientProfileRegistry(AkskClientProfile defaultProfile, Map<String, AkskClientProfile> namedProfiles) {
        this.defaultProfile = defaultProfile;
        this.namedProfiles = namedProfiles == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(namedProfiles));
        this.credentialHashes = buildCredentialHashes();
        validateUniqueCredentials();
    }

    /**
     * 从 Properties 构建不可变注册表
     *
     * @param properties AKSK Client 全局配置
     * @return Profile 注册表
     */
    public static AkskClientProfileRegistry fromProperties(SimpleAkskClientCoreProperties properties) {
        AkskClientProfile defaultProfile = buildDefaultProfile(properties);
        Map<String, AkskClientProfile> namedProfiles = new LinkedHashMap<>();
        if (properties.getProfiles() != null) {
            properties.getProfiles().forEach((name, config) ->
                    namedProfiles.put(name, buildNamedProfile(name, config)));
        }
        return new AkskClientProfileRegistry(defaultProfile, namedProfiles);
    }

    private static AkskClientProfile buildDefaultProfile(SimpleAkskClientCoreProperties properties) {
        if (!StringUtils.hasText(properties.getServerUrl())
                || !StringUtils.hasText(properties.getClientId())
                || !StringUtils.hasText(properties.getClientSecret())) {
            return null;
        }
        validateNoControlCharacter(
                SimpleAkskClientCoreConstant.DEFAULT_PROFILE,
                properties.getServerUrl(),
                properties.getClientId(),
                properties.getClientSecret());
        validateOrigin(properties.getServerUrl());
        return new AkskClientProfile(
                SimpleAkskClientCoreConstant.DEFAULT_PROFILE,
                properties.getServerUrl(),
                properties.getTokenEndpoint(),
                properties.getClientId(),
                properties.getClientSecret(),
                properties.getHttp().getConnectTimeoutMs(),
                properties.getHttp().getReadTimeoutMs());
    }

    private static AkskClientProfile buildNamedProfile(String name, SimpleAkskClientCoreProperties.ProfileConfig config) {
        validateProfileName(name);
        if (config == null || !StringUtils.hasText(config.getServerUrl())
                || !StringUtils.hasText(config.getClientId())
                || !StringUtils.hasText(config.getClientSecret())) {
            throw new ConfigurationException(
                    ClientErrorCode.CONFIG_MISSING,
                    String.format(ClientErrorMessage.CONFIG_MISSING, "AKP Profile: " + name));
        }
        validateNoControlCharacter(name, config.getServerUrl(), config.getClientId(), config.getClientSecret());
        validateOrigin(config.getServerUrl());
        return new AkskClientProfile(
                name,
                config.getServerUrl(),
                config.getTokenEndpoint(),
                config.getClientId(),
                config.getClientSecret(),
                config.getHttp().getConnectTimeoutMs(),
                config.getHttp().getReadTimeoutMs());
    }

    private static void validateProfileName(String name) {
        if (SimpleAkskClientCoreConstant.DEFAULT_PROFILE.equals(name) || !PROFILE_PATTERN.matcher(name).matches()) {
            throw new ConfigurationException(
                    ClientErrorCode.CONFIG_INVALID,
                    String.format(ClientErrorMessage.CONFIG_INVALID, "AKP Profile 名称: " + name));
        }
    }

    private static void validateNoControlCharacter(String name, String serverUrl, String clientId, String clientSecret) {
        if (hasControlCharacter(name) || hasControlCharacter(serverUrl)
                || hasControlCharacter(clientId) || hasControlCharacter(clientSecret)) {
            throw new ConfigurationException(
                    ClientErrorCode.CONFIG_INVALID,
                    String.format(ClientErrorMessage.CONFIG_INVALID, "AKP Profile: " + name));
        }
    }

    private static boolean hasControlCharacter(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char current = value.charAt(i);
            if (current < 0x20 || current == 0x7F) {
                return true;
            }
        }
        return false;
    }

    private static java.net.URI validateOrigin(String serverUrl) {
        java.net.URI source;
        try {
            source = java.net.URI.create(serverUrl.trim());
        } catch (Exception exception) {
            throw invalidOrigin(serverUrl, exception);
        }
        if (!"http".equalsIgnoreCase(source.getScheme()) && !"https".equalsIgnoreCase(source.getScheme())
                || !StringUtils.hasText(source.getHost())
                || source.getUserInfo() != null
                || (source.getPath() != null && !source.getPath().isEmpty() && !"/".equals(source.getPath()))
                || source.getQuery() != null
                || source.getFragment() != null) {
            throw invalidOrigin(serverUrl, null);
        }
        return source;
    }

    private static String computeCredentialHash(AkskClientProfile profile) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] source = (normalizeServerUrl(profile.getServerUrl())
                    + CREDENTIAL_SEPARATOR + profile.getClientId()).getBytes(StandardCharsets.UTF_8);
            return toHex(digest.digest(source), HASH_BYTES);
        } catch (NoSuchAlgorithmException exception) {
            throw new ConfigurationException(
                    ClientErrorCode.CONFIG_INVALID,
                    String.format(ClientErrorMessage.CONFIG_INVALID, "AKP 凭据身份哈希算法"),
                    exception);
        }
    }

    private static String normalizeServerUrl(String serverUrl) {
        java.net.URI source = validateOrigin(serverUrl);
        String scheme = source.getScheme().toLowerCase();
        String host = source.getHost().toLowerCase();
        int port = source.getPort();
        if (("http".equals(scheme) && port == 80) || ("https".equals(scheme) && port == 443)) {
            port = -1;
        }
        try {
            return new java.net.URI(scheme, null, host, port, null, null, null).toString();
        } catch (java.net.URISyntaxException exception) {
            throw invalidOrigin(serverUrl, exception);
        }
    }

    private static ConfigurationException invalidOrigin(String serverUrl, Exception cause) {
        return new ConfigurationException(
                ClientErrorCode.CONFIG_INVALID,
                String.format(ClientErrorMessage.CONFIG_INVALID, "AKSK Server origin"),
                cause);
    }

    private static String toHex(byte[] bytes, int length) {
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder result = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            result.append(hex[(bytes[i] >>> 4) & 0x0f]);
            result.append(hex[bytes[i] & 0x0f]);
        }
        return result.toString();
    }

    /**
     * 判断 Profile 是否已配置
     *
     * @param profile Profile 名称
     * @return true 表示已配置
     */
    public boolean contains(String profile) {
        if (SimpleAkskClientCoreConstant.DEFAULT_PROFILE.equals(profile)) {
            return defaultProfile != null;
        }
        return profile != null && namedProfiles.containsKey(profile);
    }

    /**
     * 解析 Profile
     *
     * @param profile Profile 名称
     * @return 不可变 Profile
     */
    public AkskClientProfile resolve(String profile) {
        if (!StringUtils.hasText(profile) || SimpleAkskClientCoreConstant.DEFAULT_PROFILE.equals(profile)) {
            return requireDefault();
        }
        AkskClientProfile target = namedProfiles.get(profile);
        if (target == null) {
            throw new ConfigurationException(
                    ClientErrorCode.CONFIG_MISSING,
                    String.format(ClientErrorMessage.CONFIG_MISSING, "AKP Profile: " + profile));
        }
        return target;
    }

    /**
     * 获取凭据身份哈希
     *
     * @param profile 已解析 Profile
     * @return SHA-256 截断 128-bit 的 hex 字符串
     */
    public String credentialHash(AkskClientProfile profile) {
        String hash = credentialHashes.get(identityKey(profile));
        if (hash == null) {
            throw new ConfigurationException(
                    ClientErrorCode.CONFIG_MISSING,
                    String.format(ClientErrorMessage.CONFIG_MISSING, "AKP Profile: " + profile.getProfile()));
        }
        return hash;
    }

    private AkskClientProfile requireDefault() {
        if (defaultProfile == null) {
            throw new ConfigurationException(
                    ClientErrorCode.CONFIG_MISSING,
                    String.format(ClientErrorMessage.CONFIG_MISSING, "default AKP Profile"));
        }
        return defaultProfile;
    }

    private Map<String, String> buildCredentialHashes() {
        Map<String, String> hashes = new LinkedHashMap<>();
        if (defaultProfile != null) {
            hashes.put(identityKey(defaultProfile), computeCredentialHash(defaultProfile));
        }
        namedProfiles.values().forEach(profile -> hashes.put(identityKey(profile), computeCredentialHash(profile)));
        return Collections.unmodifiableMap(hashes);
    }

    private void validateUniqueCredentials() {
        Map<String, String> owners = new LinkedHashMap<>();
        if (defaultProfile != null) {
            owners.put(identityKey(defaultProfile), defaultProfile.getProfile());
        }
        namedProfiles.values().forEach(profile -> {
            String identity = identityKey(profile);
            if (owners.containsKey(identity)) {
                throw new ConfigurationException(
                        ClientErrorCode.CONFIG_INVALID,
                        String.format(ClientErrorMessage.CONFIG_INVALID,
                                "重复 AKP Profile: " + owners.get(identity) + ", " + profile.getProfile()));
            }
            owners.put(identity, profile.getProfile());
        });
    }

    private String identityKey(AkskClientProfile profile) {
        return normalizeServerUrl(profile.getServerUrl()) + CREDENTIAL_SEPARATOR + profile.getClientId();
    }
}
