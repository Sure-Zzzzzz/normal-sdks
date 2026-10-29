package io.github.surezzzzzz.sdk.audit.crm.listener;

import io.github.surezzzzzz.sdk.audit.crm.handler.CrmAuditHandler;
import io.github.surezzzzzz.sdk.audit.crm.model.CrmAuditRecord;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * CRM 审计处理器异步分发器。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
public class CrmAuditEventDispatcher {

    private final List<CrmAuditHandler> auditHandlers;

    /**
     * 创建分发器。
     *
     * @param auditHandlers 宿主注册的处理器
     */
    public CrmAuditEventDispatcher(List<CrmAuditHandler> auditHandlers) {
        this.auditHandlers = Collections.unmodifiableList(new ArrayList<CrmAuditHandler>(auditHandlers));
    }

    /**
     * 异步分发已完成事件的审计记录。
     *
     * @param record 已完成事件的审计记录
     */
    @Async
    public void dispatch(CrmAuditRecord record) {
        for (CrmAuditHandler handler : auditHandlers) {
            try {
                handler.handle(record);
            } catch (Exception e) {
                // 旁路处理器异常不能影响其他处理器；不输出异常消息，避免泄露宿主业务数据。
                log.error("CRM审计处理器异常：eventType={}，handler={}，exceptionType={}",
                        record.getEventType(), handler.getClass().getName(), e.getClass().getName());
            }
        }
    }
}
