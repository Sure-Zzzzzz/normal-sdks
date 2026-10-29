package io.github.surezzzzzz.sdk.crm.server.event;

import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import io.github.surezzzzzz.sdk.crm.server.core.domain.event.CrmAuditEvent;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmAction;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmIdResourceType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.port.audit.CrmAuditPort;
import io.github.surezzzzzz.sdk.crm.server.core.port.system.CrmIdGenerator;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcAuditRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

/**
 * 审计端口装饰器：本地事实落库由委托完成，提交后发布 {@link CrmAuditEvent} 进程内事件。
 *
 * <p>发布时机=当前写事务提交后（注册事务同步）；事务回滚不发布，保证不产生幻影审计。
 * 事务同步不活跃（无事务上下文）时立即发布，行为退化为落库即发。</p>
 *
 * @author surezzzzzz
 */
@Primary
@SimpleCrmServerComponent
public class SimpleCrmServerEventPublishingAuditPort implements CrmAuditPort {

    private final CrmAuditPort delegate;
    private final ApplicationEventPublisher eventPublisher;
    private final CrmIdGenerator idGenerator;

    /**
     * 创建事件扇出审计端口。
     *
     * @param delegate       落库委托（本地审计事实表）
     * @param eventPublisher Spring 事件发布器
     * @param idGenerator    事件标识生成器
     */
    public SimpleCrmServerEventPublishingAuditPort(SimpleCrmServerJdbcAuditRepository delegate,
                                                   ApplicationEventPublisher eventPublisher,
                                                   CrmIdGenerator idGenerator) {
        this.delegate = delegate;
        this.eventPublisher = eventPublisher;
        this.idGenerator = idGenerator;
    }

    @Override
    public void record(CrmOwner actor, CrmAction action, CrmResourceType targetResourceType,
                       String targetResourceId, long targetAggregateVersion, String correlationId,
                       CrmErrorCode resultCode, Instant occurredAt) {
        delegate.record(actor, action, targetResourceType, targetResourceId, targetAggregateVersion,
                correlationId, resultCode, occurredAt);
        CrmAuditEvent event = new CrmAuditEvent(idGenerator.nextId(CrmIdResourceType.DOMAIN_EVENT),
                CrmAuditEvent.CURRENT_EVENT_VERSION, actor.getOwner(), action.getCode(),
                targetResourceType.getCode(), targetResourceId, targetAggregateVersion, correlationId,
                resultCode == null ? null : resultCode.getCode(), occurredAt);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    eventPublisher.publishEvent(event);
                }
            });
            return;
        }
        eventPublisher.publishEvent(event);
    }
}
