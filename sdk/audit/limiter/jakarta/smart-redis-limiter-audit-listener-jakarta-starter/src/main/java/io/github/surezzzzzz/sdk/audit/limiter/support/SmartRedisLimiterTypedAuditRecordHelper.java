package io.github.surezzzzzz.sdk.audit.limiter.support;

import io.github.surezzzzzz.sdk.audit.limiter.model.SmartRedisLimiterTypedAuditRecord;
import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterTypedManagementEventPayload;

import java.util.Map;

/**
 * 类型化限流管理审计记录转换工具
 * <p>只提取受控摘要字段；操作人短摘要取自事件 payload 自身的 operator 语义——
 * payload 不携带原始操作人时置 null，不从审计侧反查任何身份系统。
 * operatorIdentity 完整身份事实不进入审计记录。</p>
 *
 * @author surezzzzzz
 */
public final class SmartRedisLimiterTypedAuditRecordHelper {

    /**
     * 不反查身份系统：审计只消费事件自身字段
     */
    public SmartRedisLimiterTypedAuditRecordHelper() {
    }

    /**
     * 将类型化管理事件载荷转换为受控摘要审计记录
     *
     * @param payload 类型化管理事件载荷
     * @return 受控摘要审计记录
     */
    public SmartRedisLimiterTypedAuditRecord map(SmartRedisLimiterTypedManagementEventPayload payload) {
        return SmartRedisLimiterTypedAuditRecord.builder()
                .operation(payload.getOperation() == null ? null : payload.getOperation().getCode())
                .serviceCode(payload.getServiceCode())
                .resourceCode(payload.getResourceCode())
                .dimension(payload.getDimension() == null ? null : payload.getDimension().getCode())
                .selector(payload.getSelector() == null ? null : payload.getSelector().getCode())
                .objectDigest(payload.getObjectDigest())
                .ruleId(payload.getRuleId())
                .revision(payload.getRevision())
                .result(payload.getResult())
                .reason(payload.getReason())
                .operatorDigest(operatorDigestOf(payload))
                .occurredAt(payload.getOccurredAt())
                .build();
    }

    private static String operatorDigestOf(SmartRedisLimiterTypedManagementEventPayload payload) {
        Map<String, Object> attributes = payload.getAttributes();
        if (attributes == null) {
            return null;
        }
        Object identity = attributes.get(SmartRedisLimiterConstant.OPERATOR_IDENTITY_ATTRIBUTE_KEY);
        return identity == null ? null : identity.toString();
    }
}
