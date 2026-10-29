package io.github.surezzzzzz.sdk.crm.server.service;

import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import io.github.surezzzzzz.sdk.crm.server.constant.SimpleCrmServerConstant;
import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmCommandType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.type.CrmResourceType;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.port.idempotency.CrmCreateIdempotencyPort;
import io.github.surezzzzzz.sdk.crm.server.core.port.idempotency.CrmIdempotencyPort;
import io.github.surezzzzzz.sdk.crm.server.core.port.idempotency.CrmReplayableIdempotencyPort;
import io.github.surezzzzzz.sdk.crm.server.entity.SimpleCrmServerIdempotencyEntity;
import io.github.surezzzzzz.sdk.crm.server.repository.SimpleCrmServerJdbcIdempotencyRepository;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerIdempotencyResultCodec;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;

/**
 * 幂等 Port 的 JDBC 实现：稳定范围键 = owner|command|resourceType[|targetId]|idempotencyKey；
 * 首成功记录与业务事实同事务提交（事务由调用方命令编排提供）。
 * 履约命令在订单锁之后读取当前幂等记录，等待锁前的事务快照不作为重放依据。
 *
 * @author surezzzzzz
 */
@SimpleCrmServerComponent
public class SimpleCrmServerJdbcIdempotencyPort implements CrmCreateIdempotencyPort, CrmReplayableIdempotencyPort {

    private final SimpleCrmServerJdbcIdempotencyRepository repository;

    /**
     * 创建 JDBC 幂等 Port。
     *
     * @param repository 幂等仓储
     */
    public SimpleCrmServerJdbcIdempotencyPort(SimpleCrmServerJdbcIdempotencyRepository repository) {
        this.repository = repository;
    }

    @Override
    public String execute(CrmOwner actor, CrmCommandMetadata metadata,
                          CrmCommandType commandType, CrmResourceType targetResourceType,
                          String requestDigest, CrmCreateIdempotentCallback callback) {
        validateCreatePair(commandType, targetResourceType);
        requireTransaction();
        String scopeKey = createScope(actor, metadata, commandType, targetResourceType, null);
        if (!repository.claim(new SimpleCrmServerIdempotencyEntity(scopeKey, requestDigest,
                null, null, Instant.now()))) {
            SimpleCrmServerIdempotencyEntity existing = existing(scopeKey, requestDigest);
            if (existing.getFirstResourceId() == null) {
                throw new IllegalStateException(SimpleCrmServerConstant.ERROR_IDEMPOTENCY_CREATE_RESULT_MISSING);
            }
            return existing.getFirstResourceId();
        }
        String resourceId = callback.execute();
        if (resourceId == null || resourceId.trim().isEmpty()) {
            throw CrmException.validation("resourceId");
        }
        repository.completeCreate(scopeKey, requestDigest, resourceId);
        return resourceId;
    }

    @Override
    public <T> T execute(CrmOwner actor, CrmCommandMetadata metadata, CrmCommandType commandType,
                         CrmResourceType targetResourceType, String targetResourceId,
                         String requestDigest, CrmIdempotencyPort.CrmIdempotentCallback<T> callback) {
        return callback.execute();
    }

    @Override
    public <T> T execute(CrmOwner actor, CrmCommandMetadata metadata, CrmCommandType commandType,
                         CrmResourceType targetResourceType, String targetResourceId,
                         String requestDigest, CrmIdempotencyPort.CrmIdempotentCallback<T> callback,
                         CrmIdempotentReplayCallback<T> replayCallback) {
        validateKnownTargetPair(commandType, targetResourceType);
        requireTransaction();
        String scopeKey = createScope(actor, metadata, commandType, targetResourceType,
                targetResourceId);
        if (!repository.claim(new SimpleCrmServerIdempotencyEntity(scopeKey, requestDigest,
                null, null, Instant.now()))) {
            SimpleCrmServerIdempotencyEntity existing = existing(scopeKey, requestDigest);
            // 业务入口先按当前权限加载目标；持久化首结果避免后续状态覆盖旧请求的响应。
            // 仅兼容开发期未保存快照的记录，不把当前事实回填为首次事实。
            String firstResultJson = existing.getFirstResultJson();
            return firstResultJson == null ? replayCallback.execute()
                    : SimpleCrmServerIdempotencyResultCodec.read(firstResultJson, commandType);
        }
        T result = callback.execute();
        repository.completeResult(scopeKey, requestDigest,
                SimpleCrmServerIdempotencyResultCodec.write(result));
        return result;
    }

