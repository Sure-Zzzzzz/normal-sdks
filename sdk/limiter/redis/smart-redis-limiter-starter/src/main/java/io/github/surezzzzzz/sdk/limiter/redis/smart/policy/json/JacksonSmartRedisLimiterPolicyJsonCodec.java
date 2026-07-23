package io.github.surezzzzzz.sdk.limiter.redis.smart.policy.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.starter.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;

import java.io.IOException;
import java.io.InputStream;

/**
 * 基于独立 Jackson ObjectMapper 的远程策略 JSON 编解码器
 *
 * @author surezzzzzz
 */
public class JacksonSmartRedisLimiterPolicyJsonCodec implements SmartRedisLimiterPolicyJsonCodec {

    /**
     * SDK 独立 JSON 映射器
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建独立 JSON 编解码器
     */
    public JacksonSmartRedisLimiterPolicyJsonCodec() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.objectMapper.enable(DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES);
        this.objectMapper.enable(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES);
        this.objectMapper.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
    }

    /**
     * 从受限输入流解析完整策略快照
     *
     * @param inputStream JSON 输入流
     * @return 完整策略快照
     */
    @Override
    public SmartRedisLimiterPolicySnapshot decode(InputStream inputStream) {
        try {
            return objectMapper.readValue(inputStream, SmartRedisLimiterPolicySnapshot.class);
        } catch (IOException | RuntimeException ex) {
            throw new SmartRedisLimiterException(
                    ErrorCode.POLICY_JSON_INVALID,
                    String.format(ErrorMessage.POLICY_JSON_INVALID, ex.getMessage()),
                    ex);
        }
    }
}
