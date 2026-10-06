package io.github.surezzzzzz.sdk.limiter.redis.smart.management.client.support;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicySnapshot;

import java.io.IOException;
import java.io.InputStream;

/**
 * 基于独立 Jackson ObjectMapper 的策略快照 JSON 编解码器
 * <p>SDK 自有 ObjectMapper（禁注入宿主 Bean）；严格拒绝未知字段；协议版本判别由
 * 快照模型的 schemaVersion 等值校验承担（v1 快照解析不出 v2 内容，反之亦然，
 * 构造即整份拒绝）；可空字段（customType/objectId）缺省或 null 均归一为 null，
 * 必填字段缺失由必填参数为 null 时的构造校验兜底拒绝。</p>
 *
 * @author surezzzzzz
 */
public class JacksonSmartRedisLimiterManagementJsonCodec implements SmartRedisLimiterManagementJsonCodec {

    /**
     * SDK 独立 JSON 映射器
     */
    private final ObjectMapper objectMapper;

    /**
     * 创建独立 JSON 编解码器
     */
    public JacksonSmartRedisLimiterManagementJsonCodec() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
        this.objectMapper.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        this.objectMapper.disable(DeserializationFeature.READ_DATE_TIMESTAMPS_AS_NANOSECONDS);
    }

    /**
     * 从受限输入流解析 v1 三元组策略快照
     *
     * @param inputStream JSON 输入流
     * @return 完整策略快照
     */
    @Override
    public SmartRedisLimiterPolicySnapshot decodePolicy(InputStream inputStream) {
        try {
            return objectMapper.readValue(inputStream, SmartRedisLimiterPolicySnapshot.class);
        } catch (IOException | RuntimeException ex) {
            throw new SmartRedisLimiterException(
                    ErrorCode.POLICY_SNAPSHOT_INVALID,
                    String.format(ErrorMessage.POLICY_SNAPSHOT_INVALID, ex.getMessage()),
                    ex);
        }
    }

    /**
     * 从受限输入流解析 v2 类型化策略快照
     *
     * @param inputStream JSON 输入流
     * @return 完整类型化策略快照
     */
    @Override
    public SmartRedisLimiterTypedPolicySnapshot decodeTypedPolicy(InputStream inputStream) {
        try {
            return objectMapper.readValue(inputStream, SmartRedisLimiterTypedPolicySnapshot.class);
        } catch (IOException | RuntimeException ex) {
            throw new SmartRedisLimiterException(
                    ErrorCode.TYPED_POLICY_SNAPSHOT_INVALID,
                    String.format(ErrorMessage.TYPED_POLICY_SNAPSHOT_INVALID, ex.getMessage()),
                    ex);
        }
    }
}
