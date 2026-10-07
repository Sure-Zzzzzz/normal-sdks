package io.github.surezzzzzz.sdk.audit.limiter.listener;

import io.github.surezzzzzz.sdk.audit.limiter.annotation.SmartRedisLimiterAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.limiter.handler.SmartRedisLimiterTypedAuditHandler;
import io.github.surezzzzzz.sdk.audit.limiter.model.SmartRedisLimiterTypedAuditRecord;
import io.github.surezzzzzz.sdk.audit.limiter.support.SmartRedisLimiterAuditRecordHelper;
import io.github.surezzzzzz.sdk.audit.limiter.support.SmartRedisLimiterTypedAuditRecordHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterTraceIdProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.audit.SmartRedisLimiterUserProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterTypedManagementEvent;
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
 * 同时消费 v1 执行事件与 v2 类型化管理事件（受控摘要记录）。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartRedisLimiterAuditListenerComponent
public class SmartRedisLimiterAuditEventListener {

    private final SmartRedisLimiterAuditRecordHelper recordHelper;
    private final SmartRedisLimiterAuditHandlerDispatcher handlerDispatcher;
    private final SmartRedisLimiterTypedAuditRecordHelper typedRecordHelper;
    private final List<SmartRedisLimiterTypedAuditHandler> typedHandlers;

    /**
     * 创建限流审计事件监听器
     *
     * @param userProviders     用户信息 Provider 列表
     * @param traceIdProvider   TraceId Provider
     * @param handlerDispatcher 异步审计 Handler 分发器
     * @param typedHandlers     类型化管理审计 Handler 列表（可为空，记录被跳过）
     */
    public SmartRedisLimiterAuditEventListener(
            @Autowired(required = false) List<SmartRedisLimiterUserProvider> userProviders,
            @Autowired(required = false) SmartRedisLimiterTraceIdProvider traceIdProvider,
            SmartRedisLimiterAuditHandlerDispatcher handlerDispatcher,
            @Autowired(required = false) List<SmartRedisLimiterTypedAuditHandler> typedHandlers) {
        this.recordHelper = new SmartRedisLimiterAuditRecordHelper(
                userProviders == null ? Collections.emptyList() : userProviders, traceIdProvider);
        this.handlerDispatcher = handlerDispatcher;
        this.typedRecordHelper = new SmartRedisLimiterTypedAuditRecordHelper();
        this.typedHandlers = typedHandlers == null ? Collections.<SmartRedisLimiterTypedAuditHandler>emptyList()
                : typedHandlers;
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

    /**
     * 接收类型化管理事件并分发受控摘要审计
     *
     * @param event 类型化管理事件
     */
    @EventListener
    public void onTypedManagementEvent(SmartRedisLimiterTypedManagementEvent event) {
        try {
            SmartRedisLimiterTypedAuditRecord record = typedRecordHelper.map(event.getPayload());
            for (SmartRedisLimiterTypedAuditHandler handler : typedHandlers) {
                try {
                    handler.handle(record);
                } catch (Exception ex) {
                    log.error("类型化管理审计 Handler 处理失败: handler={}", handler.getName(), ex);
                }
            }
        } catch (Exception e) {
            log.error("SmartRedisLimiter 类型化管理事件审计快照生成失败", e);
        }
    }
}
