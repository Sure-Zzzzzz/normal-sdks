package io.github.surezzzzzz.sdk.audit.limiter.support;

import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterTraceIdProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterUserProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;
import java.util.function.Function;

/**
 * SmartRedisLimiter 审计记录转换工具
 *
 * @author surezzzzzz
 */
@Slf4j
public class SmartRedisLimiterAuditRecordHelper {

    private final List<SmartRedisLimiterUserProvider> userProviders;
    private final SmartRedisLimiterTraceIdProvider traceIdProvider;

    /**
     * 创建审计记录转换工具
     *
     * @param userProviders   用户信息 Provider 列表
     * @param traceIdProvider TraceId Provider
     */
    public SmartRedisLimiterAuditRecordHelper(List<SmartRedisLimiterUserProvider> userProviders,
                                              SmartRedisLimiterTraceIdProvider traceIdProvider) {
        this.userProviders = userProviders == null ? Collections.emptyList() : userProviders;
        this.traceIdProvider = traceIdProvider;
    }

    /**
     * 将事件转换为安全审计记录
     *
     * @param event 限流事件
     * @return 审计记录
     */
    public SmartRedisLimiterRecord map(SmartRedisLimiterEvent event) {
        SmartRedisLimiterRecord record = SmartRedisLimiterRecord.builder()
                .clientId(getFirstNonNull(SmartRedisLimiterUserProvider::getClientId))
                .clientType(getFirstNonNull(SmartRedisLimiterUserProvider::getClientType))
                .userId(getFirstNonNull(SmartRedisLimiterUserProvider::getUserId))
                .username(getFirstNonNull(SmartRedisLimiterUserProvider::getUsername))
                .limitKey(event.getLimitKey())
                .keyStrategy(event.getKeyStrategy())
                .algorithm(event.getAlgorithm())
                .limitRules(event.getLimitRules())
                .passed(event.isPassed())
                .routeKey(event.getRouteKey())
                .datasourceKey(event.getDatasourceKey())
                .redisMode(event.getRedisMode())
                .routeRequired(event.isRouteRequired())
                .routeResolved(event.isRouteResolved())
                .fallbackReason(event.getFallbackReason())
                .source(event.getSource())
                .requestUri(event.getRequestUri())
                .httpMethod(event.getHttpMethod())
                .clientIp(event.getClientIp())
                .matchedPathPattern(event.getMatchedPathPattern())
                .methodName(event.getMethodName())
                .methodQualifiedName(event.getMethodQualifiedName())
                .limit(event.getLimit())
                .remaining(event.getRemaining())
                .resetAt(event.getResetAt())
                .durationNanos(event.getDurationNanos())
                .timestamp(System.currentTimeMillis())
                .traceId(getTraceId())
                .extra(null)
                .resourceCode(event.getResourceCode())
                .policySource(event.getPolicySource())
                .policyRevision(event.getPolicyRevision())
                .build();
        record.validatePolicyContext();
        return record;
    }

    private String getFirstNonNull(Function<SmartRedisLimiterUserProvider, String> getter) {
        for (SmartRedisLimiterUserProvider provider : userProviders) {
            try {
                String value = getter.apply(provider);
                if (value != null) {
                    return value;
                }
            } catch (Exception e) {
                log.warn("SmartRedisLimiter 审计用户信息 Provider 调用失败", e);
            }
        }
        return null;
    }

    private String getTraceId() {
        if (traceIdProvider == null) {
            return null;
        }
        try {
            return traceIdProvider.getTraceId();
        } catch (Exception e) {
            log.warn("SmartRedisLimiter 审计 TraceId Provider 调用失败", e);
            return null;
        }
    }
}
