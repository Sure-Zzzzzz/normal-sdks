package io.github.surezzzzzz.sdk.auth.aksk.server.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.surezzzzzz.sdk.auth.aksk.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.auth.aksk.core.exception.AkskException;
import io.github.surezzzzzz.sdk.auth.aksk.server.exception.ConfigurationException;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;

/**
 * OAuth2 Settings Helper
 * 用于序列化 ClientSettings 和 TokenSettings 为 Spring Authorization Server 期望的 JSON 格式
 *
 * @author surezzzzzz
 */
public final class OAuth2SettingsHelper {

    private OAuth2SettingsHelper() {
        throw new AkskException(ErrorMessage.UTILITY_CLASS_INSTANTIATION);
    }

    /**
     * 配置了类型信息的 ObjectMapper
     * 用于序列化时包含 @class 类型标识符
     */
    private static final ObjectMapper OBJECT_MAPPER = createObjectMapper();

    /**
     * 创建配置了类型信息的 ObjectMapper
     */
    private static ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();

        // 注册 JavaTimeModule 以支持 Java 8 时间类型（如 Duration）
        mapper.registerModule(new JavaTimeModule());

        // 配置多态类型验证器
        BasicPolymorphicTypeValidator validator = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();

        // 激活默认类型信息，使序列化的 JSON 包含 @class 属性
        mapper.activateDefaultTyping(
                validator,
                ObjectMapper.DefaultTyping.NON_FINAL
        );

        return mapper;
    }

    /**
     * 生成默认的 ClientSettings JSON 字符串
     *
     * @return ClientSettings 的 JSON 表示
     */
    public static String getDefaultClientSettingsJson() {
        try {
            ClientSettings clientSettings = ClientSettings.builder()
                    .requireProofKey(false)
                    .requireAuthorizationConsent(false)
                    .build();

            return OBJECT_MAPPER.writeValueAsString(clientSettings.getSettings());
        } catch (JsonProcessingException e) {
            throw new ConfigurationException(ErrorMessage.CLIENT_SETTINGS_SERIALIZE_FAILED, e);
        }
    }

    /**
     * 生成TokenSettings JSON 字符串
     *
     * @param expiresInSeconds Token过期时间（秒）
     * @return TokenSettings 的 JSON 表示
     */
    public static String getTokenSettingsJson(int expiresInSeconds) {
        try {
            TokenSettings tokenSettings = TokenSettings.builder()
                    .accessTokenTimeToLive(Duration.ofSeconds(expiresInSeconds))
                    .build();

            return OBJECT_MAPPER.writeValueAsString(tokenSettings.getSettings());
        } catch (JsonProcessingException e) {
            throw new ConfigurationException(ErrorMessage.TOKEN_SETTINGS_SERIALIZE_FAILED, e);
        }
    }
}
