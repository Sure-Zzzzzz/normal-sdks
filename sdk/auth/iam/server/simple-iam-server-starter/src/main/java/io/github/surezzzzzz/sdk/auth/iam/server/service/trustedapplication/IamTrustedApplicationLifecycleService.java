package io.github.surezzzzzz.sdk.auth.iam.server.service.trustedapplication;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.dto.trustedapplication.response.TrustedApplicationCleanupOperationResponse;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationCleanupOperationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.TrustedApplicationCleanupOperationState;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminActionType;
import io.github.surezzzzzz.sdk.auth.iam.server.event.AdminSubjectType;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.publisher.IamAuditEventPublisher;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamRoleAuthorizationRuleRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.manifest.IamApplicationPermissionManifestRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.portal.IamTrustedApplicationMenuRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.portal.IamTrustedApplicationPortalRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.resource.IamResourceVerificationClientRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationCleanupOperationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.service.authorization.IamApplicationAuthorizationStateService;
import io.github.surezzzzzz.sdk.auth.iam.server.service.portal.IamPortalApplicationOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * 可信应用全局生命周期服务。
 *
 * <p>删除请求只负责停用应用、推进安全纪元并落操作记录。后续授权清理和物理删除在独立
 * 事务中异步执行，避免管理端请求超时且确保中断后可恢复。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamTrustedApplicationLifecycleService {

    private static final String ACTION_DELETE = "DELETE";
    private static final long INITIAL_CURSOR_VALUE = 0L;
    private static final long INITIAL_ATTEMPT_COUNT = 0L;
    private static final long SECURITY_EPOCH_INITIAL_VALUE = 1L;
    private static final String FAILURE_CATEGORY_UNKNOWN = "UNKNOWN";
    private static final String SQL_LIST_REGISTERED_CLIENT_ROWS =
            "SELECT id, client_id FROM oauth2_registered_client WHERE application_id = ? ORDER BY id ASC";
    private static final String SQL_DELETE_REGISTERED_CLIENTS =
            "DELETE FROM oauth2_registered_client WHERE application_id = ?";
    private static final ObjectMapper SNAPSHOT_OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<List<String>>() {
    };
    private static final List<TrustedApplicationCleanupOperationState> MUTATING_OPERATION_STATES = Arrays.asList(
            TrustedApplicationCleanupOperationState.PENDING,
            TrustedApplicationCleanupOperationState.RUNNING,
            TrustedApplicationCleanupOperationState.RETRYING,
            TrustedApplicationCleanupOperationState.FAILED);
    private static final List<TrustedApplicationCleanupOperationState> READY_OPERATION_STATES = Arrays.asList(
            TrustedApplicationCleanupOperationState.PENDING,
            TrustedApplicationCleanupOperationState.RETRYING);

    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final IamTrustedApplicationCleanupOperationRepository cleanupOperationRepository;
    private final IamTrustedApplicationPortalRepository trustedApplicationPortalRepository;
    private final IamTrustedApplicationMenuRepository trustedApplicationMenuRepository;
    private final IamApplicationPermissionManifestRepository manifestRepository;
    private final IamRoleAuthorizationRuleRepository ruleRepository;
    private final IamApplicationAuthorizationRepository applicationAuthorizationRepository;
    private final IamResourceVerificationClientRepository resourceVerificationClientRepository;
    private final IamPortalApplicationOrderService portalApplicationOrderService;
    private final IamTrustedApplicationAuthorizationCleanupService authorizationCleanupService;
    private final IamTrustedApplicationMutationGuard mutationGuard;
    private final IamApplicationAuthorizationStateService authorizationStateService;
    private final JdbcTemplate jdbcTemplate;
    private final IamAuditEventPublisher auditEventPublisher;

    /**
     * 受理删除：同一应用处于删除中的重复请求返回同一操作；FAILED 需要显式 retry，
     * 不会被新的 DELETE 隐式重启。
     */
    @Transactional
    public TrustedApplicationCleanupOperationResponse acceptDelete(IamTrustedApplicationEntity application) {
        final Long applicationId = application.getId();
        application = trustedApplicationRepository.findByIdForUpdate(applicationId).orElseThrow(
                () -> new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_NOT_FOUND,
                        String.format(ServerErrorMessage.TRUSTED_APPLICATION_NOT_FOUND_BY_ID, applicationId)));
        Optional<IamTrustedApplicationCleanupOperationEntity> existing = cleanupOperationRepository
                .findFirstByApplicationIdAndStateInOrderByIdDesc(application.getId(), MUTATING_OPERATION_STATES);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }
        suspendInternal(application);
        List<ClientRow> clients = listClientRows(application.getId());
        Instant now = Instant.now();
        IamTrustedApplicationCleanupOperationEntity operation = new IamTrustedApplicationCleanupOperationEntity();
        operation.setApplicationId(application.getId());
        operation.setAction(ACTION_DELETE);
        operation.setState(TrustedApplicationCleanupOperationState.PENDING);
        operation.setRegisteredClientIdsJson(writeRegisteredClientIdSnapshot(clients));
        operation.setCursorValue(INITIAL_CURSOR_VALUE);
        operation.setAttemptCount(INITIAL_ATTEMPT_COUNT);
        operation.setAcceptedAt(now);
        operation.setUpdatedAt(now);
        operation = cleanupOperationRepository.save(operation);
        auditEventPublisher.publishAdminAction(AdminActionType.UPDATED, AdminSubjectType.APPLICATION,
                String.valueOf(application.getId()), application.getApplicationCode(),
                "cleanupOperationId=" + operation.getId() + ", action=" + ACTION_DELETE);
        return toResponse(operation);
    }

    /**
     * 停用应用，立即使已有 OAuth 授权失效。
     */
    @Transactional
    public void suspend(IamTrustedApplicationEntity application) {
        requireMutable(application.getId());
        suspendInternal(application);
    }

    /**
     * 恢复应用；恢复同样推进安全纪元，旧授权和 consent 永不复活。
     */
    @Transactional
    public void resume(IamTrustedApplicationEntity application) {
        requireMutable(application.getId());
        if (isActive(application)) {
            return;
        }
        application.setStatus(SimpleIamServerConstant.STATUS_ACTIVE);
        application.setApplicationSecurityEpoch(nextSecurityEpoch(application.getApplicationSecurityEpoch()));
        application.setUpdatedAt(Instant.now());
        trustedApplicationRepository.save(application);
        authorizationStateService.advanceAuthorizationEpoch(application.getId());
        authorizationStateService.advanceOwnerInheritedAccessEpoch(application.getId());
    }

    /**
     * 所有配置写入口在删除中或删除失败待人工处理时拒绝修改。
     */
    public void requireMutable(Long applicationId) {
        mutationGuard.requireMutable(applicationId);
    }

    /**
     * 查询异步删除操作。
     */
    public TrustedApplicationCleanupOperationResponse getOperation(Long operationId) {
        return toResponse(requireOperation(operationId));
    }

    /**
     * FAILED 只能由管理端显式重试，保持应用停用状态。
     */
    @Transactional
    public TrustedApplicationCleanupOperationResponse retryOperation(Long operationId) {
        IamTrustedApplicationCleanupOperationEntity operation = requireOperation(operationId);
        if (operation.getState() != TrustedApplicationCleanupOperationState.FAILED) {
            throw new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_CLEANUP_RETRY_NOT_ALLOWED,
                    String.format(ServerErrorMessage.TRUSTED_APPLICATION_CLEANUP_RETRY_NOT_ALLOWED, operationId));
        }
        operation.setState(TrustedApplicationCleanupOperationState.RETRYING);
        operation.setFailureCategory(null);
        operation.setLeaseUntil(null);
        operation.setLeaseOwner(null);
        operation.setUpdatedAt(Instant.now());
        return toResponse(cleanupOperationRepository.save(operation));
    }

    /**
     * 由后台调度器触发，一次仅领取一个操作，避免长事务占用调度线程。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IamTrustedApplicationCleanupLease claimNextOperation() {
        List<IamTrustedApplicationCleanupOperationEntity> operations = cleanupOperationRepository
                .findProcessableOperations(READY_OPERATION_STATES,
                        TrustedApplicationCleanupOperationState.RUNNING, Instant.now(), PageRequest.of(0, 1));
        if (operations.isEmpty()) {
            return null;
        }
        Instant now = Instant.now();
        String leaseOwner = UUID.randomUUID().toString();
        int updated = cleanupOperationRepository.claimForProcessing(operations.get(0).getId(),
                READY_OPERATION_STATES, TrustedApplicationCleanupOperationState.RUNNING, now,
                now.plus(SimpleIamServerConstant.TRUSTED_APPLICATION_CLEANUP_LEASE_SECONDS,
                        ChronoUnit.SECONDS), leaseOwner);
        return updated == 1 ? new IamTrustedApplicationCleanupLease(operations.get(0).getId(), leaseOwner) : null;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processClaimedOperation(IamTrustedApplicationCleanupLease lease) {
        IamTrustedApplicationCleanupOperationEntity operation = requireOperation(lease.getOperationId());
        if (!isLeaseCurrent(operation, lease)) {
            return;
        }
        try {
            List<String> registeredClientIds = readRegisteredClientIdSnapshot(operation);
            long removed = authorizationCleanupService.removeAuthorizationBatch(registeredClientIds,
                    SimpleIamServerConstant.TRUSTED_APPLICATION_CLEANUP_BATCH_SIZE);
            if (removed > 0L) {
                operation.setCursorValue(operation.getCursorValue() + removed);
                operation.setUpdatedAt(Instant.now());
                cleanupOperationRepository.save(operation);
                return;
            }
            finalizeDeletion(operation, registeredClientIds, lease);
        } catch (RuntimeException exception) {
            if (!isLeaseCurrent(operation, lease)) {
                return;
            }
            operation.setState(TrustedApplicationCleanupOperationState.FAILED);
            operation.setLeaseUntil(null);
            operation.setLeaseOwner(null);
            operation.setFailureCategory(failureCategory(exception));
            operation.setUpdatedAt(Instant.now());
            cleanupOperationRepository.save(operation);
            log.error("可信应用异步删除失败：operationId={}, applicationId={}, category={}",
                    lease.getOperationId(), operation.getApplicationId(), operation.getFailureCategory(), exception);
        }
    }

    private void finalizeDeletion(IamTrustedApplicationCleanupOperationEntity operation,
                                  List<String> registeredClientIds,
                                  IamTrustedApplicationCleanupLease lease) {
        if (!isLeaseCurrent(operation, lease)) {
            return;
        }
        IamTrustedApplicationEntity application = trustedApplicationRepository.findById(operation.getApplicationId())
                .orElse(null);
        if (application == null) {
            markCompleted(operation);
            return;
        }
        List<ClientRow> clientRows = listClientRows(application.getId());
        authorizationCleanupService.removeConsentRows(registeredClientIds, clientIds(clientRows));
        boolean portalConfigured = trustedApplicationPortalRepository.existsById(application.getId());
        if (portalConfigured) {
            portalApplicationOrderService.preparePortalRemoval(application.getId());
            trustedApplicationPortalRepository.deleteById(application.getId());
        }
        trustedApplicationMenuRepository.deleteByApplicationId(application.getId());
        manifestRepository.deleteByApplicationId(application.getId());
        ruleRepository.deleteByApplicationId(application.getId());
        applicationAuthorizationRepository.deleteByApplicationId(application.getId());
        authorizationStateService.deleteState(application.getId());
        resourceVerificationClientRepository.deleteByApplicationId(application.getId());
        jdbcTemplate.update(SQL_DELETE_REGISTERED_CLIENTS, application.getId());
        trustedApplicationRepository.delete(application);
        markCompleted(operation);
        auditEventPublisher.publishAdminAction(AdminActionType.DELETED, AdminSubjectType.APPLICATION,
                String.valueOf(operation.getApplicationId()), application.getApplicationCode(),
                "cleanupOperationId=" + operation.getId() + ", processedAuthorizationCount="
                        + operation.getCursorValue());
    }

    private void markCompleted(IamTrustedApplicationCleanupOperationEntity operation) {
        operation.setState(TrustedApplicationCleanupOperationState.COMPLETED);
        operation.setFailureCategory(null);
        operation.setLeaseUntil(null);
        operation.setLeaseOwner(null);
        operation.setUpdatedAt(Instant.now());
        cleanupOperationRepository.save(operation);
    }

    private void suspendInternal(IamTrustedApplicationEntity application) {
        if (!isActive(application)) {
            return;
        }
        application.setStatus(SimpleIamServerConstant.STATUS_INACTIVE);
        application.setApplicationSecurityEpoch(nextSecurityEpoch(application.getApplicationSecurityEpoch()));
        application.setUpdatedAt(Instant.now());
        trustedApplicationRepository.save(application);
        authorizationStateService.advanceAuthorizationEpoch(application.getId());
        authorizationStateService.advanceOwnerInheritedAccessEpoch(application.getId());
    }

    private IamTrustedApplicationCleanupOperationEntity requireOperation(Long operationId) {
        return cleanupOperationRepository.findById(operationId).orElseThrow(
                () -> new SimpleIamServerException(ErrorCode.TRUSTED_APPLICATION_CLEANUP_OPERATION_NOT_FOUND,
                        String.format(ServerErrorMessage.TRUSTED_APPLICATION_CLEANUP_OPERATION_NOT_FOUND,
                                operationId)));
    }

    private List<ClientRow> listClientRows(Long applicationId) {
        return jdbcTemplate.query(SQL_LIST_REGISTERED_CLIENT_ROWS, (resultSet, rowNum) -> {
            ClientRow row = new ClientRow();
            row.registeredClientId = resultSet.getString("id");
            row.clientId = resultSet.getString("client_id");
            return row;
        }, applicationId);
    }

    private String writeRegisteredClientIdSnapshot(List<ClientRow> clients) {
        try {
            return SNAPSHOT_OBJECT_MAPPER.writeValueAsString(registeredClientIds(clients));
        } catch (Exception exception) {
            throw new SimpleIamServerException(ErrorCode.TOKEN_OPERATION_FAILED,
                    String.format(ServerErrorMessage.TOKEN_OPERATION_FAILED, "删除客户端快照序列化失败"));
        }
    }

    private List<String> readRegisteredClientIdSnapshot(IamTrustedApplicationCleanupOperationEntity operation) {
        try {
            return SNAPSHOT_OBJECT_MAPPER.readValue(operation.getRegisteredClientIdsJson(), STRING_LIST_TYPE);
        } catch (Exception exception) {
            throw new SimpleIamServerException(ErrorCode.TOKEN_OPERATION_FAILED,
                    String.format(ServerErrorMessage.TOKEN_OPERATION_FAILED, "删除客户端快照解析失败"));
        }
    }

    private List<String> registeredClientIds(List<ClientRow> clients) {
        if (clients.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.ArrayList<String> result = new java.util.ArrayList<String>();
        for (ClientRow client : clients) {
            result.add(client.registeredClientId);
        }
        return result;
    }

    private List<String> clientIds(List<ClientRow> clients) {
        if (clients.isEmpty()) {
            return Collections.emptyList();
        }
        java.util.ArrayList<String> result = new java.util.ArrayList<String>();
        for (ClientRow client : clients) {
            result.add(client.clientId);
        }
        return result;
    }

    private boolean isActive(IamTrustedApplicationEntity application) {
        return application.getStatus() != null
                && SimpleIamServerConstant.STATUS_ACTIVE == application.getStatus().intValue();
    }

    private long nextSecurityEpoch(Long currentEpoch) {
        return (currentEpoch == null ? SECURITY_EPOCH_INITIAL_VALUE : currentEpoch) + 1L;
    }

    private String failureCategory(RuntimeException exception) {
        String simpleName = exception.getClass().getSimpleName();
        return simpleName == null || simpleName.isEmpty() ? FAILURE_CATEGORY_UNKNOWN : simpleName;
    }

    private boolean isLeaseCurrent(IamTrustedApplicationCleanupOperationEntity operation,
                                   IamTrustedApplicationCleanupLease lease) {
        return operation.getState() == TrustedApplicationCleanupOperationState.RUNNING
                && operation.getLeaseUntil() != null
                && operation.getLeaseUntil().isAfter(Instant.now())
                && lease.getLeaseOwner().equals(operation.getLeaseOwner());
    }

    private TrustedApplicationCleanupOperationResponse toResponse(
            IamTrustedApplicationCleanupOperationEntity operation) {
        return TrustedApplicationCleanupOperationResponse.builder()
                .operationId(operation.getId())
                .applicationId(operation.getApplicationId())
                .action(operation.getAction())
                .state(operation.getState().name())
                .retryable(operation.getState() == TrustedApplicationCleanupOperationState.FAILED)
                .processedAuthorizationCount(operation.getCursorValue())
                .attemptCount(operation.getAttemptCount())
                .failureCategory(operation.getFailureCategory())
                .acceptedAt(operation.getAcceptedAt())
                .updatedAt(operation.getUpdatedAt())
                .build();
    }

    private static class ClientRow {
        private String registeredClientId;
        private String clientId;
    }
}
