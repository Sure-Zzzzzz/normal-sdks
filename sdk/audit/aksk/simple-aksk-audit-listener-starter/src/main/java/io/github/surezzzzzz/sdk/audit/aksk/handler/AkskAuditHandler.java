package io.github.surezzzzzz.sdk.audit.aksk.handler;

import io.github.surezzzzzz.sdk.audit.aksk.model.AkskAuditRecord;

/**
 * AKSK 审计处理器接口
 *
 * <p>业务需要实现此接口来处理审计记录。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public interface AkskAuditHandler {
    /**
     * 处理审计记录
     *
     * @param record 审计记录
     */
    void handle(AkskAuditRecord record);
}
