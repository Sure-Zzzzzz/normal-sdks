package io.github.surezzzzzz.sdk.auth.aksk.server.test.cases;

import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.AuthorizationSmartCacheSerializer;
import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SmartCacheAuthorizationSerializerConfiguration;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.AuthorizationSmartCacheCodec;
import io.github.surezzzzzz.sdk.cache.configuration.SmartCacheProperties;
import io.github.surezzzzzz.sdk.cache.serializer.JacksonSmartCacheSerializer;
import io.github.surezzzzzz.sdk.cache.serializer.PackageSmartCacheTypeValidator;
import io.github.surezzzzzz.sdk.cache.serializer.SmartCacheSerializer;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectOutputStream;
import java.io.StreamCorruptedException;
import java.util.Collections;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 授权对象 SmartCache 序列化回归：专用 Mapper 必须支持完整回读。
 *
 * @author surezzzzzz
 */
@Slf4j
class OAuth2AuthorizationSmartCacheSerializationTest {

    private final AnnotationConfigApplicationContext context =
            new AnnotationConfigApplicationContext(
                    OAuth2AuthorizationSmartCacheSerializationTest.TestConfiguration.class,
                    SmartCacheAuthorizationSerializerConfiguration.class);

    @AfterEach
    void closeContext() {
        context.close();
    }

    @Test
    @DisplayName("测试专用序列化器覆盖 SmartCache 默认配置")
    void shouldExposeAuthorizationSerializerAsSmartCacheSerializer() {
        SmartCacheSerializer actual = context.getBean(SmartCacheSerializer.class);

        assertThat(actual).isNotNull();
        assertThat(actual).isInstanceOf(AuthorizationSmartCacheSerializer.class);
    }

    @Test
    @DisplayName("测试 OAuth2Authorization 可经 SmartCache 载荷完整回读")
    void shouldSerializeAndDeserializeOAuth2Authorization() {
        SmartCacheSerializer serializer = context.getBean(SmartCacheSerializer.class);
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("cache-client")
                .clientSecret("{noop}cache-secret")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder().build())
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id("authorization-1")
                .principalName("principal-1")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .attribute(OAuth2ParameterNames.SCOPE, "read write")
                .attribute("authorities", Collections.singletonList(new SimpleGrantedAuthority("ROLE_TEST")))
                .build();

        String payload = serializer.serialize(authorization, OAuth2Authorization.class);
        OAuth2Authorization restored =
                (OAuth2Authorization) serializer.deserialize(payload, OAuth2Authorization.class);

        log.info("OAuth2Authorization SmartCache 载荷长度: {}", payload.length());
        assertThat(restored).isNotNull();
        assertThat(restored.getId()).isEqualTo("authorization-1");
        assertThat(restored.getPrincipalName()).isEqualTo("principal-1");
        assertThat(restored.getRegisteredClientId()).isEqualTo(client.getId());
        assertThat(restored.getAuthorizationGrantType()).isEqualTo(AuthorizationGrantType.CLIENT_CREDENTIALS);
    }

    @Test
    @DisplayName("测试非授权对象继续走 SmartCache 默认 JSON 载荷")
    void shouldDelegateNonAuthorizationValueToDefaultSerializer() {
        SmartCacheSerializer serializer = context.getBean(SmartCacheSerializer.class);

        String payload = serializer.serialize("cache-value", String.class);

        assertThat(payload).contains("\"schemaVersion\":1");
        assertThat(payload).doesNotContain("jdk-oauth2-authorization");
        assertThat(serializer.deserialize(payload, Object.class)).isEqualTo("cache-value");
    }

    @Test
    @DisplayName("测试旧 JSON 授权载荷不再走专用授权路径")
    void shouldRejectLegacyAuthorizationJsonInsteadOfReturningCorruptObject() {
        SmartCacheSerializer serializer = new AuthorizationSmartCacheSerializer(
                new JacksonSmartCacheSerializer(new ObjectMapper(),
                        new PackageSmartCacheTypeValidator(Collections.singletonList(
                                "org.springframework.security.oauth2.server.authorization"))));
        String legacyPayload = "{\"type\":\"org.springframework.security.oauth2.server.authorization."
                + "OAuth2Authorization\",\"data\":{\"id\":\"legacy\"}}";

        org.junit.jupiter.api.Assertions.assertThrows(
                Exception.class,
                () -> serializer.deserialize(legacyPayload, Object.class)
        );
    }

    @Test
    @DisplayName("测试历史裸 Java 序列化授权使用同一白名单")
    void shouldDecodeLegacyRawJavaAuthorizationWithWhitelist() throws Exception {
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("legacy-raw-client")
                .clientSecret("{noop}cache-secret")
                .clientSettings(ClientSettings.builder().requireAuthorizationConsent(false).build())
                .tokenSettings(TokenSettings.builder().build())
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(client)
                .id("legacy-raw-authorization")
                .principalName("principal-1")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();

        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(buffer)) {
            stream.writeObject(authorization);
        }

        OAuth2Authorization restored =
                AuthorizationSmartCacheCodec.decodeRawJavaSerialized(buffer.toByteArray());

        assertThat(restored.getId()).isEqualTo("legacy-raw-authorization");
    }

    @Test
    @DisplayName("测试历史裸 Java 序列化拒绝白名单外类型")
    void shouldRejectForbiddenLegacyRawJavaType() throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (ObjectOutputStream stream = new ObjectOutputStream(buffer)) {
            stream.writeObject(new File("cache-forbidden"));
        }

        assertThatThrownBy(() ->
                AuthorizationSmartCacheCodec.decodeRawJavaSerialized(buffer.toByteArray()))
                .isInstanceOf(StreamCorruptedException.class)
                .hasMessageContaining("禁止反序列化授权缓存类");
    }

    /**
     * 仅给窄上下文提供 SmartCache 配置和默认 Mapper，不启动 MVC 或数据库。
     */
    @Configuration
    @EnableConfigurationProperties(SmartCacheProperties.class)
    static class TestConfiguration {

        @Bean("smartCacheObjectMapper")
        ObjectMapper smartCacheObjectMapper() {
            return new ObjectMapper().registerModule(new JavaTimeModule());
        }
    }
}
