package io.github.surezzzzzz.sdk.audit.aksk.server.handler;

import io.github.surezzzzzz.sdk.audit.aksk.server.model.ServerTokenAuditRecord;

/**
 * Server Token 审计处理器接口
 *
 * <p>业务需要实现此接口来处理 Token 生命周期审计记录。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public interface ServerTokenAuditHandler {
    /**
     * 处理审计记录
     *
     * @param record 审计记录
     */
    void handle(ServerTokenAuditRecord record);
}
