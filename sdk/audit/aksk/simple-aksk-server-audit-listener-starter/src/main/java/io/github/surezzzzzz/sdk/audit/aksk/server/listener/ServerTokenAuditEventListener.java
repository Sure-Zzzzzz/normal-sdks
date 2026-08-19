package io.github.surezzzzzz.sdk.audit.aksk.server.listener;

import io.github.surezzzzzz.sdk.audit.aksk.server.handler.ServerTokenAuditHandler;
import io.github.surezzzzzz.sdk.audit.aksk.server.model.ServerTokenAuditRecord;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.AbstractTokenEvent;
import io.github.surezzzzzz.sdk.auth.aksk.server.event.TokenIntrospectedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * Server Token 审计事件监听器。
 *
 * <p>事务事件仅在成功提交后转换为审计记录，防止回滚的业务变更留下错误审计；无事务发布方通过
 * {@code fallbackExecution} 兼容消费。该监听器是提交后的尽力处理，不承担可靠投递与重试职责。
 *
 * <p>转换过程刻意不复制事件中的 Token 原文，处理器只能获得脱敏审计字段。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
public class ServerTokenAuditEventListener {

    private final List<ServerTokenAuditHandler> auditHandlers;

    public ServerTokenAuditEventListener(List<ServerTokenAuditHandler> auditHandlers) {
        this.auditHandlers = auditHandlers;
        log.info("Server Token 审计事件监听器已初始化，共 {} 个处理器", auditHandlers.size());
        for (ServerTokenAuditHandler handler : auditHandlers) {
            log.info("  - {}", handler.getClass().getName());
        }
    }

    /**
     * 在事务成功提交后分发审计记录；单个处理器失败只记录安全摘要，不能影响已提交业务或阻断其他处理器。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onTokenEvent(AbstractTokenEvent event) {
        ServerTokenAuditRecord record = convertToAuditRecord(event);
        for (ServerTokenAuditHandler handler : auditHandlers) {
            try {
                handler.handle(record);
            } catch (Exception e) {
                log.error("Server Token 审计处理失败: handler={}, eventType={}, cause={}, clientId={}",
                        handler.getClass().getName(), event.getEventType(), event.getCause(), event.getClientId(), e);
            }
        }
    }

    /**
     * 仅复制审计所需的非敏感元数据；Token 原文不得进入 {@link ServerTokenAuditRecord}。
     */
    private ServerTokenAuditRecord convertToAuditRecord(AbstractTokenEvent event) {
        ServerTokenAuditRecord.ServerTokenAuditRecordBuilder builder = ServerTokenAuditRecord.builder()
                .eventType(event.getEventType())
                .cause(event.getCause())
                .eventTime(event.getEventTime())
                .clientId(event.getClientId())
                .clientType(event.getClientType())
                .userId(event.getUserId())
                .username(event.getUsername())
                .scopes(event.getScopes())
                .issuedAt(event.getIssuedAt())
                .expiresAt(event.getExpiresAt());

        if (event instanceof TokenIntrospectedEvent) {
            builder.active(((TokenIntrospectedEvent) event).isActive());
        }

        return builder.build();
    }
}
