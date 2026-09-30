package io.github.surezzzzzz.sdk.auth.aksk.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.configuration.SimpleAkskServerProperties;
import io.github.surezzzzzz.sdk.auth.aksk.server.constant.SimpleAkskServerConstant;
import io.github.surezzzzzz.sdk.auth.aksk.server.entity.*;
import io.github.surezzzzzz.sdk.auth.aksk.server.repository.*;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationChange;
import io.github.surezzzzzz.sdk.auth.authorization.owner.collaboration.core.model.OwnerAuthorizationReadResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

/**
 * AKSK 侧外部身份源 owner 授权本地投影。
 *
 * <p>所有事件先落 durable Inbox，再和投影及 cursor 在同一事务内提交。投影只接受更大的
 * 业务访问纪元，sourceSequence 仅决定日志消费顺序，不能代替业务版本。</p>
 */
@Slf4j
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class AkskOwnerAuthorizationProjectionService {

    public static final String STREAM_KEY = "owner-authorization";

    // ==================== 变更流载荷键（与外部身份源写端的解析契约字段，改名即破坏协议） ====================
    private static final String FIELD_OWNER_SOURCE_ID = "ownerSourceId";
    private static final String FIELD_OWNER_SUBJECT_ID = "ownerSubjectId";
    private static final String FIELD_TARGET_APPLICATION_ID = "targetApplicationId";
    private static final String FIELD_ACTIVE = "active";
    private static final String FIELD_OWNER_SECURITY_EPOCH = "ownerSecurityEpoch";
    private static final String FIELD_APPLICATION_AUTHORIZATION_EPOCH = "applicationAuthorizationEpoch";
    private static final String FIELD_OWNER_INHERITED_ACCESS_EPOCH = "ownerInheritedAccessEpoch";
    private static final String FIELD_PROJECTION_ACCESS_EPOCH = "projectionAccessEpoch";
    private static final String FIELD_AUTHORIZATION = "authorization";
    private static final int ACTIVE = 1;
    private static final int INACTIVE = 0;
    private static final ObjectMapper PAYLOAD_OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<Map<String, Object>>() {
    };

    private final SimpleAkskServerProperties properties;
    private final AkskOwnerAuthorizationCursorRepository cursorRepository;
    private final AkskOwnerAuthorizationInboxRepository inboxRepository;
    private final AkskOwnerAuthorizationOwnerStateRepository ownerStateRepository;
    private final AkskOwnerAuthorizationTargetApplicationStateRepository targetStateRepository;
    private final AkskOwnerAuthorizationProjectionRepository projectionRepository;

    /**
     * 新 binding 的同步 resolve 仅初始化不存在的 cursor，绝不推进已有流进度。
     */
    @Transactional
    public void bootstrap(String ownerSourceId, String ownerSubjectId, Long targetApplicationId,
                          OwnerAuthorizationReadResult result) {
        if (!validIdentity(ownerSourceId, ownerSubjectId, targetApplicationId) || result == null
                || !result.isActive() || result.getAuthorization() == null
                || !positive(result.getOwnerSecurityEpoch()) || !positive(result.getApplicationAuthorizationEpoch())
                || !positive(result.getOwnerInheritedAccessEpoch()) || !positive(result.getProjectionAccessEpoch())
                || result.getResumeAfterSequence() == null || result.getResumeAfterSequence().longValue() < 0L) {
            return;
        }
        Instant now = Instant.now();
        upsertOwner(ownerSourceId, ownerSubjectId, ACTIVE, result.getOwnerSecurityEpoch(), now);
        upsertTarget(targetApplicationId, ACTIVE, result.getApplicationAuthorizationEpoch(),
                result.getOwnerInheritedAccessEpoch(), now);
        upsertProjection(ownerSourceId, ownerSubjectId, targetApplicationId, ACTIVE,
                result.getOwnerSecurityEpoch(), result.getApplicationAuthorizationEpoch(),
                result.getOwnerInheritedAccessEpoch(), result.getProjectionAccessEpoch(),
                ApplicationAuthorizationContextClaimMapper.toClaim(result.toApplicationAuthorizationContext()), now);
        if (!cursorRepository.existsById(STREAM_KEY)) {
            AkskOwnerAuthorizationCursorEntity cursor = newCursor(result.getResumeAfterSequence(), now);
            cursorRepository.save(cursor);
        }
    }

    /**
     * 一个事件的 Inbox、投影和 cursor 同事务提交；false 表示调用方不得续租。
     */
    @Transactional
    public boolean apply(OwnerAuthorizationChange change) {
        if (change == null || !positive(change.getSourceSequence()) || change.getEventId() == null
                || change.getPayload() == null) {
            return false;
        }
        AkskOwnerAuthorizationCursorEntity cursor = requireCursor();
        if (change.getSourceSequence().longValue() <= cursor.getLastSourceSequence().longValue()) {
            return isKnownEvent(change);
        }
        if (change.getSourceSequence().longValue() != cursor.getLastSourceSequence().longValue() + 1L) {
            log.warn("身份源授权日志不连续，拒绝推进 cursor：expected={}, actual={}",
                    cursor.getLastSourceSequence() + 1L, change.getSourceSequence());
            return false;
        }
        if (!persistInbox(change)) {
            return false;
        }
        Instant now = Instant.now();
        if (!applyFinalState(change.getChangeType(), change.getPayload(), now)) {
            return false;
        }
        cursor.setLastSourceSequence(change.getSourceSequence());
        cursor.setUpdatedAt(now);
        cursorRepository.save(cursor);
        return true;
    }

    /**
     * 身份源成功返回连续页（即使空页）后才续本地授权租约。
     */
    @Transactional
    public void renewSynchronizationLease() {
        AkskOwnerAuthorizationCursorEntity cursor = requireCursor();
        Instant now = Instant.now();
        cursor.setLastSuccessfulPullAt(now);
        cursor.setSynchronizationLeaseUntil(now.plusSeconds(leaseSeconds()));
        cursor.setUpdatedAt(now);
        cursorRepository.save(cursor);
    }

    /**
     * 日志保留缺口或协议异常时立即失效本地租约，阻止旧 inherited 投影继续服务。
     */
    @Transactional
    public void invalidateSynchronizationLease() {
        AkskOwnerAuthorizationCursorEntity cursor = requireCursor();
        cursor.setSynchronizationLeaseUntil(Instant.now());
        cursor.setUpdatedAt(Instant.now());
        cursorRepository.save(cursor);
    }

    /**
     * 全量修复完成后允许将 cursor 跳到身份源明确给出的有效 high-water。
     */
    @Transactional
    public void completeResync(Long highWaterSequence) {
        if (highWaterSequence == null || highWaterSequence.longValue() < 0L) {
            return;
        }
        AkskOwnerAuthorizationCursorEntity cursor = requireCursor();
        cursor.setLastSourceSequence(highWaterSequence);
        Instant now = Instant.now();
        cursor.setLastSuccessfulPullAt(now);
        cursor.setSynchronizationLeaseUntil(now.plusSeconds(leaseSeconds()));
        cursor.setUpdatedAt(now);
        cursorRepository.save(cursor);
    }

    /**
     * 判断本地 inherited 投影是否仍处于身份源最近成功同步的宽限租约内。
     */
    @Transactional(readOnly = true)
    public boolean hasValidSynchronizationLease() {
        AkskOwnerAuthorizationCursorEntity cursor = cursorRepository.findById(STREAM_KEY).orElse(null);
        return cursor != null && cursor.getSynchronizationLeaseUntil() != null
                && Instant.now().isBefore(cursor.getSynchronizationLeaseUntil());
    }

    /**
     * 同步租约是否已过期（游标已存在但不在服务宽限内）：供候选目录标记降级——
     * 此时空候选可能是"读不到"而非"没授权"。游标不存在（继承链路从未启用/同步）不算降级。
     */
    @Transactional(readOnly = true)
    public boolean isSynchronizationLeaseExpired() {
        AkskOwnerAuthorizationCursorEntity cursor = cursorRepository.findById(STREAM_KEY).orElse(null);
        return cursor != null && !hasValidSynchronizationLease();
    }

    /**
     * 多实例 worker 领取；网络调用不在该事务内执行。
     */
    @Transactional
    public String claimWorkerLease() {
        AkskOwnerAuthorizationCursorEntity cursor = requireCursor();
        Instant now = Instant.now();
        if (cursor.getWorkerLeaseUntil() != null && cursor.getWorkerLeaseUntil().isAfter(now)) {
            return null;
        }
        String owner = java.util.UUID.randomUUID().toString();
        Instant leaseUntil = now.plusSeconds(Math.min(leaseSeconds(), 5L));
        int claimed = cursorRepository.tryClaimWorkerLease(STREAM_KEY, owner, leaseUntil, now);
        // 多实例同时抢租约时，只有条件更新成功的一方继续拉取；输家下一轮再竞争。
        return claimed == 1 ? owner : null;
    }

    @Transactional
    public void releaseWorkerLease(String owner) {
        AkskOwnerAuthorizationCursorEntity cursor = cursorRepository.findById(STREAM_KEY).orElse(null);
        if (cursor != null && owner != null && owner.equals(cursor.getWorkerLeaseOwner())) {
            cursor.setWorkerLeaseUntil(Instant.now());
            cursor.setUpdatedAt(Instant.now());
            cursorRepository.save(cursor);
        }
    }

    @Transactional(readOnly = true)
    public Long currentCursor() {
        AkskOwnerAuthorizationCursorEntity cursor = cursorRepository.findById(STREAM_KEY).orElse(null);
        return cursor == null ? 0L : cursor.getLastSourceSequence();
    }

    @Transactional(readOnly = true)
    public AkskOwnerAuthorizationProjectionEntity findActiveProjection(String ownerSourceId, String ownerSubjectId,
                                                                       Long targetApplicationId) {
        AkskOwnerAuthorizationProjectionEntity projection = projectionRepository
                .findById(projectionKey(ownerSourceId, ownerSubjectId, targetApplicationId)).orElse(null);
        if (projection == null || projection.getActive() == null || projection.getActive().intValue() != ACTIVE) {
            return null;
        }
        AkskOwnerAuthorizationOwnerStateEntity owner = ownerStateRepository.findById(ownerKey(ownerSourceId, ownerSubjectId))
                .orElse(null);
        AkskOwnerAuthorizationTargetApplicationStateEntity target = targetStateRepository
                .findById(targetApplicationId).orElse(null);
        if (owner == null || target == null || owner.getActive().intValue() != ACTIVE
                || target.getActive().intValue() != ACTIVE
                || owner.getOwnerSecurityEpoch().longValue() != projection.getOwnerSecurityEpoch().longValue()
                || target.getOwnerInheritedAccessEpoch().longValue()
                != projection.getOwnerInheritedAccessEpoch().longValue()) {
            return null;
        }
        return projection;
    }

    /**
     * 应用级授权纪元可独立推进；它使旧 token 失效，但不必重写每条三权投影内容。
     */
    @Transactional(readOnly = true)
    public Long currentApplicationAuthorizationEpoch(Long targetApplicationId) {
        return targetStateRepository.findById(targetApplicationId)
                .map(AkskOwnerAuthorizationTargetApplicationStateEntity::getApplicationAuthorizationEpoch)
                .orElse(null);
    }

    /**
     * 将持久化的授权 claim 按本次 token 时间窗还原，拒绝畸形投影。
     */
    public ApplicationAuthorizationContext readAuthorization(AkskOwnerAuthorizationProjectionEntity projection,
                                                             Instant issuedAt, Instant expiresAt) {
        if (projection == null || projection.getAuthorizationJson() == null || issuedAt == null || expiresAt == null) {
            return null;
        }
        try {
            ApplicationAuthorizationContext source = ApplicationAuthorizationContextClaimMapper.fromClaim(
                    PAYLOAD_OBJECT_MAPPER.readValue(projection.getAuthorizationJson(), MAP_TYPE));
            return new ApplicationAuthorizationContext(source.getProtocol(), source.getVersion(), source.getSubjectType(),
                    source.getSubjectId(), source.getApplicationCode(), source.isAdmitted(), source.getRoles(),
                    source.getPagePermissions(), source.getApiPermissions(), source.getDataGrantDocument(),
                    source.getAuthorizationVersion(), source.getManifestVersion(), source.getManifestDigest(), issuedAt, expiresAt);
        } catch (Exception exception) {
            log.warn("AKSK 本地 inherited 授权投影解析失败，按失败关闭：projectionKey={}",
                    projection.getProjectionKey());
            return null;
        }
    }

    private boolean persistInbox(OwnerAuthorizationChange change) {
        AkskOwnerAuthorizationInboxEntity inbox = new AkskOwnerAuthorizationInboxEntity();
        inbox.setEventId(change.getEventId());
        inbox.setSourceSequence(change.getSourceSequence());
        inbox.setChangeType(change.getChangeType());
        try {
            inbox.setPayloadJson(PAYLOAD_OBJECT_MAPPER.writeValueAsString(change.getPayload()));
            inbox.setReceivedAt(Instant.now());
            inbox.setAppliedAt(Instant.now());
            inboxRepository.saveAndFlush(inbox);
            return true;
        } catch (DataIntegrityViolationException exception) {
            return inboxRepository.existsById(change.getEventId());
        } catch (Exception exception) {
            return false;
        }
    }

    private boolean applyFinalState(String changeType, Map<String, Object> payload, Instant now) {
        if ("OWNER_STATE".equals(changeType)) {
            String source = string(payload, FIELD_OWNER_SOURCE_ID);
            String subject = string(payload, FIELD_OWNER_SUBJECT_ID);
            Long epoch = positiveNumber(payload, FIELD_OWNER_SECURITY_EPOCH);
            Boolean active = bool(payload, FIELD_ACTIVE);
            if (!validIdentity(source, subject, 1L) || epoch == null || active == null) {
                return false;
            }
            upsertOwner(source, subject, active.booleanValue() ? ACTIVE : INACTIVE, epoch, now);
            return true;
        }
        if ("TARGET_APPLICATION_STATE".equals(changeType)) {
            Long applicationId = positiveNumber(payload, FIELD_TARGET_APPLICATION_ID);
            Long applicationEpoch = positiveNumber(payload, FIELD_APPLICATION_AUTHORIZATION_EPOCH);
            Long inheritedEpoch = positiveNumber(payload, FIELD_OWNER_INHERITED_ACCESS_EPOCH);
            Boolean active = bool(payload, FIELD_ACTIVE);
            if (applicationId == null || applicationEpoch == null || inheritedEpoch == null || active == null) {
                return false;
            }
            upsertTarget(applicationId, active.booleanValue() ? ACTIVE : INACTIVE, applicationEpoch, inheritedEpoch, now);
            return true;
        }
        if ("OWNER_APPLICATION_PROJECTION".equals(changeType)) {
            String source = string(payload, FIELD_OWNER_SOURCE_ID);
            String subject = string(payload, FIELD_OWNER_SUBJECT_ID);
            Long applicationId = positiveNumber(payload, FIELD_TARGET_APPLICATION_ID);
            Long ownerEpoch = positiveNumber(payload, FIELD_OWNER_SECURITY_EPOCH);
            Long applicationEpoch = positiveNumber(payload, FIELD_APPLICATION_AUTHORIZATION_EPOCH);
            Long inheritedEpoch = positiveNumber(payload, FIELD_OWNER_INHERITED_ACCESS_EPOCH);
            Long projectionEpoch = positiveNumber(payload, FIELD_PROJECTION_ACCESS_EPOCH);
            Boolean active = bool(payload, FIELD_ACTIVE);
            if (!validIdentity(source, subject, applicationId) || ownerEpoch == null || applicationEpoch == null
                    || inheritedEpoch == null || projectionEpoch == null || active == null
                    || (active.booleanValue() && !(payload.get(FIELD_AUTHORIZATION) instanceof Map))) {
                return false;
            }
            upsertProjection(source, subject, applicationId, active.booleanValue() ? ACTIVE : INACTIVE,
                    ownerEpoch, applicationEpoch, inheritedEpoch, projectionEpoch, payload.get(FIELD_AUTHORIZATION), now);
            return true;
        }
        return false;
    }

    private void upsertOwner(String source, String subject, int active, Long epoch, Instant now) {
        String key = ownerKey(source, subject);
        AkskOwnerAuthorizationOwnerStateEntity state = ownerStateRepository.findById(key).orElse(null);
        if (state != null && state.getOwnerSecurityEpoch().longValue() >= epoch.longValue()) {
            return;
        }
        if (state == null) {
            state = new AkskOwnerAuthorizationOwnerStateEntity();
            state.setOwnerKey(key);
            state.setOwnerSourceId(source);
            state.setOwnerSubjectId(subject);
        }
        state.setActive(active);
        state.setOwnerSecurityEpoch(epoch);
        state.setUpdatedAt(now);
        ownerStateRepository.save(state);
    }

    private void upsertTarget(Long applicationId, int active, Long applicationEpoch, Long inheritedEpoch, Instant now) {
        AkskOwnerAuthorizationTargetApplicationStateEntity state = targetStateRepository.findById(applicationId).orElse(null);
        if (state != null && (state.getOwnerInheritedAccessEpoch().longValue() > inheritedEpoch.longValue()
                || (state.getOwnerInheritedAccessEpoch().longValue() == inheritedEpoch.longValue()
                && state.getApplicationAuthorizationEpoch().longValue() >= applicationEpoch.longValue()))) {
            return;
        }
        if (state == null) {
            state = new AkskOwnerAuthorizationTargetApplicationStateEntity();
            state.setTargetApplicationId(applicationId);
        }
        state.setActive(active);
        state.setApplicationAuthorizationEpoch(applicationEpoch);
        state.setOwnerInheritedAccessEpoch(inheritedEpoch);
        state.setUpdatedAt(now);
        targetStateRepository.save(state);
    }

    private void upsertProjection(String source, String subject, Long applicationId, int active, Long ownerEpoch,
                                  Long applicationEpoch, Long inheritedEpoch, Long projectionEpoch,
                                  Object authorization, Instant now) {
        String key = projectionKey(source, subject, applicationId);
        AkskOwnerAuthorizationProjectionEntity projection = projectionRepository.findById(key).orElse(null);
        if (projection != null && projection.getProjectionAccessEpoch().longValue() >= projectionEpoch.longValue()) {
            return;
        }
        if (projection == null) {
            projection = new AkskOwnerAuthorizationProjectionEntity();
            projection.setProjectionKey(key);
            projection.setOwnerSourceId(source);
            projection.setOwnerSubjectId(subject);
            projection.setTargetApplicationId(applicationId);
        }
        projection.setActive(active);
        projection.setOwnerSecurityEpoch(ownerEpoch);
        projection.setApplicationAuthorizationEpoch(applicationEpoch);
        projection.setOwnerInheritedAccessEpoch(inheritedEpoch);
        projection.setProjectionAccessEpoch(projectionEpoch);
        try {
            projection.setAuthorizationJson(authorization == null ? null
                    : PAYLOAD_OBJECT_MAPPER.writeValueAsString(authorization));
        } catch (Exception exception) {
            projection.setActive(INACTIVE);
            projection.setAuthorizationJson(null);
        }
        projection.setUpdatedAt(now);
        projectionRepository.save(projection);
    }

    private AkskOwnerAuthorizationCursorEntity requireCursor() {
        return cursorRepository.findById(STREAM_KEY).orElseGet(() -> cursorRepository.save(newCursor(0L, Instant.now())));
    }

    private AkskOwnerAuthorizationCursorEntity newCursor(Long sequence, Instant now) {
        AkskOwnerAuthorizationCursorEntity cursor = new AkskOwnerAuthorizationCursorEntity();
        cursor.setStreamKey(STREAM_KEY);
        cursor.setLastSourceSequence(sequence == null ? 0L : sequence);
        cursor.setUpdatedAt(now);
        return cursor;
    }

    /**
     * 已推进的 sequence 只能重放原 eventId，避免异常重复包被静默接受。
     */
    private boolean isKnownEvent(OwnerAuthorizationChange change) {
        AkskOwnerAuthorizationInboxEntity inbox = inboxRepository.findById(change.getEventId()).orElse(null);
        return inbox != null && change.getSourceSequence().equals(inbox.getSourceSequence());
    }

    private long leaseSeconds() {
        Integer value = properties.getOwnerAuthorization().getLeaseSeconds();
        return value == null || value.intValue() < 1 ? 30L : value.longValue();
    }

    private boolean validIdentity(String source, String subject, Long applicationId) {
        return source != null && source.length() <= 64 && subject != null
                && subject.length() <= SimpleAkskServerConstant.OWNER_SUBJECT_ID_MAX_LENGTH
                && applicationId != null && applicationId.longValue() > 0L;
    }

    private String ownerKey(String source, String subject) {
        return source + ":" + subject;
    }

    private String projectionKey(String source, String subject, Long applicationId) {
        return ownerKey(source, subject) + ":" + applicationId;
    }

    private boolean positive(Long value) {
        return value != null && value.longValue() > 0L;
    }

    private Long positiveNumber(Map<String, Object> payload, String name) {
        Object value = payload.get(name);
        return value instanceof Number && ((Number) value).longValue() > 0L
                ? Long.valueOf(((Number) value).longValue()) : null;
    }

    private Boolean bool(Map<String, Object> payload, String name) {
        return payload.get(name) instanceof Boolean ? (Boolean) payload.get(name) : null;
    }

    private String string(Map<String, Object> payload, String name) {
        return payload.get(name) instanceof String ? (String) payload.get(name) : null;
    }
}
