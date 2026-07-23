package io.github.surezzzzzz.sdk.kms.core.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsAuditEvent;

/**
 * 审计仓储端口。
 *
 * <p>适配层必须把审计写入与对应业务事务按设计规定的提交顺序协调；审计持久化失败时敏感操作必须失败关闭。</p>
 *
 * @author surezzzzzz
 */
public interface KmsAuditRepository {

    /**
     * 追加已通过安全边界校验的审计事件。
     *
     * @param event 安全审计事件
     */
    void append(KmsAuditEvent event);
}
