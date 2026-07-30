package io.github.surezzzzzz.sdk.crm.server.core.port.outbox;

import io.github.surezzzzzz.sdk.crm.server.core.domain.event.DomainEventOutboxRecord;

/**
 * 内部领域事件 Outbox 端口。
 *
 * @author surezzzzzz
 */
public interface DomainEventOutboxPort {

    /**
     * 保存权威 Outbox 事实。
     *
     * @param tenantId 租户唯一标识
     * @param record   待持久化的权威记录
     * @return 处理后的领域事实或校验结果。
     */
    DomainEventOutboxRecord save(String tenantId, DomainEventOutboxRecord record);
}
