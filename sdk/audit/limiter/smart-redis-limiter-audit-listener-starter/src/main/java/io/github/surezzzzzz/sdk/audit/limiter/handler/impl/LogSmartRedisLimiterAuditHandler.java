package io.github.surezzzzzz.sdk.audit.limiter.handler.impl;

import io.github.surezzzzzz.sdk.audit.limiter.annotation.SmartRedisLimiterAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.limiter.constant.SmartRedisLimiterAuditListenerConstant;
import io.github.surezzzzzz.sdk.audit.limiter.handler.SmartRedisLimiterAuditHandler;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 默认日志限流审计处理器
 *
 * <p>仅输出限流运行诊断字段，不输出限流 Key、用户标识、原始 URI 或扩展属性。
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartRedisLimiterAuditListenerComponent
@ConditionalOnProperty(
        prefix = SmartRedisLimiterAuditListenerConstant.LOG_HANDLER_CONFIG_PREFIX,
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LogSmartRedisLimiterAuditHandler implements SmartRedisLimiterAuditHandler {

    @Override
    public void handle(SmartRedisLimiterRecord record) {
        if (record.isPassed()) {
            log.info("[LimiterAudit:PASS] source={}, algorithm={}, keyStrategy={}, resourceCode={}, "
                            + "policySource={}, policyRevision={}, routeRequired={}, routeResolved={}, redisMode={}, "
                            + "datasourceKey={}, fallbackReason={}, matchedPathPattern={}, methodName={}, limit={}, remaining={}, "
                            + "resetAt={}, durationNanos={}",
                    record.getSource(), record.getAlgorithm(), record.getKeyStrategy(), record.getResourceCode(),
                    record.getPolicySource(), record.getPolicyRevision(), record.isRouteRequired(),
                    record.isRouteResolved(), record.getRedisMode(), record.getDatasourceKey(),
                    record.getFallbackReason(), record.getMatchedPathPattern(), record.getMethodName(),
                    record.getLimit(), record.getRemaining(), record.getResetAt(), record.getDurationNanos());
        } else {
            log.warn("[LimiterAudit:LIMITED] source={}, algorithm={}, keyStrategy={}, resourceCode={}, "
                            + "policySource={}, policyRevision={}, routeRequired={}, routeResolved={}, redisMode={}, "
                            + "datasourceKey={}, fallbackReason={}, matchedPathPattern={}, methodName={}, limit={}, remaining={}, "
                            + "resetAt={}, durationNanos={}",
                    record.getSource(), record.getAlgorithm(), record.getKeyStrategy(), record.getResourceCode(),
                    record.getPolicySource(), record.getPolicyRevision(), record.isRouteRequired(),
                    record.isRouteResolved(), record.getRedisMode(), record.getDatasourceKey(),
                    record.getFallbackReason(), record.getMatchedPathPattern(), record.getMethodName(),
                    record.getLimit(), record.getRemaining(), record.getResetAt(), record.getDurationNanos());
        }
    }
}
