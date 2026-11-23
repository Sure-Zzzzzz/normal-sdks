package io.github.surezzzzzz.sdk.auth.aksk.client.core.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.client.core.configuration.SimpleAkskClientCoreProperties;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.constant.SimpleAkskClientCoreConstant;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.exception.ConfigurationException;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.model.AkskClientProfile;
import io.github.surezzzzzz.sdk.auth.aksk.client.core.support.AkskClientProfileRegistry;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AKP Profile 注册表配置校验测试。
 *
 * @author surezzzzzz
 */
@Slf4j
class AkskClientProfileRegistryTest {

    private static SimpleAkskClientCoreProperties.ProfileConfig profile(
            String serverUrl, String clientId, String clientSecret) {
        SimpleAkskClientCoreProperties.ProfileConfig config = new SimpleAkskClientCoreProperties.ProfileConfig();
        config.setServerUrl(serverUrl);
        config.setTokenEndpoint(SimpleAkskClientCoreConstant.DEFAULT_TOKEN_ENDPOINT);
        config.setClientId(clientId);
        config.setClientSecret(clientSecret);
        return config;
    }

    private static SimpleAkskClientCoreProperties propertiesWithProfiles(String profileName,
                                                                         String serverUrl,
                                                                         String clientId,
                                                                         String clientSecret) {
        SimpleAkskClientCoreProperties properties = new SimpleAkskClientCoreProperties();
        Map<String, SimpleAkskClientCoreProperties.ProfileConfig> profiles = new LinkedHashMap<>();
        profiles.put(profileName, profile(serverUrl, clientId, clientSecret));
        properties.setProfiles(profiles);
        return properties;
    }

    @Test
    @DisplayName("Profile-only 配置应解析命名 Profile，且默认 Profile 允许缺省")
    void shouldResolveNamedProfileWhenDefaultCredentialIsAbsent() {
        SimpleAkskClientCoreProperties properties =
                propertiesWithProfiles("crm", "https://aksk.example.test", "crm-client", "crm-secret");
        AkskClientProfileRegistry registry = AkskClientProfileRegistry.fromProperties(properties);

        AkskClientProfile resolved = registry.resolve("crm");
        log.info("Profile 解析结果: profile={}, serverUrl={}, clientId={}, secretLen={}",
                resolved.getProfile(), resolved.getServerUrl(), resolved.getClientId(),
                resolved.getClientSecret().length());

        assertTrue(registry.contains("crm"));
        assertFalse(registry.contains(SimpleAkskClientCoreConstant.DEFAULT_PROFILE));
        assertEquals("crm", resolved.getProfile());
        assertEquals("https://aksk.example.test", resolved.getServerUrl());
        assertEquals("crm-client", resolved.getClientId());
        assertEquals("/oauth2/token", resolved.getTokenEndpoint());
        assertNotNull(registry.credentialHash(resolved));
        assertThrows(ConfigurationException.class, () -> registry.resolve("feedback"));
    }

    @Test
    @DisplayName("default 是保留名，非法 Profile 名称和缺失凭据都应启动失败")
    void shouldRejectReservedMissingOrInvalidProfileConfiguration() {
        SimpleAkskClientCoreProperties reserved = propertiesWithProfiles(
                SimpleAkskClientCoreConstant.DEFAULT_PROFILE, "https://aksk.example.test", "client", "secret");
        SimpleAkskClientCoreProperties invalidName = propertiesWithProfiles(
                "1crm", "https://aksk.example.test", "client", "secret");
        SimpleAkskClientCoreProperties missingSecret = propertiesWithProfiles(
                "crm", "https://aksk.example.test", "client", "");

        assertThrows(ConfigurationException.class, () -> AkskClientProfileRegistry.fromProperties(reserved));
        assertThrows(ConfigurationException.class, () -> AkskClientProfileRegistry.fromProperties(invalidName));
        assertThrows(ConfigurationException.class, () -> AkskClientProfileRegistry.fromProperties(missingSecret));
    }

