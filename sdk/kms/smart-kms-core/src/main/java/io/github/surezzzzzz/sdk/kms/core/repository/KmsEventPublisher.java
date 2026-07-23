package io.github.surezzzzzz.sdk.kms.core.repository;

import io.github.surezzzzzz.sdk.kms.core.model.KmsAuditEvent;

/**
 * 可选 KMS 事件发布端口。
 *
 * <p>事件载荷沿用安全审计事件边界；适配层不得补充密码材料、请求正文、认证凭据或底层异常细节。</p>
 *
 * @author surezzzzzz
 */
public interface KmsEventPublisher {

    /**
     * 发布已完成的安全事件。
     *
     * @param event 安全事件
     */
    void publish(KmsAuditEvent event);
}
