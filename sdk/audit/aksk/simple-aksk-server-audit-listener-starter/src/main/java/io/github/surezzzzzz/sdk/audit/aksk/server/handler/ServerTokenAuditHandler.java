package io.github.surezzzzzz.sdk.audit.aksk.server.handler;

import io.github.surezzzzzz.sdk.audit.aksk.server.model.ServerTokenAuditRecord;

/**
 * Server Token 审计处理器接口。
 *
 * <p>业务通过实现本接口消费已脱敏的 Token 生命周期审计记录。处理器应自行负责持久化、重试等可靠性能力，
 * 不得依赖监听器提供可靠投递，也不得从记录中推导或回填 Token 原文。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
public interface ServerTokenAuditHandler {
    /**
     * 处理已提交事务或无事务发布方对应的脱敏审计记录。
     *
     * @param record 不含 Token 原文的审计记录
     */
    void handle(ServerTokenAuditRecord record);
}
