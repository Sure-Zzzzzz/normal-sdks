package io.github.surezzzzzz.sdk.audit.limiter.listener;

import io.github.surezzzzzz.sdk.audit.limiter.annotation.SmartRedisLimiterAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.limiter.support.SmartRedisLimiterAuditRecordHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterTraceIdProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterUserProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;

import java.util.Collections;
import java.util.List;

/**
 * SmartRedisLimiter 限流审计事件监听器
 *
 * <p>在事件发布线程中生成安全审计快照，再异步分发给 Handler。
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartRedisLimiterAuditListenerComponent
public class SmartRedisLimiterAuditEventListener {

    private final SmartRedisLimiterAuditRecordHelper recordHelper;
    private final SmartRedisLimiterAuditHandlerDispatcher handlerDispatcher;

    /**
     * 创建限流审计事件监听器
     *
     * @param userProviders     用户信息 Provider 列表
     * @param traceIdProvider   TraceId Provider
     * @param handlerDispatcher 异步审计 Handler 分发器
     */
    public SmartRedisLimiterAuditEventListener(
            @Autowired(required = false) List<SmartRedisLimiterUserProvider> userProviders,
            @Autowired(required = false) SmartRedisLimiterTraceIdProvider traceIdProvider,
            SmartRedisLimiterAuditHandlerDispatcher handlerDispatcher) {
        this.recordHelper = new SmartRedisLimiterAuditRecordHelper(
                userProviders == null ? Collections.emptyList() : userProviders, traceIdProvider);
        this.handlerDispatcher = handlerDispatcher;
    }

    /**
     * 接收限流事件并提交异步审计
     *
     * @param event 限流事件
     */
    @EventListener
    public void onLimitEvent(SmartRedisLimiterEvent event) {
        try {
            SmartRedisLimiterRecord record = recordHelper.map(event);
            handlerDispatcher.dispatch(record);
        } catch (Exception e) {
            log.error("SmartRedisLimiter 限流事件审计快照生成失败", e);
        }
    }
}
