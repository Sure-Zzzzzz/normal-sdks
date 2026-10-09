package io.github.surezzzzzz.sdk.limiter.redis.smart.management.service;

import io.github.surezzzzzz.sdk.limiter.redis.smart.constant.*;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterDirectoryProvider;
import io.github.surezzzzzz.sdk.limiter.redis.smart.directory.SmartRedisLimiterServiceDeclaration;
import io.github.surezzzzzz.sdk.limiter.redis.smart.event.SmartRedisLimiterTypedManagementEvent;
import io.github.surezzzzzz.sdk.limiter.redis.smart.exception.SmartRedisLimiterException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterTypedMutationResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterTypedPageResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.controller.response.SmartRedisLimiterTypedRuleResponse;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementAccessDeniedException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementValidationException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterPolicyConflictException;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterPolicyRevisionEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterTypedRuleEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.entity.SmartRedisLimiterTypedRuleLimitEntity;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterPolicyDataScope;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.model.view.SmartRedisLimiterTypedRuleQuery;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.SmartRedisLimiterPolicyRepository;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.repository.SmartRedisLimiterTypedRuleRepository;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.support.SmartRedisLimiterEtagHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.support.SmartRedisLimiterManagementTimeHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.SmartRedisLimiterLimit;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicy;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicyKey;
import io.github.surezzzzzz.sdk.limiter.redis.smart.model.policy.typed.SmartRedisLimiterTypedPolicySnapshot;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterAttributeSnapshotHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterBucketIdentityHelper;
import io.github.surezzzzzz.sdk.limiter.redis.smart.support.SmartRedisLimiterTypedPolicyValidationHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * v2 类型化规则管理默认实现
 *
 * <p>身份组合校验复用 core 2.2.0 的类型化校验 Helper（构造
 * {@link SmartRedisLimiterTypedPolicyKey} 即校验）；目录校验要求服务声明为
 * TYPED_V2 且资源声明了对应维度，命名空间与自定义类型必须与目录一致。
 * 服务 revision 与 v1 共用 revision 表（一个服务只属于一种协议模式）。
 * 变更发布 core 类型化管理事件，操作人以 83 字符短摘要进入 attributes。
 *
 * @author surezzzzzz
 */
