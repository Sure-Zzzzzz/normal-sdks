package io.github.surezzzzzz.sdk.audit.limiter.management.listener;

import io.github.surezzzzzz.sdk.audit.limiter.management.annotation.SmartRedisLimiterManagementAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.limiter.management.handler.SmartRedisLimiterManagementAuditHandler;
import io.github.surezzzzzz.sdk.audit.limiter.management.handler.SmartRedisLimiterTypedAuditHandler;
import io.github.surezzzzzz.sdk.audit.limiter.management.model.SmartRedisLimiterManagementAuditRecord;
import io.github.surezzzzzz.sdk.audit.limiter.management.model.SmartRedisLimiterTypedAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * 管理审计 Handler 异步分发器
 *
 * <p>审计分发不占用事件发布线程；单个 Handler 失败仅记日志，
 * 不中断后续 Handler、不反写事件发布方。
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartRedisLimiterManagementAuditListenerComponent
public class SmartRedisLimiterManagementAuditHandlerDispatcher {

    /**
     * 异步分发三元组管理审计记录
     *
     * @param record   动作事实审计记录
     * @param handlers 已注册 Handler 列表
     */
    @Async
    public void dispatchManagement(SmartRedisLimiterManagementAuditRecord record,
                                   List<SmartRedisLimiterManagementAuditHandler> handlers) {
        for (SmartRedisLimiterManagementAuditHandler handler : handlers) {
            try {
                handler.handle(record);
            } catch (Exception ex) {
                log.error("三元组管理审计 Handler 处理失败: handler={}", handler.getName(), ex);
            }
        }
    }

    /**
     * 异步分发类型化管理审计记录
     *
     * @param record   受控摘要审计记录
     * @param handlers 已注册 Handler 列表
     */
    @Async
    public void dispatchTyped(SmartRedisLimiterTypedAuditRecord record,
                              List<SmartRedisLimiterTypedAuditHandler> handlers) {
        for (SmartRedisLimiterTypedAuditHandler handler : handlers) {
            try {
                handler.handle(record);
            } catch (Exception ex) {
                log.error("类型化管理审计 Handler 处理失败: handler={}", handler.getName(), ex);
            }
        }
    }
}
