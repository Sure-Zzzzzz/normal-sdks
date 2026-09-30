package io.github.surezzzzzz.sdk.audit.iam.resource.handler;

import io.github.surezzzzzz.sdk.audit.iam.resource.model.IamResourceAuditRecord;

/**
 * IAM 资源访问审计处理器。
 *
 * @author surezzzzzz
 */
public interface IamResourceAuditHandler {

    /**
     * 处理一条 IAM 来源的资源访问审计记录。
     *
     * @param record 审计记录
     */
    void handle(IamResourceAuditRecord record);
}
