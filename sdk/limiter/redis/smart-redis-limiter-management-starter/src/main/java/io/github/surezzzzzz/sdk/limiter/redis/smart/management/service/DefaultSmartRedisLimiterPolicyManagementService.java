package io.github.surezzzzzz.sdk.limiter.redis.smart.management.service;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.SmartRedisLimiterManagementOperation;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration.SmartRedisLimiterManagementProperties;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyCreateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.request.SmartRedisLimiterPolicyUpdateRequest;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyMutationResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyPageResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterPolicyResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.event.SmartRedisLimiterManagementEventPublisher;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementValidationException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterPolicyConflictException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterPolicyNotFoundException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterPolicyEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterPolicyLimitEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterPolicyRevisionEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyQuery;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.SmartRedisLimiterPolicyRepository;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.support.SmartRedisLimiterManagementMapper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.support.SmartRedisLimiterManagementTimeHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterManagementEventPayload;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterPolicyValidationHelper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 默认限流策略管理服务
 *
 * @author surezzzzzz
 */
public class DefaultSmartRedisLimiterPolicyManagementService
        implements SmartRedisLimiterPolicyManagementService {

    private final SmartRedisLimiterPolicyRepository repository;
    private final SmartRedisLimiterManagementEventPublisher eventPublisher;
    private final SmartRedisLimiterManagementProperties properties;

    /**
     * 构造策略管理服务
     *
     * @param repository     策略 Repository
     * @param eventPublisher 管理事件发布器
     * @param properties     management 配置
     */
    public DefaultSmartRedisLimiterPolicyManagementService(
            SmartRedisLimiterPolicyRepository repository,
            SmartRedisLimiterManagementEventPublisher eventPublisher,
            SmartRedisLimiterManagementProperties properties) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
        this.properties = properties;
    }

    @Override
    @Transactional
    public SmartRedisLimiterPolicyMutationResponse create(
            SmartRedisLimiterPolicyCreateRequest request, String operator) {
        SmartRedisLimiterPolicy afterPolicy = validateCreateRequest(request);
        String normalizedOperator = SmartRedisLimiterPolicyValidationHelper.normalizeOperator(operator);
        SmartRedisLimiterPolicyKey key = afterPolicy.getKey();
        SmartRedisLimiterPolicyRevisionEntity revisionEntity = lockRevision(key.getServiceCode());
        if (repository.findByIdentity(key.getServiceCode(), key.getResourceCode(), key.getSubject()) != null) {
            throw identityConflict();
        }
        Instant now = SmartRedisLimiterManagementTimeHelper.nowMillis();
        SmartRedisLimiterPolicyEntity entity = newEntity(afterPolicy,
                Boolean.TRUE.equals(request.getEnabled()), now);
        try {
            long id = repository.insert(entity);
            long revision = advanceRevision(revisionEntity, now);
            SmartRedisLimiterPolicyEntity saved = repository.findById(id);
            eventPublisher.publishAfterCommit(SmartRedisLimiterManagementEventPayload.builder()
                    .operation(SmartRedisLimiterManagementOperation.CREATE)
                    .policyKey(key)
                    .afterPolicy(afterPolicy)
                    .afterEnabled(saved.getEnabled())
                    .revision(revision)
                    .operator(normalizedOperator)
                    .occurredAt(now)
                    .build());
            return mutation(saved, null, revision, true);
        } catch (DuplicateKeyException ex) {
            throw identityConflict();
        }
    }

    @Override
    @Transactional
    public SmartRedisLimiterPolicyMutationResponse update(
            long id, SmartRedisLimiterPolicyUpdateRequest request, String operator) {
        requireRowVersion(request == null ? null : request.getExpectedRowVersion());
        SmartRedisLimiterPolicyEntity current = requirePolicy(id);
        SmartRedisLimiterPolicy beforePolicy = SmartRedisLimiterManagementMapper.toCorePolicy(current);
        SmartRedisLimiterPolicy afterPolicy = new SmartRedisLimiterPolicy(
                beforePolicy.getKey(), request.getLimits());
        String normalizedOperator = SmartRedisLimiterPolicyValidationHelper.normalizeOperator(operator);
        SmartRedisLimiterPolicyRevisionEntity revisionEntity = lockRevision(current.getServiceCode());
        current = requirePolicy(id);
        verifyVersion(current, request.getExpectedRowVersion());
        beforePolicy = SmartRedisLimiterManagementMapper.toCorePolicy(current);
        if (beforePolicy.equals(afterPolicy)) {
            return mutation(current, null, revisionEntity.getRevision(), false);
        }
        Instant now = SmartRedisLimiterManagementTimeHelper.nowMillis();
        if (!repository.replaceLimits(id, request.getExpectedRowVersion(), afterPolicy.getLimits(), now)) {
            throw versionConflict();
        }
        long revision = advanceRevision(revisionEntity, now);
        SmartRedisLimiterPolicyEntity saved = requirePolicy(id);
        eventPublisher.publishAfterCommit(SmartRedisLimiterManagementEventPayload.builder()
                .operation(SmartRedisLimiterManagementOperation.UPDATE)
                .policyKey(beforePolicy.getKey())
                .beforePolicy(beforePolicy)
                .afterPolicy(afterPolicy)
                .beforeEnabled(current.getEnabled())
                .afterEnabled(current.getEnabled())
                .revision(revision)
                .operator(normalizedOperator)
                .occurredAt(now)
                .build());
        return mutation(saved, null, revision, true);
    }

    @Override
    @Transactional
    public SmartRedisLimiterPolicyMutationResponse enable(long id, long rowVersion, String operator) {
        return changeState(id, rowVersion, true, operator);
    }

    @Override
    @Transactional
    public SmartRedisLimiterPolicyMutationResponse disable(long id, long rowVersion, String operator) {
        return changeState(id, rowVersion, false, operator);
    }

    @Override
    @Transactional
    public SmartRedisLimiterPolicyMutationResponse delete(long id, long rowVersion, String operator) {
        SmartRedisLimiterPolicyEntity current = requirePolicy(id);
        SmartRedisLimiterPolicyRevisionEntity revisionEntity = lockRevision(current.getServiceCode());
        current = requirePolicy(id);
        verifyVersion(current, rowVersion);
        SmartRedisLimiterPolicy beforePolicy = SmartRedisLimiterManagementMapper.toCorePolicy(current);
        String normalizedOperator = SmartRedisLimiterPolicyValidationHelper.normalizeOperator(operator);
        Instant now = SmartRedisLimiterManagementTimeHelper.nowMillis();
        if (!repository.delete(id, rowVersion)) {
            throw versionConflict();
        }
        long revision = advanceRevision(revisionEntity, now);
        eventPublisher.publishAfterCommit(SmartRedisLimiterManagementEventPayload.builder()
                .operation(SmartRedisLimiterManagementOperation.DELETE)
                .policyKey(beforePolicy.getKey())
                .beforePolicy(beforePolicy)
                .beforeEnabled(current.getEnabled())
                .revision(revision)
                .operator(normalizedOperator)
                .occurredAt(now)
                .build());
        return mutation(null, beforePolicy.getKey(), revision, true);
    }

    @Override
    @Transactional(readOnly = true)
    public SmartRedisLimiterPolicyResponse findById(long id) {
        return SmartRedisLimiterManagementMapper.toResponse(requirePolicy(id));
    }

    @Override
    @Transactional(readOnly = true)
    public SmartRedisLimiterPolicyPageResponse query(SmartRedisLimiterPolicyQuery query) {
        SmartRedisLimiterPolicyQuery normalized = normalizeQuery(query);
        List<SmartRedisLimiterPolicyResponse> items = new ArrayList<>();
        for (SmartRedisLimiterPolicyEntity entity : repository.query(normalized)) {
            items.add(SmartRedisLimiterManagementMapper.toResponse(entity));
        }
        long total = repository.count(normalized);
        int totalPages = total == 0 ? 0
                : (int) ((total + normalized.getSize() - 1) / normalized.getSize());
        return SmartRedisLimiterPolicyPageResponse.builder()
                .items(items)
                .page(normalized.getPage())
                .size(normalized.getSize())
                .totalElements(total)
                .totalPages(totalPages)
                .build();
    }

    private SmartRedisLimiterPolicyMutationResponse changeState(
            long id, long rowVersion, boolean enabled, String operator) {
        SmartRedisLimiterPolicyEntity current = requirePolicy(id);
        SmartRedisLimiterPolicyRevisionEntity revisionEntity = lockRevision(current.getServiceCode());
        current = requirePolicy(id);
        verifyVersion(current, rowVersion);
        if (Boolean.valueOf(enabled).equals(current.getEnabled())) {
            return mutation(current, null, revisionEntity.getRevision(), false);
        }
        SmartRedisLimiterPolicy policy = SmartRedisLimiterManagementMapper.toCorePolicy(current);
        String normalizedOperator = SmartRedisLimiterPolicyValidationHelper.normalizeOperator(operator);
        Instant now = SmartRedisLimiterManagementTimeHelper.nowMillis();
        if (!repository.updateEnabled(id, rowVersion, enabled, now)) {
            throw versionConflict();
        }
        long revision = advanceRevision(revisionEntity, now);
        SmartRedisLimiterPolicyEntity saved = requirePolicy(id);
        eventPublisher.publishAfterCommit(SmartRedisLimiterManagementEventPayload.builder()
                .operation(enabled ? SmartRedisLimiterManagementOperation.ENABLE
                        : SmartRedisLimiterManagementOperation.DISABLE)
                .policyKey(policy.getKey())
                .beforePolicy(policy)
                .afterPolicy(policy)
                .beforeEnabled(!enabled)
                .afterEnabled(enabled)
                .revision(revision)
                .operator(normalizedOperator)
                .occurredAt(now)
                .build());
        return mutation(saved, null, revision, true);
    }

    private SmartRedisLimiterPolicy validateCreateRequest(SmartRedisLimiterPolicyCreateRequest request) {
        if (request == null || request.getKey() == null || request.getLimits() == null) {
            throw validationException();
        }
        return new SmartRedisLimiterPolicy(request.getKey(), request.getLimits());
    }

    private SmartRedisLimiterPolicyRevisionEntity lockRevision(String serviceCode) {
        repository.initializeRevision(serviceCode);
        SmartRedisLimiterPolicyRevisionEntity revision = repository.lockRevision(serviceCode);
        if (revision == null) {
            throw new SmartRedisLimiterManagementException(
                    ErrorCode.PERSISTENCE_FAILED, ErrorMessage.PERSISTENCE_FAILED);
        }
        return revision;
    }

    private long advanceRevision(SmartRedisLimiterPolicyRevisionEntity current, Instant now) {
        if (current.getRevision() == Long.MAX_VALUE) {
            throw new SmartRedisLimiterPolicyConflictException(
                    ErrorCode.REVISION_OVERFLOW, ErrorMessage.REVISION_OVERFLOW);
        }
        long revision = current.getRevision() + SmartRedisLimiterManagementConstant.REVISION_INCREMENT;
        repository.updateRevision(current.getServiceCode(), revision, now);
        return revision;
    }

    private SmartRedisLimiterPolicyEntity requirePolicy(long id) {
        SmartRedisLimiterPolicyEntity entity = repository.findById(id);
        if (entity == null) {
            throw new SmartRedisLimiterPolicyNotFoundException();
        }
        return entity;
    }

    private void requireRowVersion(Long rowVersion) {
        if (rowVersion == null || rowVersion < 0) {
            throw validationException();
        }
    }

    private void verifyVersion(SmartRedisLimiterPolicyEntity current, long expected) {
        if (current.getRowVersion() == null || current.getRowVersion() != expected) {
            throw versionConflict();
        }
    }

    private SmartRedisLimiterPolicyEntity newEntity(SmartRedisLimiterPolicy policy,
                                                    boolean enabled,
                                                    Instant now) {
        SmartRedisLimiterPolicyEntity entity = new SmartRedisLimiterPolicyEntity();
        entity.setServiceCode(policy.getKey().getServiceCode());
        entity.setResourceCode(policy.getKey().getResourceCode());
        entity.setSubject(policy.getKey().getSubject());
        entity.setEnabled(enabled);
        entity.setRowVersion(SmartRedisLimiterManagementConstant.INITIAL_ROW_VERSION);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        List<SmartRedisLimiterPolicyLimitEntity> limits = new ArrayList<>();
        int index = 0;
        for (SmartRedisLimiterLimit limit : policy.getLimits()) {
            SmartRedisLimiterPolicyLimitEntity item = new SmartRedisLimiterPolicyLimitEntity();
            item.setSortOrder(index++);
            item.setCount(limit.getCount());
            item.setWindow(limit.getWindow());
            item.setUnit(limit.getUnit().getCode());
            item.setWindowSeconds(limit.getWindowSeconds());
            item.setCreatedAt(now);
            item.setUpdatedAt(now);
            limits.add(item);
        }
        entity.setLimits(limits);
        return entity;
    }

    private SmartRedisLimiterPolicyMutationResponse mutation(
            SmartRedisLimiterPolicyEntity entity,
            SmartRedisLimiterPolicyKey deletedKey,
            long revision,
            boolean changed) {
        return SmartRedisLimiterPolicyMutationResponse.builder()
                .policy(entity == null ? null : SmartRedisLimiterManagementMapper.toResponse(entity))
                .deletedPolicyKey(deletedKey)
                .revision(revision)
                .changed(changed)
                .build();
    }

    private SmartRedisLimiterPolicyQuery normalizeQuery(SmartRedisLimiterPolicyQuery query) {
        int page = query == null || query.getPage() == null ? 1 : query.getPage();
        int size = query == null || query.getSize() == null
                ? properties.getPage().getDefaultSize() : query.getSize();
        if (page <= 0 || size <= 0 || size > properties.getPage().getMaxSize()) {
            throw validationException();
        }
        return SmartRedisLimiterPolicyQuery.builder()
                .serviceCode(query == null ? null : normalizeOptionalServiceCode(query.getServiceCode()))
                .resourceCode(query == null ? null : normalizeOptionalResourceCode(query.getResourceCode()))
                .subject(query == null ? null : normalizeOptionalSubject(query.getSubject()))
                .enabled(query == null ? null : query.getEnabled())
                .page(page)
                .size(size)
                .build();
    }

    private String normalizeOptionalServiceCode(String value) {
        return hasText(value) ? SmartRedisLimiterPolicyValidationHelper.normalizeServiceCode(value) : null;
    }

    private String normalizeOptionalResourceCode(String value) {
        return hasText(value) ? SmartRedisLimiterPolicyValidationHelper.normalizeResourceCode(value) : null;
    }

    private String normalizeOptionalSubject(String value) {
        return hasText(value) ? SmartRedisLimiterPolicyValidationHelper.normalizeSubject(value) : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private SmartRedisLimiterManagementValidationException validationException() {
        return new SmartRedisLimiterManagementValidationException(
                String.format(ErrorMessage.POLICY_VALIDATION_FAILED,
                        ErrorMessage.PAGE_INVALID));
    }

    private SmartRedisLimiterPolicyConflictException identityConflict() {
        return new SmartRedisLimiterPolicyConflictException(
                ErrorCode.POLICY_IDENTITY_CONFLICT,
                ErrorMessage.POLICY_IDENTITY_CONFLICT);
    }

    private SmartRedisLimiterPolicyConflictException versionConflict() {
        return new SmartRedisLimiterPolicyConflictException(
                ErrorCode.POLICY_VERSION_CONFLICT,
                ErrorMessage.POLICY_VERSION_CONFLICT);
    }
}
