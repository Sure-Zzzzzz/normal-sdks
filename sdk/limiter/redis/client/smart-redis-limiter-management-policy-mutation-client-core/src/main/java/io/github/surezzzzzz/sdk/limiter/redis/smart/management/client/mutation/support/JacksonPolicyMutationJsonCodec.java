package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.mutation.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;

import java.io.IOException;
import java.io.InputStream;

/**
 * 基于独立 Jackson ObjectMapper 的变更编解码实现（SDK 自有 mapper，禁注入宿主 Bean；
 * 未知字段严格拒绝）
 *
 * @author surezzzzzz
 */
public class JacksonPolicyMutationJsonCodec implements PolicyMutationJsonCodec {

    private final ObjectMapper objectMapper;

    public JacksonPolicyMutationJsonCodec() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    @Override
    public TypedRuleWire.MutationWire decodeMutation(InputStream inputStream) {
        try {
            return objectMapper.readValue(inputStream, TypedRuleWire.MutationWire.class);
        } catch (IOException | RuntimeException ex) {
            throw new SmartRedisLimiterException(
                    ErrorCode.MANAGEMENT_PAYLOAD_INVALID,
                    String.format(ErrorMessage.MANAGEMENT_PAYLOAD_INVALID, ex.getMessage()),
                    ex);
        }
    }

    @Override
    public TypedRuleWire.PageWire decodePage(InputStream inputStream) {
        try {
            return objectMapper.readValue(inputStream, TypedRuleWire.PageWire.class);
        } catch (IOException | RuntimeException ex) {
            throw new SmartRedisLimiterException(
                    ErrorCode.MANAGEMENT_PAYLOAD_INVALID,
                    String.format(ErrorMessage.MANAGEMENT_PAYLOAD_INVALID, ex.getMessage()),
                    ex);
        }
    }

    @Override
    public TypedRuleWire decodeRule(InputStream inputStream) {
        try {
            return objectMapper.readValue(inputStream, TypedRuleWire.class);
        } catch (IOException | RuntimeException ex) {
            throw new SmartRedisLimiterException(
                    ErrorCode.MANAGEMENT_PAYLOAD_INVALID,
                    String.format(ErrorMessage.MANAGEMENT_PAYLOAD_INVALID, ex.getMessage()),
                    ex);
        }
    }

    @Override
    public String encode(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException | RuntimeException ex) {
            throw new SmartRedisLimiterException(
                    ErrorCode.MANAGEMENT_PAYLOAD_INVALID,
                    String.format(ErrorMessage.MANAGEMENT_PAYLOAD_INVALID, ex.getMessage()),
                    ex);
        }
    }
}
