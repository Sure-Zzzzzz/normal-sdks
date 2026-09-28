package io.github.surezzzzzz.sdk.audit.aksk.server.handler;

import io.github.surezzzzzz.sdk.audit.aksk.server.model.ServerClientLifecycleAuditRecord;

/**
 * AKU 生命周期审计处理扩展点。
 */
public interface ServerClientLifecycleAuditHandler {

    /**
     * 处理不含 secret、Token 和三权快照的已提交生命周期记录。
     */
    void handle(ServerClientLifecycleAuditRecord record);
}
