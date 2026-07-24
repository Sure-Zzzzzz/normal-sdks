package io.github.surezzzzzz.sdk.kms.core.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsAuditEvent;

/**
 * 历史兼容审计仓储端口。
 *
 * <p>该端口保留给已有直接审计持久化适配；KMS Server 默认审计链路使用提交后的
 * {@link KmsEventPublisher} 与独立 listener。两种适配均不得记录审计边界以外的数据；默认链路的
 * 发布、监听和外部持久化失败不得回滚已提交的 KMS 状态或改变密码学结果。</p>
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
