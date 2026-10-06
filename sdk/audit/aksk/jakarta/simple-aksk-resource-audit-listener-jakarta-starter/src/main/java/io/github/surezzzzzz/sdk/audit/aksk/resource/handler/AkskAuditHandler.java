package io.github.surezzzzzz.sdk.audit.aksk.resource.handler;

import io.github.surezzzzzz.sdk.audit.aksk.resource.model.AkskAuditRecord;

/**
 * 业务审计消费接口；业务只需负责记录的持久化或转发，不需自行监听公共事件。
 *
 * @author surezzzzzz
 */
public interface AkskAuditHandler {
    /**
     * 处理一条 AKSK 已认证访问记录；处理器之间相互隔离，异常由官方监听器拦截。
     *
     * @param record 当前处理器独立持有的审计记录
     */
    void handle(AkskAuditRecord record);
}
