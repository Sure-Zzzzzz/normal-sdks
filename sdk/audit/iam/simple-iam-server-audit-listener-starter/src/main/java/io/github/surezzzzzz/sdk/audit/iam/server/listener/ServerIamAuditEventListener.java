package io.github.surezzzzzz.sdk.audit.iam.server.listener;

import io.github.surezzzzzz.sdk.audit.iam.server.handler.ServerIamAuditHandler;
import io.github.surezzzzzz.sdk.audit.iam.server.model.ServerIamAuditEventFamily;
import io.github.surezzzzzz.sdk.audit.iam.server.model.ServerIamAuditRecord;
import io.github.surezzzzzz.sdk.auth.iam.server.event.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;

/**
 * IAM 统一审计事件监听器。
 *
 * <p>订阅 {@code AbstractIamEvent} 统一根，单入口收全部四族事件（Token / 认证 / 会话 / 管理面）。
 * 事务事件仅在成功提交后转换为审计记录，防止回滚的业务变更留下错误审计；无事务发布方通过
 * {@code fallbackExecution} 兼容消费。该监听器是提交后的尽力处理，不承担可靠投递与重试职责。
 *
 * <p>转换过程刻意不复制事件中的 Token 原文，处理器只能获得脱敏审计字段。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Slf4j
public class ServerIamAuditEventListener {

    private final List<ServerIamAuditHandler> auditHandlers;

    public ServerIamAuditEventListener(List<ServerIamAuditHandler> auditHandlers) {
        this.auditHandlers = auditHandlers;
        log.info("IAM 审计事件监听器已初始化，共 {} 个处理器", auditHandlers.size());
        for (ServerIamAuditHandler handler : auditHandlers) {
            log.info("  - {}", handler.getClass().getName());
        }
    }

    /**
     * 在事务成功提交后分发审计记录；单个处理器失败只记录安全摘要，不能影响已提交业务或阻断其他处理器。
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onIamEvent(AbstractIamEvent event) {
        ServerIamAuditRecord record = convertToAuditRecord(event);
        for (ServerIamAuditHandler handler : auditHandlers) {
            try {
                handler.handle(record);
            } catch (Exception e) {
                log.error("IAM 审计处理失败: handler={}, family={}, eventType={}, cause={}, subjectId={}",
                        handler.getClass().getName(), record.getFamily(), record.getEventType(),
                        record.getCause(), record.getSubjectId(), e);
            }
        }
    }

    /**
     * 按事件族归一化为统一审计记录；仅复制审计所需的非敏感元数据，
     * Token 原文不得进入 {@link ServerIamAuditRecord}。
     */
    private ServerIamAuditRecord convertToAuditRecord(AbstractIamEvent event) {
        if (event instanceof AbstractTokenEvent) {
            return convertTokenEvent((AbstractTokenEvent) event);
        }
        if (event instanceof AuthenticationEvent) {
            return convertAuthenticationEvent((AuthenticationEvent) event);
        }
        if (event instanceof SessionLifecycleEvent) {
            return convertSessionEvent((SessionLifecycleEvent) event);
        }
        if (event instanceof AdminActionEvent) {
            return convertAdminActionEvent((AdminActionEvent) event);
        }
        log.warn("未识别的 IAM 事件类型，跳过审计转换：class={}", event.getClass().getName());
        return null;
    }

    private ServerIamAuditRecord convertTokenEvent(AbstractTokenEvent event) {
        ServerIamAuditRecord.ServerIamAuditRecordBuilder builder = ServerIamAuditRecord.builder()
                .family(ServerIamAuditEventFamily.TOKEN)
                .eventType(event.getEventType().getCode())
                .cause(event.getCause().getCode())
                .eventTime(event.getEventTime())
                .clientId(event.getClientId())
                .clientType(event.getClientType())
                .userId(event.getUserId())
                .username(event.getUsername())
                .scopes(event.getScopes())
                .issuedAt(event.getIssuedAt())
                .expiresAt(event.getExpiresAt());

        if (event instanceof TokenVerifiedEvent) {
            TokenVerifiedEvent verifiedEvent = (TokenVerifiedEvent) event;
            builder.active(verifiedEvent.isActive())
                    .verificationClientId(verifiedEvent.getVerificationClientId());
        } else if (event instanceof RefreshTokenReuseDetectedEvent) {
            builder.familyId(((RefreshTokenReuseDetectedEvent) event).getFamilyId());
        }
        return builder.build();
    }

    private ServerIamAuditRecord convertAuthenticationEvent(AuthenticationEvent event) {
        return ServerIamAuditRecord.builder()
                .family(ServerIamAuditEventFamily.AUTHENTICATION)
                .eventType(event.getEventType().getCode())
                .eventTime(event.getEventTime())
                .provider(event.getProvider())
                .username(event.getUsername())
                .userId(event.getUserId() == null ? null : String.valueOf(event.getUserId()))
                .ip(event.getIp())
                .userAgent(event.getUserAgent())
                .errorCode(event.getErrorCode())
                .failureCount(event.getFailureCount())
                .build();
    }

    private ServerIamAuditRecord convertSessionEvent(SessionLifecycleEvent event) {
        return ServerIamAuditRecord.builder()
                .family(ServerIamAuditEventFamily.SESSION)
                .eventType(event.getEventType().getCode())
                .cause(event.getCause().getCode())
                .eventTime(event.getEventTime())
                .sessionId(event.getSessionId())
                .userId(event.getUserId() == null ? null : String.valueOf(event.getUserId()))
                .username(event.getUsername())
                .revokedCount(event.getRevokedCount())
                .build();
    }

    private ServerIamAuditRecord convertAdminActionEvent(AdminActionEvent event) {
        return ServerIamAuditRecord.builder()
                .family(ServerIamAuditEventFamily.ADMIN_ACTION)
                .eventType(event.getAction().getCode())
                .eventTime(event.getEventTime())
                .subjectType(event.getSubjectType().getCode())
                .subjectId(event.getSubjectId())
                .subjectName(event.getSubjectName())
                .operator(event.getOperator())
                .detail(event.getDetail())
                .build();
    }
}