    private SimpleCrmServerIdempotencyEntity existing(String scopeKey, String requestDigest) {
        SimpleCrmServerIdempotencyEntity existing = repository.findCurrent(scopeKey)
                .orElseThrow(() -> new IllegalStateException(SimpleCrmServerConstant.ERROR_IDEMPOTENCY_RECORD_MISSING));
        if (!existing.getRequestDigest().equals(requestDigest)) {
            throw new CrmException(CrmErrorCode.IDEMPOTENCY_KEY_REUSED_WITH_DIFFERENT_PAYLOAD,
                    "requestDigest");
        }
        return existing;
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException(SimpleCrmServerConstant.ERROR_IDEMPOTENCY_TRANSACTION_REQUIRED);
        }
    }

    private String createScope(CrmOwner actor, CrmCommandMetadata metadata,
                               CrmCommandType commandType, CrmResourceType resourceType,
                               String targetResourceId) {
        StringBuilder key = new StringBuilder(actor.getOwner()).append('|')
                .append(commandType.getCode()).append('|').append(resourceType.getCode());
        if (targetResourceId != null) {
            key.append('|').append(targetResourceId);
        }
        return key.append('|').append(metadata.getIdempotencyKey()).toString();
    }

    private void validateCreatePair(CrmCommandType commandType, CrmResourceType resourceType) {
        boolean valid = commandType == CrmCommandType.CREATE_CUSTOMER
                && resourceType == CrmResourceType.CUSTOMER
                || commandType == CrmCommandType.CREATE_CONTACT && resourceType == CrmResourceType.CONTACT
                || commandType == CrmCommandType.CREATE_OFFERING
                && resourceType == CrmResourceType.OFFERING
                || commandType == CrmCommandType.CREATE_QUOTATION
                && resourceType == CrmResourceType.QUOTATION
                || commandType == CrmCommandType.CREATE_OPPORTUNITY
                && resourceType == CrmResourceType.OPPORTUNITY
                || commandType == CrmCommandType.RENEGOTIATE_QUOTATION
                && resourceType == CrmResourceType.QUOTATION;
        if (!valid) {
            throw CrmException.validation("commandType/targetResourceType");
        }
    }

    private void validateKnownTargetPair(CrmCommandType commandType, CrmResourceType resourceType) {
        boolean valid = commandType == CrmCommandType.ISSUE_QUOTATION && resourceType == CrmResourceType.QUOTATION
                || commandType == CrmCommandType.CONFIRM_QUOTATION && resourceType == CrmResourceType.QUOTATION
                || commandType == CrmCommandType.WITHDRAW_QUOTATION && resourceType == CrmResourceType.QUOTATION
                || commandType == CrmCommandType.EDIT_QUOTATION && resourceType == CrmResourceType.QUOTATION
                || commandType == CrmCommandType.TRANSFER_QUOTATION && resourceType == CrmResourceType.QUOTATION
                || commandType == CrmCommandType.TRANSITION_OPPORTUNITY && resourceType == CrmResourceType.OPPORTUNITY
                || commandType == CrmCommandType.TRANSFER_OPPORTUNITY && resourceType == CrmResourceType.OPPORTUNITY
                || commandType == CrmCommandType.DEACTIVATE_CUSTOMER && resourceType == CrmResourceType.CUSTOMER
                || commandType == CrmCommandType.ACTIVATE_CUSTOMER && resourceType == CrmResourceType.CUSTOMER
                || commandType == CrmCommandType.MERGE_CUSTOMER && resourceType == CrmResourceType.CUSTOMER
                || commandType == CrmCommandType.TRANSFER_CUSTOMER && resourceType == CrmResourceType.CUSTOMER
                || commandType == CrmCommandType.PUBLISH_OFFERING && resourceType == CrmResourceType.OFFERING
                || commandType == CrmCommandType.DISABLE_OFFERING && resourceType == CrmResourceType.OFFERING
                || commandType == CrmCommandType.REPORT_FULFILLMENT_RESULT
                && resourceType == CrmResourceType.FULFILLMENT_ITEM
                || commandType == CrmCommandType.RESET_FULFILLMENT_ITEM
                && resourceType == CrmResourceType.FULFILLMENT_ITEM
                || commandType == CrmCommandType.UPSERT_FULFILLMENT_CONSUMER
                && resourceType == CrmResourceType.FULFILLMENT_CONSUMER;
        if (!valid) {
            throw CrmException.validation("commandType/targetResourceType");
        }
    }
}
