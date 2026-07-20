package io.github.surezzzzzz.sdk.cache.serializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.cache.constant.ErrorCode;
import io.github.surezzzzzz.sdk.cache.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.cache.exception.CacheSerializationException;

/**
 * Jackson Smart Cache 序列化器
 *
 * @author surezzzzzz
 */
public class JacksonSmartCacheSerializer implements SmartCacheSerializer {

    private final ObjectMapper objectMapper;
    private final SmartCacheTypeValidator typeValidator;

    public JacksonSmartCacheSerializer(ObjectMapper objectMapper, SmartCacheTypeValidator typeValidator) {
        this.objectMapper = objectMapper;
        this.typeValidator = typeValidator;
    }

    @Override
    public String serialize(Object value, Class<?> declaredType) {
        if (value == null) {
            return null;
        }
        try {
            SmartCachePayload payload = new SmartCachePayload();
            Class<?> valueType = declaredType == null || Object.class.equals(declaredType) ? value.getClass() : declaredType;
            payload.setType(valueType.getName());
            payload.setData(value);
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new CacheSerializationException(
                    ErrorCode.SMART_CACHE_SERIALIZATION_FAILED,
                    String.format(ErrorMessage.SMART_CACHE_SERIALIZATION_FAILED, value.getClass().getName()),
                    e
            );
        }
    }

    @Override
    public Object deserialize(String payload, Class<?> expectedType) {
        if (payload == null || payload.trim().isEmpty()) {
            return null;
        }
        try {
            SmartCachePayload cachePayload = objectMapper.readValue(payload, SmartCachePayload.class);
            Class<?> targetType = resolveTargetType(cachePayload, expectedType);
            if (targetType == null) {
                return cachePayload.getData();
            }
            return objectMapper.convertValue(cachePayload.getData(), targetType);
        } catch (CacheSerializationException e) {
            throw e;
        } catch (Exception e) {
            throw new CacheSerializationException(
                    ErrorCode.SMART_CACHE_SERIALIZATION_FAILED,
                    String.format(ErrorMessage.SMART_CACHE_SERIALIZATION_FAILED, expectedType),
                    e
            );
        }
    }

    private Class<?> resolveTargetType(SmartCachePayload payload, Class<?> expectedType) throws ClassNotFoundException {
        if (expectedType != null && !Object.class.equals(expectedType)) {
            return expectedType;
        }
        String typeName = payload.getType();
        if (typeName == null || typeName.trim().isEmpty()) {
            return null;
        }
        if (!typeValidator.isTrusted(typeName)) {
            throw new CacheSerializationException(
                    ErrorCode.SMART_CACHE_SERIALIZATION_FAILED,
                    String.format(ErrorMessage.SMART_CACHE_SERIALIZATION_FAILED, typeName)
            );
        }
        return Class.forName(typeName);
    }
}
