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
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.*;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterPolicyEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterPolicyLimitEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterPolicyRevisionEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyQuery;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.SmartRedisLimiterPolicyRepository;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.support.SmartRedisLimiterManagementMapper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.support.SmartRedisLimiterManagementTimeHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterManagementEventPayload;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterPolicyValidationHelper;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
        return create(request, operator, consoleScope());
    }

    @Override
    @Transactional
    public SmartRedisLimiterPolicyMutationResponse create(
            SmartRedisLimiterPolicyCreateRequest request, String operator, SmartRedisLimiterPolicyDataScope scope) {
        requireScope(scope);
        SmartRedisLimiterPolicy afterPolicy = validateCreateRequest(request);
        String normalizedOperator = SmartRedisLimiterPolicyValidationHelper.normalizeOperator(operator);
        SmartRedisLimiterPolicyKey key = afterPolicy.getKey();
        scope.requireService(key.getServiceCode());
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
        return update(id, request, operator, consoleScope());
    }

    @Override
    @Transactional
    public SmartRedisLimiterPolicyMutationResponse update(long id, SmartRedisLimiterPolicyUpdateRequest request,
                                                          String operator, SmartRedisLimiterPolicyDataScope scope) {
        requireScope(scope);
        requireRowVersion(request == null ? null : request.getExpectedRowVersion());
        SmartRedisLimiterPolicyEntity current = requirePolicy(id, scope);
        SmartRedisLimiterPolicy beforePolicy = SmartRedisLimiterManagementMapper.toCorePolicy(current);
        SmartRedisLimiterPolicy afterPolicy = new SmartRedisLimiterPolicy(
                beforePolicy.getKey(), request.getLimits());
        String normalizedOperator = SmartRedisLimiterPolicyValidationHelper.normalizeOperator(operator);
        SmartRedisLimiterPolicyRevisionEntity revisionEntity = lockRevision(current.getServiceCode());
        current = requirePolicy(id, scope);
        verifyVersion(current, request.getExpectedRowVersion());
        beforePolicy = SmartRedisLimiterManagementMapper.toCorePolicy(current);
        if (beforePolicy.equals(afterPolicy)) {
            return mutation(current, null, revisionEntity.getRevision(), false);
        }
        Instant now = SmartRedisLimiterManagementTimeHelper.nowMillis();
        if (!repository.replaceLimits(id, request.getExpectedRowVersion(), afterPolicy.getLimits(), now, scope)) {
            throw versionConflict();
        }
        long revision = advanceRevision(revisionEntity, now);
        SmartRedisLimiterPolicyEntity saved = requirePolicy(id, scope);
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
        return enable(id, rowVersion, operator, consoleScope());
    }

    @Override
    @Transactional
    public SmartRedisLimiterPolicyMutationResponse enable(long id, long rowVersion, String operator,
                                                          SmartRedisLimiterPolicyDataScope scope) {
        requireScope(scope);
        return changeState(id, rowVersion, true, operator, scope);
    }

    @Override
    @Transactional
    public SmartRedisLimiterPolicyMutationResponse disable(long id, long rowVersion, String operator) {
        return disable(id, rowVersion, operator, consoleScope());
    }

    @Override
    @Transactional
    public SmartRedisLimiterPolicyMutationResponse disable(long id, long rowVersion, String operator,
                                                           SmartRedisLimiterPolicyDataScope scope) {
        requireScope(scope);
        return changeState(id, rowVersion, false, operator, scope);
    }

    @Override
    @Transactional
    public SmartRedisLimiterPolicyMutationResponse delete(long id, long rowVersion, String operator) {
        return delete(id, rowVersion, operator, consoleScope());
    }

    @Override
    @Transactional
    public SmartRedisLimiterPolicyMutationResponse delete(long id, long rowVersion, String operator,
                                                          SmartRedisLimiterPolicyDataScope scope) {
        requireScope(scope);
        SmartRedisLimiterPolicyEntity current = requirePolicy(id, scope);
        SmartRedisLimiterPolicyRevisionEntity revisionEntity = lockRevision(current.getServiceCode());
        current = requirePolicy(id, scope);
        verifyVersion(current, rowVersion);
        SmartRedisLimiterPolicy beforePolicy = SmartRedisLimiterManagementMapper.toCorePolicy(current);
        String normalizedOperator = SmartRedisLimiterPolicyValidationHelper.normalizeOperator(operator);
        Instant now = SmartRedisLimiterManagementTimeHelper.nowMillis();
        if (!repository.delete(id, rowVersion, scope)) {
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
        return findById(id, consoleScope());
    }

    @Override
    @Transactional(readOnly = true)
    public SmartRedisLimiterPolicyResponse findById(long id, SmartRedisLimiterPolicyDataScope scope) {
        requireScope(scope);
        return SmartRedisLimiterManagementMapper.toResponse(requirePolicy(id, scope));
    }

    @Override
    @Transactional(readOnly = true)
    public SmartRedisLimiterPolicyPageResponse query(SmartRedisLimiterPolicyQuery query) {
        return query(query, consoleScope());
    }

    @Override
    @Transactional(readOnly = true)
    public SmartRedisLimiterPolicyPageResponse query(SmartRedisLimiterPolicyQuery query,
                                                     SmartRedisLimiterPolicyDataScope scope) {
        requireScope(scope);
        SmartRedisLimiterPolicyQuery normalized = normalizeQuery(query);
        List<SmartRedisLimiterPolicyResponse> items = new ArrayList<>();
        for (SmartRedisLimiterPolicyEntity entity : repository.query(normalized, scope)) {
            items.add(SmartRedisLimiterManagementMapper.toResponse(entity));
        }
        long total = repository.count(normalized, scope);
        log.debug("策略查询结果 page={}, size={}, itemCount={}, total={}, all={}, serviceCount={}",
                normalized.getPage(), normalized.getSize(), items.size(), total, scope.isAll(), scope.getServiceCodes().size());
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
            long id, long rowVersion, boolean enabled, String operator, SmartRedisLimiterPolicyDataScope scope) {
        SmartRedisLimiterPolicyEntity current = requirePolicy(id, scope);
        SmartRedisLimiterPolicyRevisionEntity revisionEntity = lockRevision(current.getServiceCode());
        current = requirePolicy(id, scope);
        verifyVersion(current, rowVersion);
        if (Boolean.valueOf(enabled).equals(current.getEnabled())) {
            return mutation(current, null, revisionEntity.getRevision(), false);
        }
        SmartRedisLimiterPolicy policy = SmartRedisLimiterManagementMapper.toCorePolicy(current);
        String normalizedOperator = SmartRedisLimiterPolicyValidationHelper.normalizeOperator(operator);
        Instant now = SmartRedisLimiterManagementTimeHelper.nowMillis();
        if (!repository.updateEnabled(id, rowVersion, enabled, now, scope)) {
            throw versionConflict();
        }
        long revision = advanceRevision(revisionEntity, now);
        SmartRedisLimiterPolicyEntity saved = requirePolicy(id, scope);
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

    private SmartRedisLimiterPolicyEntity requirePolicy(long id, SmartRedisLimiterPolicyDataScope scope) {
        SmartRedisLimiterPolicyEntity entity = repository.findById(id, scope);
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

    private SmartRedisLimiterPolicyDataScope consoleScope() {
        if (properties.isPortal()) {
            throw new SmartRedisLimiterManagementAccessDeniedException();
        }
        return SmartRedisLimiterPolicyDataScope.all();
    }

    private void requireScope(SmartRedisLimiterPolicyDataScope scope) {
        if (scope == null) {
            throw new SmartRedisLimiterManagementAccessDeniedException();
        }
        scope.requireUsable();
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
        log.debug("策略变更结果 policyId={}, revision={}, changed={}",
                entity == null ? null : entity.getId(), revision, changed);
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
