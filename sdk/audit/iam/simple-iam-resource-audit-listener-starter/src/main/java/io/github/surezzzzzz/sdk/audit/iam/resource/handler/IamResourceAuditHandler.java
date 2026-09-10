package io.github.surezzzzzz.sdk.audit.iam.resource.handler;

import io.github.surezzzzzz.sdk.audit.iam.resource.model.IamResourceAuditRecord;

/**
 * IAM 资源访问审计处理器
 *
 * <p>业务实现此接口来处理 IAM 来源的资源访问审计记录（落库、投递等由业务决定）。
 * 单个处理器抛出异常只记录告警，不影响其他处理器，也不影响业务请求。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public interface IamResourceAuditHandler {

    /**
     * 处理一条 IAM 资源访问审计记录
     *
     * @param record 审计记录
     */
    void handle(IamResourceAuditRecord record);
}