    @Test
    @DisplayName("同一 Server 重复 clientId 应失败，不同 Server 同名 clientId 应允许并存")
    void shouldRejectDuplicateIdentityButAllowSameClientOnDifferentServers() {
        SimpleAkskClientCoreProperties duplicate = new SimpleAkskClientCoreProperties();
        duplicate.setServerUrl("https://aksk.example.test");
        duplicate.setTokenEndpoint(SimpleAkskClientCoreConstant.DEFAULT_TOKEN_ENDPOINT);
        duplicate.setClientId("shared-client");
        duplicate.setClientSecret("default-secret");
        Map<String, SimpleAkskClientCoreProperties.ProfileConfig> profiles = new LinkedHashMap<>();
        profiles.put("first", profile("https://AKSK.example.test/", "shared-client", "first-secret"));
        profiles.put("second", profile("https://aksk.example.test:443", "shared-client", "second-secret"));
        duplicate.setProfiles(profiles);
        assertThrows(ConfigurationException.class, () -> AkskClientProfileRegistry.fromProperties(duplicate));

        SimpleAkskClientCoreProperties distinct = new SimpleAkskClientCoreProperties();
        distinct.setServerUrl("http://aksk-default.example.test");
        distinct.setTokenEndpoint(SimpleAkskClientCoreConstant.DEFAULT_TOKEN_ENDPOINT);
        distinct.setClientId("shared-client");
        distinct.setClientSecret("default-secret");
        Map<String, SimpleAkskClientCoreProperties.ProfileConfig> distinctProfiles = new LinkedHashMap<>();
        distinctProfiles.put("one", profile("http://aksk-one.example.test:80", "shared-client", "one-secret"));
        distinctProfiles.put("two", profile("http://aksk-two.example.test", "shared-client", "two-secret"));
        distinct.setProfiles(distinctProfiles);
        AkskClientProfileRegistry registry = AkskClientProfileRegistry.fromProperties(distinct);

        assertTrue(registry.contains("one"));
        assertTrue(registry.contains("two"));
        assertFalse(registry.credentialHash(registry.resolve(SimpleAkskClientCoreConstant.DEFAULT_PROFILE))
                .equals(registry.credentialHash(registry.resolve("one"))));
        assertFalse(registry.credentialHash(registry.resolve("one"))
                .equals(registry.credentialHash(registry.resolve("two"))));
    }

    @Test
    @DisplayName("Profile 模型 toString 不包含 client-secret")
    void shouldExcludeClientSecretFromProfileToString() {
        SimpleAkskClientCoreProperties properties =
                propertiesWithProfiles("crm", "https://aksk.example.test", "crm-client", "super-secret");
        AkskClientProfile profile = AkskClientProfileRegistry.fromProperties(properties).resolve("crm");

        log.info("Profile 安全 toString: {}", profile);
        assertFalse(profile.toString().contains("super-secret"));
        assertFalse(profile.toString().contains("clientSecret=super-secret"));
    }

    @Test
    @DisplayName("Server URL 只允许 http/https origin，拒绝路径、查询和用户信息")
    void shouldRejectServerUrlThatIsNotOrigin() {
        String[] invalidUrls = {
                "https://aksk.example.test/base",
                "https://aksk.example.test?timeout=5",
                "https://user@aksk.example.test",
                "https://aksk.example.test#fragment",
                "ftp://aksk.example.test"
        };
        for (String serverUrl : invalidUrls) {
            SimpleAkskClientCoreProperties properties =
                    propertiesWithProfiles("crm", serverUrl, "crm-client", "crm-secret");
            assertThrows(ConfigurationException.class,
                    () -> AkskClientProfileRegistry.fromProperties(properties),
                    "应拒绝非法 Server URL: " + serverUrl);
        }
    }
}