@Slf4j
public class DefaultSmartRedisLimiterTypedPolicyManagementService
        implements SmartRedisLimiterTypedPolicyManagementService {

    private final SmartRedisLimiterTypedRuleRepository repository;
    private final SmartRedisLimiterPolicyRepository policyRepository;
    private final SmartRedisLimiterDirectoryProvider directoryProvider;
    private final TypedEventPublisher eventPublisher;
    private final int defaultPageSize;
    private final int maxPageSize;

    /**
     * 构造类型化管理服务
     *
     * @param repository        类型化规则 Repository
     * @param policyRepository  v1 策略 Repository（复用服务 revision 表）
     * @param directoryProvider 目录提供方
     * @param eventPublisher    类型化事件发布器
     * @param defaultPageSize   默认分页大小
     * @param maxPageSize       最大分页大小
     */
    public DefaultSmartRedisLimiterTypedPolicyManagementService(SmartRedisLimiterTypedRuleRepository repository,
                                                                SmartRedisLimiterPolicyRepository policyRepository,
                                                                SmartRedisLimiterDirectoryProvider directoryProvider,
                                                                TypedEventPublisher eventPublisher,
                                                                int defaultPageSize,
                                                                int maxPageSize) {
        this.repository = repository;
        this.policyRepository = policyRepository;
        this.directoryProvider = directoryProvider;
        this.eventPublisher = eventPublisher;
        this.defaultPageSize = defaultPageSize;
        this.maxPageSize = maxPageSize;
    }

    private static String nullable(String value) {
        return value == null ? "" : value;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    @Override
    @Transactional
    public SmartRedisLimiterTypedMutationResponse create(SmartRedisLimiterTypedPolicyKey key,
                                                         Boolean enabled,
                                                         List<SmartRedisLimiterLimit> limits,
                                                         String operator,
                                                         SmartRedisLimiterPolicyDataScope scope) {
        requireScope(scope);
        SmartRedisLimiterServiceDeclaration declaration = requireTypedDeclaration(key.getServiceCode());
        key = alignKeyToDirectory(key, declaration);
        requireTypedDirectory(key, declaration);
        List<SmartRedisLimiterLimit> validated = SmartRedisLimiterTypedPolicyValidationHelper
                .validateLimits(limits);
        scope.requireService(key.getServiceCode());
        SmartRedisLimiterPolicyRevisionEntity revisionEntity = lockRevision(key.getServiceCode());
        if (repository.findByIdentity(key.getServiceCode(), key.getResourceCode(),
                key.getDimension().name(), key.getSelector().name(),
                nullable(key.getNamespace()), nullable(key.getCustomType()),
                nullable(key.getObjectId())) != null) {
            throw identityConflict();
        }
        Instant now = SmartRedisLimiterManagementTimeHelper.nowMillis();
        SmartRedisLimiterTypedRuleEntity entity = newEntity(key,
                Boolean.TRUE.equals(enabled), validated, now);
        long id;
        long revision;
        try {
            id = repository.insert(entity);
            revision = advanceRevision(revisionEntity, now);
        } catch (DuplicateKeyException ex) {
            throw identityConflict();
        }
        SmartRedisLimiterTypedRuleEntity saved = repository.findById(id);
        publishEvent(SmartRedisLimiterManagementOperation.CREATE, key, id, revision, null, operator, now);
        log.debug("类型化规则变更结果 operation=CREATE, ruleId={}, revision={}", id, revision);
        return mutation(saved, revision);
    }

    @Override
    @Transactional
    public SmartRedisLimiterTypedMutationResponse update(long id,
                                                         long expectedRowVersion,
                                                         List<SmartRedisLimiterLimit> limits,
                                                         String operator,
                                                         SmartRedisLimiterPolicyDataScope scope) {
        requireScope(scope);
        SmartRedisLimiterTypedRuleEntity current = requireRule(id, scope);
        SmartRedisLimiterTypedPolicyKey key = toKey(current);
        List<SmartRedisLimiterLimit> validated = SmartRedisLimiterTypedPolicyValidationHelper
                .validateLimits(limits);
        SmartRedisLimiterPolicyRevisionEntity revisionEntity = lockRevision(current.getServiceCode());
        current = requireRule(id, scope);
        verifyVersion(current, expectedRowVersion);
        Instant now = SmartRedisLimiterManagementTimeHelper.nowMillis();
        if (!repository.replaceLimits(id, expectedRowVersion, validated, now, scope)) {
            throw versionConflict();
        }
        long revision = advanceRevision(revisionEntity, now);
        SmartRedisLimiterTypedRuleEntity saved = repository.findById(id);
        publishEvent(SmartRedisLimiterManagementOperation.UPDATE, key, id, revision, null, operator, now);
        log.debug("类型化规则变更结果 operation=UPDATE, ruleId={}, revision={}", id, revision);
        return mutation(saved, revision);
    }

    @Override
    @Transactional
    public SmartRedisLimiterTypedMutationResponse state(long id,
                                                        long expectedRowVersion,
                                                        boolean enabled,
                                                        String operator,
                                                        SmartRedisLimiterPolicyDataScope scope) {
        requireScope(scope);
        SmartRedisLimiterTypedRuleEntity current = requireRule(id, scope);
        SmartRedisLimiterTypedPolicyKey key = toKey(current);
        SmartRedisLimiterPolicyRevisionEntity revisionEntity = lockRevision(current.getServiceCode());
        current = requireRule(id, scope);
        verifyVersion(current, expectedRowVersion);
        Instant now = SmartRedisLimiterManagementTimeHelper.nowMillis();
        if (!repository.updateEnabled(id, expectedRowVersion, enabled, now, scope)) {
            throw versionConflict();
        }
        long revision = advanceRevision(revisionEntity, now);
        SmartRedisLimiterTypedRuleEntity saved = repository.findById(id);
        publishEvent(enabled
                        ? SmartRedisLimiterManagementOperation.ENABLE
                        : SmartRedisLimiterManagementOperation.DISABLE,
                key, id, revision, null, operator, now);
        log.debug("类型化规则变更结果 operation={}, ruleId={}, revision={}", enabled ? "ENABLE" : "DISABLE", id, revision);
        return mutation(saved, revision);
    }

    @Override
    @Transactional
    public SmartRedisLimiterTypedMutationResponse delete(long id,
                                                         long expectedRowVersion,
                                                         String operator,
                                                         SmartRedisLimiterPolicyDataScope scope) {
        requireScope(scope);
        SmartRedisLimiterTypedRuleEntity current = requireRule(id, scope);
        SmartRedisLimiterTypedPolicyKey key = toKey(current);
        SmartRedisLimiterPolicyRevisionEntity revisionEntity = lockRevision(current.getServiceCode());
        current = requireRule(id, scope);
        verifyVersion(current, expectedRowVersion);
        Instant now = SmartRedisLimiterManagementTimeHelper.nowMillis();
        if (!repository.delete(id, expectedRowVersion, scope)) {
            throw versionConflict();
        }
        long revision = advanceRevision(revisionEntity, now);
        publishEvent(SmartRedisLimiterManagementOperation.DELETE, key, id, revision, null, operator, now);
        log.debug("类型化规则变更结果 operation=DELETE, ruleId={}, revision={}", id, revision);
        return mutation(null, revision);
    }

    @Override
    @Transactional(readOnly = true)
    public SmartRedisLimiterTypedRuleResponse findById(long id, SmartRedisLimiterPolicyDataScope scope) {
        requireScope(scope);
        return toResponse(requireRule(id, scope));
    }

    @Override
    @Transactional(readOnly = true)
    public SmartRedisLimiterTypedPageResponse query(SmartRedisLimiterTypedRuleQuery query,
                                                    SmartRedisLimiterPolicyDataScope scope) {
        requireScope(scope);
        SmartRedisLimiterTypedRuleQuery normalized = normalizeQuery(query);
        List<SmartRedisLimiterTypedRuleResponse> items = new ArrayList<>();
        for (SmartRedisLimiterTypedRuleEntity entity : repository.query(normalized, scope)) {
            items.add(toResponse(entity));
        }
        SmartRedisLimiterTypedPageResponse pageResponse = SmartRedisLimiterTypedPageResponse.builder()
                .items(items)
                .total(repository.count(normalized, scope))
                .page(normalized.getPage())
                .size(normalized.getSize())
                .build();
        log.debug("类型化规则查询结果 page={}, size={}, itemCount={}, total={}",
                normalized.getPage(), normalized.getSize(), items.size(), pageResponse.getTotal());
        return pageResponse;
    }

    @Override
    @Transactional(readOnly = true)
    public TypedSnapshotView snapshot(String serviceCode) {
        SmartRedisLimiterServiceDeclaration declaration = requireTypedDeclaration(serviceCode);
        String normalizedServiceCode = declaration.getServiceCode();
        List<SmartRedisLimiterTypedPolicy> rules = new ArrayList<>();
        for (SmartRedisLimiterTypedRuleEntity entity : repository.findEnabledByServiceCode(
                normalizedServiceCode)) {
            rules.add(new SmartRedisLimiterTypedPolicy(toKey(entity), Boolean.TRUE.equals(entity.getEnabled()),
                    entity.getLimits().stream()
                            .map(limit -> new SmartRedisLimiterLimit(limit.getCount(), limit.getWindow(),
                                    SmartRedisLimiterTimeUnit.fromCode(limit.getUnit())))
                            .collect(java.util.stream.Collectors.toList())));
        }
        SmartRedisLimiterPolicyRevisionEntity revisionEntity = policyRepository
                .findRevision(normalizedServiceCode);
        long revision = revisionEntity == null ? 0L : revisionEntity.getRevision();
        Instant publishedAt = revisionEntity == null ? Instant.EPOCH : revisionEntity.getPublishedAt();
        SmartRedisLimiterTypedPolicySnapshot snapshot = new SmartRedisLimiterTypedPolicySnapshot(
                SmartRedisLimiterConstant.TYPED_POLICY_SCHEMA_VERSION,
                normalizedServiceCode,
                resolvePolicyEpoch(declaration),
                revision,
                publishedAt,
                rules);
        return new TypedSnapshotView(
                SmartRedisLimiterEtagHelper.build(normalizedServiceCode, revision), snapshot);
    }

    private SmartRedisLimiterServiceDeclaration requireTypedDeclaration(String serviceCode) {
        SmartRedisLimiterServiceDeclaration declaration = directoryProvider.findService(serviceCode);
        if (declaration == null
                || !SmartRedisLimiterServiceControlMode.TYPED_V2.name().equals(declaration.getControlMode())) {
            throw protocolMismatch();
        }
        return declaration;
    }

    /**
     * 策略代次以目录声明为准；未声明时使用默认值 1（协议切换时由宿主显式调整）。
     */
    private long resolvePolicyEpoch(SmartRedisLimiterServiceDeclaration declaration) {
        Long epoch = declaration.getPolicyEpoch();
        return epoch == null ? SmartRedisLimiterManagementConstant.TYPED_POLICY_EPOCH_DEFAULT : epoch;
    }

    /**
     * 命名空间以宿主目录声明为准：客户端未传或传错都以声明值对齐，避免调用方感知内部校验。
     */
    private SmartRedisLimiterTypedPolicyKey alignKeyToDirectory(SmartRedisLimiterTypedPolicyKey key,
                                                                SmartRedisLimiterServiceDeclaration declaration) {
        String declaredNamespace = declaration.getNamespaces().get(key.getDimension().name());
        if (declaredNamespace == null || declaredNamespace.equals(key.getNamespace())) {
            return key;
        }
        return new SmartRedisLimiterTypedPolicyKey(key.getServiceCode(), key.getResourceCode(),
                key.getDimension(), key.getSelector(), declaredNamespace,
                key.getCustomType(), key.getObjectId());
    }

    private void requireTypedDirectory(SmartRedisLimiterTypedPolicyKey key,
                                       SmartRedisLimiterServiceDeclaration declaration) {
        if (!SmartRedisLimiterServiceControlMode.TYPED_V2.name().equals(declaration.getControlMode())) {
            throw protocolMismatch();
        }
        if (!declaration.declares(key.getResourceCode(), key.getDimension().name())) {
            throw new SmartRedisLimiterManagementValidationException(
                    String.format(ErrorMessage.TYPED_DIMENSION_NOT_DECLARED,
                            key.getResourceCode(), key.getDimension().name()));
        }
        String declaredNamespace = declaration.getNamespaces().get(key.getDimension().name());
        if (declaredNamespace != null && !declaredNamespace.equals(nullable(key.getNamespace()))) {
            throw new SmartRedisLimiterManagementValidationException(
                    String.format(ErrorMessage.TYPED_NAMESPACE_MISMATCH, declaredNamespace));
        }
        if (key.getCustomType() != null
                && !declaration.getCustomTypes().contains(key.getCustomType())) {
            throw new SmartRedisLimiterManagementValidationException(
                    String.format(ErrorMessage.TYPED_CUSTOM_TYPE_NOT_DECLARED, key.getCustomType()));
        }
    }

    private SmartRedisLimiterPolicyRevisionEntity lockRevision(String serviceCode) {
        policyRepository.initializeRevision(serviceCode);
        SmartRedisLimiterPolicyRevisionEntity revision = policyRepository.lockRevision(serviceCode);
        if (revision == null) {
            throw new SmartRedisLimiterException(ErrorCode.PERSISTENCE_FAILED,
                    ErrorMessage.PERSISTENCE_FAILED);
        }
        return revision;
    }

    private long advanceRevision(SmartRedisLimiterPolicyRevisionEntity current, Instant now) {
        if (current.getRevision() == Long.MAX_VALUE) {
            throw new SmartRedisLimiterPolicyConflictException(
                    ErrorCode.REVISION_OVERFLOW, ErrorMessage.REVISION_OVERFLOW);
        }
        long revision = current.getRevision() + SmartRedisLimiterManagementConstant.REVISION_INCREMENT;
        policyRepository.updateRevision(current.getServiceCode(), revision, now);
        return revision;
    }

    private SmartRedisLimiterTypedRuleEntity requireRule(long id, SmartRedisLimiterPolicyDataScope scope) {
        SmartRedisLimiterTypedRuleEntity entity = repository.findById(id, scope);
        if (entity == null) {
            throw new SmartRedisLimiterPolicyConflictException(
                    ErrorCode.POLICY_NOT_FOUND, ErrorMessage.POLICY_NOT_FOUND);
        }
        return entity;
    }

    private void verifyVersion(SmartRedisLimiterTypedRuleEntity current, long expectedRowVersion) {
        if (current.getRowVersion() != expectedRowVersion) {
            throw versionConflict();
        }
    }

    private SmartRedisLimiterTypedRuleEntity newEntity(SmartRedisLimiterTypedPolicyKey key,
                                                       boolean enabled,
                                                       List<SmartRedisLimiterLimit> limits,
                                                       Instant now) {
        SmartRedisLimiterTypedRuleEntity entity = new SmartRedisLimiterTypedRuleEntity();
        entity.setServiceCode(key.getServiceCode());
        entity.setResourceCode(key.getResourceCode());
        entity.setDimension(key.getDimension().name());
        entity.setSelector(key.getSelector().name());
        entity.setNamespace(nullable(key.getNamespace()));
        entity.setCustomType(nullable(key.getCustomType()));
        entity.setObjectId(nullable(key.getObjectId()));
        entity.setEnabled(enabled);
        entity.setRowVersion(0L);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        entity.setLimits(limits.stream()
                .map(limit -> {
                    SmartRedisLimiterTypedRuleLimitEntity limitEntity = new SmartRedisLimiterTypedRuleLimitEntity();
                    limitEntity.setCount(limit.getCount());
                    limitEntity.setWindow(limit.getWindow());
                    limitEntity.setUnit(limit.getUnit().getCode());
                    limitEntity.setWindowSeconds(limit.getWindowSeconds());
                    limitEntity.setCreatedAt(now);
                    limitEntity.setUpdatedAt(now);
                    return limitEntity;
                })
                .collect(java.util.stream.Collectors.toList()));
        return entity;
    }

    private SmartRedisLimiterTypedPolicyKey toKey(SmartRedisLimiterTypedRuleEntity entity) {
        return new SmartRedisLimiterTypedPolicyKey(
                entity.getServiceCode(),
                entity.getResourceCode(),
                SmartRedisLimiterDataDimension.valueOf(entity.getDimension()),
                SmartRedisLimiterRuleSelector.valueOf(entity.getSelector()),
                entity.getNamespace(),
                "CUSTOM".equals(entity.getDimension()) ? emptyToNull(entity.getCustomType()) : null,
                "EXACT".equals(entity.getSelector()) ? emptyToNull(entity.getObjectId()) : null);
    }

    private SmartRedisLimiterTypedRuleResponse toResponse(SmartRedisLimiterTypedRuleEntity entity) {
        return SmartRedisLimiterTypedRuleResponse.builder()
                .id(entity.getId())
                .serviceCode(entity.getServiceCode())
                .resourceCode(entity.getResourceCode())
                .dimension(entity.getDimension())
                .selector(entity.getSelector())
                .namespace(entity.getNamespace())
                .customType(emptyToNull(entity.getCustomType()))
                .objectId(emptyToNull(entity.getObjectId()))
                .enabled(entity.getEnabled())
                .rowVersion(entity.getRowVersion())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .limits(entity.getLimits().stream()
                        .map(limit -> new SmartRedisLimiterLimit(limit.getCount(), limit.getWindow(),
                                SmartRedisLimiterTimeUnit.fromCode(limit.getUnit())))
                        .collect(java.util.stream.Collectors.toList()))
                .build();
    }

    private SmartRedisLimiterTypedMutationResponse mutation(SmartRedisLimiterTypedRuleEntity entity,
                                                            long revision) {
        return SmartRedisLimiterTypedMutationResponse.builder()
                .rule(entity == null ? null : toResponse(entity))
                .revision(revision)
                .build();
    }

    private void publishEvent(SmartRedisLimiterManagementOperation operation,
                              SmartRedisLimiterTypedPolicyKey key,
                              Long ruleId,
                              long revision,
                              String reason,
                              String operator,
                              Instant occurredAt) {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(SmartRedisLimiterConstant.OPERATOR_IDENTITY_ATTRIBUTE_KEY,
                SmartRedisLimiterBucketIdentityHelper.operatorDigest(
                        SmartRedisLimiterManagementConstant.OPERATOR_SOURCE_ID,
                        SmartRedisLimiterManagementConstant.OPERATOR_SUBJECT_TYPE, operator));
        eventPublisher.publish(new SmartRedisLimiterTypedManagementEvent(this,
                new io.github.surezzzzzz.sdk.limiter.redis.smart.model.SmartRedisLimiterTypedManagementEventPayload(
                        operation, key.getServiceCode(), key.getResourceCode(), key.getDimension(),
                        key.getSelector(), SmartRedisLimiterBucketIdentityHelper.digest(key), ruleId, revision,
                        "SUCCESS", reason, occurredAt,
                        SmartRedisLimiterAttributeSnapshotHelper.snapshot(attributes))));
    }

    private SmartRedisLimiterTypedRuleQuery normalizeQuery(SmartRedisLimiterTypedRuleQuery query) {
        Integer page = query.getPage() == null || query.getPage() < 1 ? 1 : query.getPage();
        Integer size = query.getSize() == null || query.getSize() < 1
                ? defaultPageSize : Math.min(query.getSize(), maxPageSize);
        return SmartRedisLimiterTypedRuleQuery.builder()
                .serviceCode(query.getServiceCode())
                .resourceCode(query.getResourceCode())
                .dimension(query.getDimension())
                .selector(query.getSelector())
                .namespace(query.getNamespace())
                .customType(query.getCustomType())
                .objectId(query.getObjectId())
                .enabled(query.getEnabled())
                .page(page)
                .size(size)
                .build();
    }

    private void requireScope(SmartRedisLimiterPolicyDataScope scope) {
        if (scope == null) {
            throw new SmartRedisLimiterManagementAccessDeniedException();
        }
        scope.requireUsable();
    }

    private SmartRedisLimiterPolicyConflictException identityConflict() {
        return new SmartRedisLimiterPolicyConflictException(
                ErrorCode.POLICY_IDENTITY_CONFLICT, ErrorMessage.POLICY_IDENTITY_CONFLICT);
    }

    private SmartRedisLimiterPolicyConflictException versionConflict() {
        return new SmartRedisLimiterPolicyConflictException(
                ErrorCode.POLICY_VERSION_CONFLICT, ErrorMessage.POLICY_VERSION_CONFLICT);
    }

    private SmartRedisLimiterPolicyConflictException protocolMismatch() {
        return new SmartRedisLimiterPolicyConflictException(
                ErrorCode.TYPED_PROTOCOL_MISMATCH, ErrorMessage.TYPED_PROTOCOL_MISMATCH);
    }

    /**
     * 类型化管理事件发布器（事务提交后投递）
     */
    public interface TypedEventPublisher {

        /**
         * 发布类型化管理事件
         */
        void publish(SmartRedisLimiterTypedManagementEvent event);
    }
}
