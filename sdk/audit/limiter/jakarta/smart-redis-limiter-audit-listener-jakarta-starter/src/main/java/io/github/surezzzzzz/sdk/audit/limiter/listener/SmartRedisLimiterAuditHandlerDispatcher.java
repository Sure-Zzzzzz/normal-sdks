package io.github.surezzzzzz.sdk.audit.limiter.listener;

import io.github.surezzzzzz.sdk.audit.limiter.annotation.SmartRedisLimiterAuditListenerComponent;
import io.github.surezzzzzz.sdk.audit.limiter.handler.SmartRedisLimiterAuditHandler;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * SmartRedisLimiter 审计处理器异步分发器
 *
 * @author surezzzzzz
 */
@Slf4j
@SmartRedisLimiterAuditListenerComponent
public class SmartRedisLimiterAuditHandlerDispatcher {

    private final List<SmartRedisLimiterAuditHandler> handlers;

    /**
     * 创建异步审计 Handler 分发器
     *
     * @param handlers 审计 Handler 列表
     */
    public SmartRedisLimiterAuditHandlerDispatcher(List<SmartRedisLimiterAuditHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * 异步分发审计记录
     *
     * @param record 审计记录
     */
    @Async
    public void dispatch(SmartRedisLimiterRecord record) {
        for (SmartRedisLimiterAuditHandler handler : handlers) {
            try {
                handler.handle(record);
            } catch (Exception e) {
                log.error("SmartRedisLimiter 审计 Handler {} 处理失败", handler.getName(), e);
            }
        }
    }
}
