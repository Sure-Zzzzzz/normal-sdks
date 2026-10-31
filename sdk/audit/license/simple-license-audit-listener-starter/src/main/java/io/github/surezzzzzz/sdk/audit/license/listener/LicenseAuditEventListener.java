package io.github.surezzzzzz.sdk.audit.license.listener;

import io.github.surezzzzzz.sdk.audit.license.constant.SmartLicenseAuditConstant;
import io.github.surezzzzzz.sdk.audit.license.handler.LicenseAuditHandler;
import io.github.surezzzzzz.sdk.audit.license.model.LicenseAuditRecord;
import io.github.surezzzzzz.sdk.audit.license.provider.LicenseAuditTraceIdProvider;
import io.github.surezzzzzz.sdk.license.server.event.LicenseAuditEvents;
import io.github.surezzzzzz.sdk.license.server.event.RoleProjectionChanged;
import io.github.surezzzzzz.sdk.license.server.event.QuotaOperationCompleted;
import io.github.surezzzzzz.sdk.license.server.event.RevocationPackageCompleted;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;

import java.util.List;

/**
 * License 业务审计事件监听器（2.0 新事件族）
 *
 * <p>异步消费 License 提交后领域事件并分发审计记录；任何处理器异常都不影响
 * 其余处理器和 License 主链路。旧 1.0 四事件（tenantId 语义）随领域重构废弃。</p>
 *
 * @author surezzzzzz
 * @since 2.0.0
 */
@Slf4j
public class LicenseAuditEventListener {

    private final List<LicenseAuditHandler> auditHandlers;
    private final LicenseAuditTraceIdProvider traceIdProvider;

    /**
     * 创建 License 审计监听器
     *
     * @param auditHandlers   宿主注册的审计处理器
     * @param traceIdProvider 链路追踪ID提供者，可缺省
     */
    public LicenseAuditEventListener(
            List<LicenseAuditHandler> auditHandlers,
            @Autowired(required = false) LicenseAuditTraceIdProvider traceIdProvider) {
        this.auditHandlers = auditHandlers;
        this.traceIdProvider = traceIdProvider;
    }

    /**
     * 审计客户授权创建事件
     *
     * @param event 授权创建事件
     */
    @EventListener
    @Async
    public void onGrantCreated(LicenseAuditEvents.GrantCreated event) {
        dispatch(LicenseAuditRecord.builder()
                .eventType(SmartLicenseAuditConstant.EVENT_TYPE_GRANT_CREATED)
                .eventId(event.eventId)
                .eventVersion(event.eventVersion)
                .timestamp(event.occurredAt.toEpochMilli())
                .principalId(event.principalId)
                .requestId(event.requestId)
                .grantId(event.grantId)
                .customerId(event.customerId)
                .productId(event.productId)
                .subjectKind(event.subjectKind)
                .mode(event.mode)
                .modelVersionId(event.modelVersionId)
                .traceId(getTraceId())
                .build());
    }

    /**
     * 审计授权管理状态变更事件
     *
     * @param event 状态变更事件
     */
    @EventListener
    @Async
    public void onGrantStateChanged(LicenseAuditEvents.GrantStateChanged event) {
        dispatch(LicenseAuditRecord.builder()
                .eventType(SmartLicenseAuditConstant.EVENT_TYPE_GRANT_STATE_CHANGED)
                .eventId(event.eventId)
                .eventVersion(event.eventVersion)
                .timestamp(event.occurredAt.toEpochMilli())
                .principalId(event.principalId)
                .requestId(event.requestId)
                .grantId(event.grantId)
                .fromState(event.fromState)
                .toState(event.toState)
                .traceId(getTraceId())
                .build());
    }

    /**
     * 审计离线凭据签发就绪事件
     *
     * @param event 签发就绪事件
     */
    @EventListener
    @Async
    public void onCredentialIssued(LicenseAuditEvents.CredentialIssued event) {
        dispatch(LicenseAuditRecord.builder()
                .eventType(SmartLicenseAuditConstant.EVENT_TYPE_CREDENTIAL_ISSUED)
                .eventId(event.eventId)
                .eventVersion(event.eventVersion)
                .timestamp(event.occurredAt.toEpochMilli())
                .principalId(event.principalId)
                .requestId(event.requestId)
                .grantId(event.grantId)
                .credentialId(event.credentialId)
                .kid(event.kid)
                .traceId(getTraceId())
                .build());
    }

