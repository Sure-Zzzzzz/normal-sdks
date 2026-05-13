package io.github.surezzzzzz.sdk.audit.limiter.listener;

import io.github.surezzzzzz.sdk.audit.limiter.annotation.SmartRedisLimiterAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.limiter.handler.SmartRedisLimiterAuditHandler;
import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterTraceIdProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterUserProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * SmartRedisLimiter 限流审计事件监听器
 *
 * <p>监听 {@link SmartRedisLimiterEvent}，转换为 {@link SmartRedisLimiterRecord} 后调用 Handler 处理。
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartRedisLimiterAuditListenerComponent
public class SmartRedisLimiterAuditEventListener {

    private final List<SmartRedisLimiterAuditHandler> handlers;
    private final List<SmartRedisLimiterUserProvider> userProviders;
    private final SmartRedisLimiterTraceIdProvider traceIdProvider;

    public SmartRedisLimiterAuditEventListener(
            List<SmartRedisLimiterAuditHandler> handlers,
            @Autowired(required = false) List<SmartRedisLimiterUserProvider> userProviders,
            @Autowired(required = false) SmartRedisLimiterTraceIdProvider traceIdProvider) {
        this.handlers = handlers;
        this.userProviders = userProviders != null ? userProviders : Collections.emptyList();
        this.traceIdProvider = traceIdProvider;
        log.info("SmartRedisLimiterAuditEventListener initialized with {} handlers, {} userProviders",
                handlers.size(), this.userProviders.size());
    }

    @EventListener
    @Async
    public void onLimitEvent(SmartRedisLimiterEvent event) {
        try {
            SmartRedisLimiterRecord record = convertToRecord(event);
            for (SmartRedisLimiterAuditHandler handler : handlers) {
                try {
                    handler.handle(record);
                } catch (Exception e) {
                    log.error("Handler {} failed", handler.getName(), e);
                }
            }
        } catch (Exception e) {
            log.error("Failed to handle limit event", e);
        }
    }

    private SmartRedisLimiterRecord convertToRecord(SmartRedisLimiterEvent event) {
        return SmartRedisLimiterRecord.builder()
                // 用户信息
                .clientId(getFirstNonNull(userProviders, SmartRedisLimiterUserProvider::getClientId))
                .clientType(getFirstNonNull(userProviders, SmartRedisLimiterUserProvider::getClientType))
                .userId(getFirstNonNull(userProviders, SmartRedisLimiterUserProvider::getUserId))
                .username(getFirstNonNull(userProviders, SmartRedisLimiterUserProvider::getUsername))
                // 限流上下文
                .limitKey(event.getLimitKey())
                .keyStrategy(event.getKeyStrategy())
                .algorithm(event.getAlgorithm())
                .limitRules(event.getLimitRules())
                .passed(event.isPassed())
                // 来源
                .source(event.getSource())
                // 请求信息
                .requestUri(event.getRequestUri())
                .httpMethod(event.getHttpMethod())
                .clientIp(event.getClientIp())
                .matchedPathPattern(event.getMatchedPathPattern())
                // 方法信息
                .methodName(event.getMethodName())
                .methodQualifiedName(event.getMethodQualifiedName())
                // 限流详情
                .limit(event.getLimit())
                .remaining(event.getRemaining())
                .resetAt(event.getResetAt())
                .durationNanos(event.getDurationNanos())
                // 元数据
                .timestamp(event.getTimestamp())
                .traceId(traceIdProvider != null ? traceIdProvider.getTraceId() : null)
                // 扩展字段
                .extra(convertAttributes(event.getAttributes()))
                .build();
    }

    /**
     * 将 Event 的 Map&lt;String, Object&gt; attributes 转换为 Record 的 Map&lt;String, String&gt; extra
     */
    private Map<String, String> convertAttributes(Map<String, Object> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return null;
        }
        Map<String, String> extra = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : attributes.entrySet()) {
            if (entry.getValue() != null) {
                extra.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return extra;
    }

    private String getFirstNonNull(List<SmartRedisLimiterUserProvider> providers,
                                   Function<SmartRedisLimiterUserProvider, String> getter) {
        for (SmartRedisLimiterUserProvider p : providers) {
            String v = getter.apply(p);
            if (v != null) {
                return v;
            }
        }
        return null;
    }
}
