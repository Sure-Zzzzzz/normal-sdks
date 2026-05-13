package io.github.surezzzzzz.sdk.audit.limiter.handler.impl;

import io.github.surezzzzzz.sdk.audit.limiter.annotation.SmartRedisLimiterAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.limiter.handler.SmartRedisLimiterAuditHandler;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

/**
 * 默认日志限流审计处理器
 *
 * <p>将限流审计记录输出到日志。
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartRedisLimiterAuditListenerComponent
@ConditionalOnProperty(
        prefix = "io.github.surezzzzzz.sdk.audit.limiter.listener.handler.log",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class LogSmartRedisLimiterAuditHandler implements SmartRedisLimiterAuditHandler {

    @Override
    public void handle(SmartRedisLimiterRecord record) {
        if (record.isPassed()) {
            log.info("[LimiterAudit:PASS] key={}, source={}, algorithm={}, clientId={}, userId={}, requestUri={}, methodName={}",
                    record.getLimitKey(), record.getSource(), record.getAlgorithm(),
                    record.getClientId(), record.getUserId(),
                    record.getRequestUri(), record.getMethodName());
        } else {
            log.warn("[LimiterAudit:LIMITED] key={}, source={}, algorithm={}, clientId={}, userId={}, requestUri={}, methodName={}, limit={}, remaining={}, resetAt={}",
                    record.getLimitKey(), record.getSource(), record.getAlgorithm(),
                    record.getClientId(), record.getUserId(),
                    record.getRequestUri(), record.getMethodName(),
                    record.getLimit(), record.getRemaining(), record.getResetAt());
        }
    }
}