    /**
     * 审计吊销创建事件
     *
     * @param event 吊销创建事件
     */
    @EventListener
    @Async
    public void onRevocationCreated(LicenseAuditEvents.RevocationCreated event) {
        dispatch(LicenseAuditRecord.builder()
                .eventType(SmartLicenseAuditConstant.EVENT_TYPE_REVOCATION_CREATED)
                .eventId(event.eventId)
                .eventVersion(event.eventVersion)
                .timestamp(event.occurredAt.toEpochMilli())
                .principalId(event.principalId)
                .requestId(event.requestId)
                .revocationId(event.revocationId)
                .targetKind(event.targetKind)
                .targetId(event.targetId)
                .reasonCategory(event.reasonCategory)
                .traceId(getTraceId())
                .build());
    }

    /**
     * 审计条件模型发布事件
     *
     * @param event 模型发布事件
     */
    @EventListener
    @Async
    public void onModelPublished(LicenseAuditEvents.ModelPublished event) {
        dispatch(LicenseAuditRecord.builder()
                .eventType(SmartLicenseAuditConstant.EVENT_TYPE_MODEL_PUBLISHED)
                .eventId(event.eventId)
                .eventVersion(event.eventVersion)
                .timestamp(event.occurredAt.toEpochMilli())
                .principalId(event.principalId)
                .requestId(event.requestId)
                .modelVersionId(event.modelVersionId)
                .productId(event.productId)
                .modelVersion(event.modelVersion)
                .traceId(getTraceId())
                .build());
    }

    /**
     * 审计角色协作计划与安全状态；只记录协调标识，不推断人员身份，不复制权限或目录内容。
     *
     * @param event 提交后的角色协作状态事件
     */
    @EventListener
    @Async
    public void onRoleProjectionChanged(RoleProjectionChanged event) {
        dispatch(LicenseAuditRecord.builder()
                .eventType(SmartLicenseAuditConstant.EVENT_TYPE_ROLE_PROJECTION_CHANGED)
                .eventId(event.getEventId())
                .eventVersion(event.getEventVersion())
                .timestamp(event.getOccurredAt().toEpochMilli())
                .projectionId(event.getProjectionId())
                .commandId(event.getCommandId())
                .desiredRevision(event.getDesiredRevision())
                .executionMode(event.getExecutionMode())
                .toState(event.getState())
                .reasonCategory(event.getReasonCategory())
                .traceId(getTraceId())
                .build());
    }

    /** 消费额度事务提交后的观察；重放不解释为新的消费，处理器仍各自隔离异常。 */
    @EventListener
    @Async
    public void onQuotaOperationCompleted(QuotaOperationCompleted event) {
        dispatch(LicenseAuditRecord.builder()
                .eventType(SmartLicenseAuditConstant.EVENT_TYPE_QUOTA_OPERATION_COMPLETED)
                .eventId(event.getEventId()).eventVersion(event.getEventVersion())
                .timestamp(event.getOccurredAt().toEpochMilli()).principalId(event.getPrincipalId())
                .requestId(event.getRequestId()).grantId(event.getGrantId()).reservationId(event.getReservationId())
                .quotaKind(event.getKind()).toState(event.getState()).reservationFence(event.getFence())
                .quotaOperation(event.getOperation().name()).traceId(getTraceId()).build());
    }

    /** 记录已提交吊销包终态，不借授权或凭据字段承载包标识。 */
    @EventListener
    @Async
    public void onRevocationPackageCompleted(RevocationPackageCompleted event) {
        dispatch(LicenseAuditRecord.builder()
                .eventType(SmartLicenseAuditConstant.EVENT_TYPE_REVOCATION_PACKAGE_COMPLETED)
                .eventId(event.getEventId())
                .eventVersion(event.getEventVersion())
                .timestamp(event.getOccurredAt().toEpochMilli())
                .packageId(event.getPackageId())
                .operationId(event.getOperationId())
                .productId(event.getProductId())
                .packageSequence(event.getSequence())
                .toState(event.getState())
                .reasonCategory(event.getReasonCategory())
                .traceId(getTraceId())
                .build());
    }

    private void dispatch(LicenseAuditRecord record) {
        try {
            for (LicenseAuditHandler handler : auditHandlers) {
                try {
                    handler.handle(record);
                } catch (Exception exception) {
                    // 扩展处理器异常可能包含令牌或完整业务载荷，只记录可关联的安全元数据。
                    log.error("License审计处理器执行失败：eventId={}，handlerType={}，exceptionType={}",
                            record.getEventId(), handler.getClass().getName(), exception.getClass().getName());
                }
            }
        } catch (Exception exception) {
            log.error("License业务审计事件处理失败：eventId={}，exceptionType={}",
                    record.getEventId(), exception.getClass().getName());
        }
    }

    private String getTraceId() {
        if (traceIdProvider == null) {
            return null;
        }
        try {
            return traceIdProvider.getTraceId();
        } catch (Exception exception) {
            log.warn("获取License审计链路标识失败：exceptionType={}", exception.getClass().getName());
            return null;
        }
    }
}
