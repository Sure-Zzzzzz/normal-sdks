package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.aksk.server.support.AuthorizationSmartCacheCodec;
import io.github.surezzzzzz.sdk.cache.constant.ErrorCode;
import io.github.surezzzzzz.sdk.cache.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.cache.exception.CacheSerializationException;
import io.github.surezzzzzz.sdk.cache.serializer.SmartCacheSerializer;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;

import java.nio.charset.StandardCharsets;

/**
 * 授权对象专用缓存序列化器。
 *
 * <p>OAuth2Authorization 走 {@link AuthorizationSmartCacheCodec}；其余缓存值继续走
 * SmartCache 默认 JSON 序列化器，保证既有 L2 载荷格式不变。</p>
 *
 * @author surezzzzzz
 */
public final class AuthorizationSmartCacheSerializer implements SmartCacheSerializer {

    private final SmartCacheSerializer defaultSerializer;

    /**
     * 创建授权对象序列化器。
     *
     * @param defaultSerializer 非 OAuth2Authorization 缓存值的默认序列化器
     */
    public AuthorizationSmartCacheSerializer(SmartCacheSerializer defaultSerializer) {
        this.defaultSerializer = defaultSerializer;
    }

    @Override
    public String serialize(Object value, Class<?> declaredType) {
        if (value == null) {
            return null;
        }
        if (!(value instanceof OAuth2Authorization)) {
            return defaultSerializer.serialize(value, declaredType);
        }
        try {
            return AuthorizationSmartCacheCodec.encode((OAuth2Authorization) value);
        } catch (Exception failure) {
            throw new CacheSerializationException(
                    ErrorCode.SMART_CACHE_SERIALIZATION_FAILED,
                    String.format(ErrorMessage.SMART_CACHE_SERIALIZATION_FAILED, value.getClass().getName()),
                    failure
            );
        }
    }

    @Override
    public Object deserialize(String payload, Class<?> expectedType) {
        if (payload == null || payload.trim().isEmpty()) {
            return null;
        }
        byte[] rawBytes = payload.getBytes(StandardCharsets.UTF_8);
        if (AuthorizationSmartCacheCodec.isEnvelope(rawBytes)) {
            try {
                return AuthorizationSmartCacheCodec.decodeEnvelope(rawBytes);
            } catch (Exception failure) {
                throw new CacheSerializationException(
                        ErrorCode.SMART_CACHE_SERIALIZATION_FAILED,
                        String.format(ErrorMessage.SMART_CACHE_SERIALIZATION_FAILED, OAuth2Authorization.class),
                        failure
                );
            }
        }
        if (isLegacyAuthorizationJsonPayload(rawBytes)) {
            throw new CacheSerializationException(
                    ErrorCode.SMART_CACHE_SERIALIZATION_FAILED,
                    String.format(ErrorMessage.SMART_CACHE_SERIALIZATION_FAILED, OAuth2Authorization.class)
            );
        }
        return defaultSerializer.deserialize(payload, expectedType);
    }

    /**
     * 2.2.0 及更早的授权 JSON 载荷已经证明读回不稳定；命中时必须失效回源。
     */
    private boolean isLegacyAuthorizationJsonPayload(byte[] rawBytes) {
        try {
            JsonNode root = new ObjectMapper().readTree(rawBytes);
            return root.isObject() && !root.hasNonNull("codec")
                    && OAuth2Authorization.class.getName().equals(root.path("type").asText(null));
        } catch (Exception ignored) {
            return false;
        }
    }
}
