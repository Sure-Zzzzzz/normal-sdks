package io.github.surezzzzzz.sdk.audit.limiter.management.listener;

import io.github.surezzzzzz.sdk.audit.limiter.management.annotation.SmartRedisLimiterManagementAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.limiter.management.handler.SmartRedisLimiterManagementAuditHandler;
import io.github.surezzzzzz.sdk.audit.limiter.management.handler.SmartRedisLimiterTypedAuditHandler;
import io.github.surezzzzzz.sdk.audit.limiter.management.model.SmartRedisLimiterManagementAuditRecord;
import io.github.surezzzzzz.sdk.audit.limiter.management.model.SmartRedisLimiterTypedAuditRecord;
import io.github.surezzzzzz.sdk.audit.limiter.management.support.SmartRedisLimiterManagementAuditRecordHelper;
import io.github.surezzzzzz.sdk.audit.limiter.management.support.SmartRedisLimiterTypedAuditRecordHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterManagementEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterTypedManagementEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;

import java.util.Collections;
import java.util.List;

/**
 * 限流管理审计事件监听器
 *
 * <p>与管理宿主同进程部署，消费三元组与类型化两类管理事件：
 * 事件发布线程内只完成受控记录映射，随后交由异步分发器处理，
 * 不占用策略写事务线程。限流执行事件由执行审计件
 * （smart-redis-limiter-audit-listener-starter）在运行端消费，本监听器不监听执行事件。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartRedisLimiterManagementAuditListenerComponent
public class SmartRedisLimiterManagementAuditEventListener {

    private final SmartRedisLimiterManagementAuditRecordHelper managementRecordHelper;
    private final List<SmartRedisLimiterManagementAuditHandler> managementHandlers;
    private final SmartRedisLimiterTypedAuditRecordHelper typedRecordHelper;
    private final List<SmartRedisLimiterTypedAuditHandler> typedHandlers;
    private final SmartRedisLimiterManagementAuditHandlerDispatcher dispatcher;

    /**
     * 创建管理审计事件监听器
     *
     * @param managementHandlers 三元组管理审计 Handler 列表（可为空，记录被跳过）
     * @param typedHandlers      类型化管理审计 Handler 列表（可为空，记录被跳过）
     * @param dispatcher         异步分发器
     */
    public SmartRedisLimiterManagementAuditEventListener(
            @Autowired(required = false) List<SmartRedisLimiterManagementAuditHandler> managementHandlers,
            @Autowired(required = false) List<SmartRedisLimiterTypedAuditHandler> typedHandlers,
            SmartRedisLimiterManagementAuditHandlerDispatcher dispatcher) {
        this.managementRecordHelper = new SmartRedisLimiterManagementAuditRecordHelper();
        this.managementHandlers = managementHandlers == null
                ? Collections.<SmartRedisLimiterManagementAuditHandler>emptyList() : managementHandlers;
        this.typedRecordHelper = new SmartRedisLimiterTypedAuditRecordHelper();
        this.typedHandlers = typedHandlers == null
                ? Collections.<SmartRedisLimiterTypedAuditHandler>emptyList() : typedHandlers;
        this.dispatcher = dispatcher;
    }

    /**
     * 接收三元组管理事件：映射动作事实记录后异步分发。
     *
     * @param event 三元组管理事件
     */
    @EventListener
    public void onManagementEvent(SmartRedisLimiterManagementEvent event) {
        try {
            SmartRedisLimiterManagementAuditRecord record = managementRecordHelper.map(event.getPayload());
            if (managementHandlers.isEmpty()) {
                log.warn("三元组管理事件无审计 Handler，记录被跳过 operation={}, revision={}",
                        record.getOperation(), record.getRevision());
                return;
            }
            dispatcher.dispatchManagement(record, managementHandlers);
        } catch (Exception e) {
            log.error("SmartRedisLimiter 三元组管理事件审计快照生成失败", e);
        }
    }

    /**
     * 接收类型化管理事件：映射受控摘要记录后异步分发。
     *
     * @param event 类型化管理事件
     */
    @EventListener
    public void onTypedManagementEvent(SmartRedisLimiterTypedManagementEvent event) {
        try {
            SmartRedisLimiterTypedAuditRecord record = typedRecordHelper.map(event.getPayload());
            if (typedHandlers.isEmpty()) {
                log.warn("类型化管理事件无审计 Handler，记录被跳过 operation={}, revision={}",
                        record.getOperation(), record.getRevision());
                return;
            }
            dispatcher.dispatchTyped(record, typedHandlers);
        } catch (Exception e) {
            log.error("SmartRedisLimiter 类型化管理事件审计快照生成失败", e);
        }
    }
}
