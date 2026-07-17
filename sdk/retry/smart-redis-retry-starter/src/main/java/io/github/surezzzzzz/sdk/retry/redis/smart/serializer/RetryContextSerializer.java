package io.github.surezzzzzz.sdk.retry.redis.smart.serializer;

import java.util.Map;

/**
 * 重试上下文序列化器
 *
 * @author surezzzzzz
 */
public interface RetryContextSerializer {

    /**
     * 序列化重试上下文
     *
     * @param context 重试上下文
     * @return 上下文 JSON
     */
    String serialize(Map<String, Object> context);

    /**
     * 反序列化重试上下文
     *
     * @param contextJson 上下文 JSON
     * @return 重试上下文
     */
    Map<String, Object> deserialize(String contextJson);
}
