package io.github.surezzzzzz.sdk.cache.serializer;

/**
 * Smart Cache 序列化器
 *
 * @author surezzzzzz
 */
public interface SmartCacheSerializer {

    /**
     * 序列化缓存值
     *
     * @param value        缓存值
     * @param declaredType 声明类型
     * @return payload
     */
    String serialize(Object value, Class<?> declaredType);

    /**
     * 反序列化缓存值
     *
     * @param payload      payload
     * @param expectedType 期望类型
     * @return 缓存值
     */
    Object deserialize(String payload, Class<?> expectedType);
}
