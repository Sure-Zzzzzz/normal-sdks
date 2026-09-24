package io.github.surezzzzzz.sdk.auth.iam.server.service.authorization;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.claim.ApplicationAuthorizationContextClaimMapper;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.model.ApplicationAuthorizationContext;
import io.github.surezzzzzz.sdk.auth.iam.server.annotation.SimpleIamServerComponent;
import io.github.surezzzzzz.sdk.auth.iam.server.configuration.SimpleIamServerProperties;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ErrorCode;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.ServerErrorMessage;
import io.github.surezzzzzz.sdk.auth.iam.server.constant.SimpleIamServerConstant;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamAkskAuthorizationChangeEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamAkskAuthorizationChangeType;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.authorization.IamApplicationAuthorizationStateEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.trustedapplication.IamTrustedApplicationEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.entity.user.IamUserEntity;
import io.github.surezzzzzz.sdk.auth.iam.server.exception.SimpleIamServerException;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamAkskAuthorizationChangeRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.authorization.IamApplicationAuthorizationStateRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.trustedapplication.IamTrustedApplicationRepository;
import io.github.surezzzzzz.sdk.auth.iam.server.repository.user.IamUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * IAM 到 AKSK 授权控制面日志写侧。
 *
 * <p>只写已持久化业务状态的规范化最终快照；不使用应用事件替代持久化，
 * 不发 HTTP 回调，也不与 AKSK 共享数据库模型。</p>
 */
@SimpleIamServerComponent
@RequiredArgsConstructor
public class IamAkskAuthorizationChangeService {

    // ==================== 变更原因码（变更流 reason_code 列的稳定取值，改名即破坏协议） ====================
    public static final String REASON_OWNER_ENABLED = "OWNER_ENABLED";
    public static final String REASON_OWNER_DISABLED = "OWNER_DISABLED";
    public static final String REASON_OWNER_DELETED = "OWNER_DELETED";
    public static final String REASON_OWNER_SECURITY_EPOCH_SYNCHRONIZED = "OWNER_SECURITY_EPOCH_SYNCHRONIZED";
    public static final String REASON_APPLICATION_AUTHORIZATION_GRANTED = "APPLICATION_AUTHORIZATION_GRANTED";
    public static final String REASON_APPLICATION_AUTHORIZATION_REPLACED = "APPLICATION_AUTHORIZATION_REPLACED";
    public static final String REASON_APPLICATION_AUTHORIZATION_REVOKED = "APPLICATION_AUTHORIZATION_REVOKED";
    public static final String REASON_APPLICATION_ADMISSION_REVOKED = "APPLICATION_ADMISSION_REVOKED";
    public static final String REASON_APPLICATION_AUTHORIZATION_EPOCH_CHANGED = "APPLICATION_AUTHORIZATION_EPOCH_CHANGED";
    public static final String REASON_OWNER_INHERITANCE_CHANGED = "OWNER_INHERITANCE_CHANGED";
    public static final String REASON_OWNER_INHERITED_ACCESS_EPOCH_CHANGED = "OWNER_INHERITED_ACCESS_EPOCH_CHANGED";
    public static final String REASON_APPLICATION_PROJECTION_CREATED = "APPLICATION_PROJECTION_CREATED";
    public static final String REASON_APPLICATION_PROJECTION_RECOMPUTED = "APPLICATION_PROJECTION_RECOMPUTED";
    private static final long SCHEMA_VERSION = 1L;
    private static final ObjectMapper PAYLOAD_OBJECT_MAPPER = new ObjectMapper();
    // ==================== 变更流载荷键（与 AKSK reader 的解析契约字段，改名即破坏协议） ====================
    private static final String FIELD_OWNER_SOURCE_ID = "ownerSourceId";
    private static final String FIELD_OWNER_SUBJECT_ID = "ownerSubjectId";
    private static final String FIELD_TARGET_APPLICATION_ID = "targetApplicationId";
    private static final String FIELD_ACTIVE = "active";
    private static final String FIELD_OWNER_SECURITY_EPOCH = "ownerSecurityEpoch";
    private static final String FIELD_APPLICATION_AUTHORIZATION_EPOCH = "applicationAuthorizationEpoch";
    private static final String FIELD_OWNER_INHERITED_ACCESS_EPOCH = "ownerInheritedAccessEpoch";
    private static final String FIELD_PROJECTION_ACCESS_EPOCH = "projectionAccessEpoch";
    private static final String FIELD_IAM_AUTHORIZATION = "iamAuthorization";
    /**
     * 聚合键前缀：人员 / 目标应用 / 人员-应用投影。
     */
    private static final String AGGREGATE_OWNER_PREFIX = "owner:";
    private static final String AGGREGATE_APPLICATION_PREFIX = "application:";
    private static final String AGGREGATE_PROJECTION_PREFIX = "projection:";

    private final SimpleIamServerProperties properties;
    private final IamAkskAuthorizationChangeRepository changeRepository;
    private final IamApplicationAuthorizationStateRepository stateRepository;
    private final IamUserRepository userRepository;
    private final IamTrustedApplicationRepository trustedApplicationRepository;
    private final IamApplicationAuthorizationService applicationAuthorizationService;

    /**
     * 记录人员可用性最终态。
     */
    @Transactional
    public void recordOwnerState(IamUserEntity owner, String reasonCode) {
        if (owner == null || owner.getId() == null) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put(FIELD_OWNER_SOURCE_ID, properties.getOwnerSourceId());
        payload.put(FIELD_OWNER_SUBJECT_ID, String.valueOf(owner.getId()));
        payload.put(FIELD_ACTIVE, isActive(owner));
        payload.put(FIELD_OWNER_SECURITY_EPOCH, positiveOrOne(owner.getPermissionVersion() == null
                ? null : owner.getPermissionVersion() + 1L));
        save(IamAkskAuthorizationChangeType.OWNER_STATE, AGGREGATE_OWNER_PREFIX + owner.getId(), reasonCode, payload);
    }

    /**
     * 记录目标应用可用性与 OWNER_INHERITED 访问纪元的最终态。
     */
    @Transactional
    public void recordTargetApplicationState(Long applicationId, String reasonCode) {
        if (applicationId == null) {
            return;
        }
        IamTrustedApplicationEntity application = trustedApplicationRepository.findById(applicationId).orElse(null);
        IamApplicationAuthorizationStateEntity state = stateRepository.findById(applicationId).orElse(null);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put(FIELD_TARGET_APPLICATION_ID, applicationId);
        payload.put(FIELD_ACTIVE, isActive(application) && isInheritanceEnabled(state));
        payload.put(FIELD_APPLICATION_AUTHORIZATION_EPOCH, positiveOrOne(state == null ? null
                : state.getAuthorizationEpoch()));
        payload.put(FIELD_OWNER_INHERITED_ACCESS_EPOCH, positiveOrOne(state == null ? null
                : state.getOwnerInheritedAccessEpoch()));
        save(IamAkskAuthorizationChangeType.TARGET_APPLICATION_STATE, AGGREGATE_APPLICATION_PREFIX + applicationId,
                reasonCode, payload);
    }

    /**
     * 记录指定人员在目标应用下的三权投影最终态。
     */
    @Transactional
    public void recordProjection(IamApplicationAuthorizationEntity projection, String reasonCode) {
        if (projection == null || projection.getUserId() == null || projection.getApplicationId() == null) {
            return;
        }
        IamUserEntity owner = userRepository.findById(projection.getUserId()).orElse(null);
        IamTrustedApplicationEntity application = trustedApplicationRepository
                .findById(projection.getApplicationId()).orElse(null);
        IamApplicationAuthorizationStateEntity state = stateRepository
                .findById(projection.getApplicationId()).orElse(null);
        boolean active = isActive(owner) && isActive(application) && isInheritanceEnabled(state)
                && isActiveAndAdmitted(projection);
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put(FIELD_OWNER_SOURCE_ID, properties.getOwnerSourceId());
        payload.put(FIELD_OWNER_SUBJECT_ID, String.valueOf(projection.getUserId()));
        payload.put(FIELD_TARGET_APPLICATION_ID, projection.getApplicationId());
        payload.put(FIELD_ACTIVE, active);
        payload.put(FIELD_OWNER_SECURITY_EPOCH, positiveOrOne(projection.getOwnerSecurityEpoch()));
        payload.put(FIELD_APPLICATION_AUTHORIZATION_EPOCH, positiveOrOne(projection.getApplicationAuthorizationEpoch()));
        payload.put(FIELD_OWNER_INHERITED_ACCESS_EPOCH, positiveOrOne(state == null ? null
                : state.getOwnerInheritedAccessEpoch()));
        payload.put(FIELD_PROJECTION_ACCESS_EPOCH, positiveOrOne(projection.getProjectionAccessEpoch()));
        if (active) {
            Instant now = Instant.now();
            ApplicationAuthorizationContext context = applicationAuthorizationService.loadActiveContext(
                    projection.getUserId(), projection.getApplicationId(), now, now.plusSeconds(60L));
            if (context != null && context.isAdmitted()) {
                payload.put(FIELD_IAM_AUTHORIZATION, ApplicationAuthorizationContextClaimMapper.toClaim(context));
            } else {
                payload.put(FIELD_ACTIVE, false);
            }
        }
        save(IamAkskAuthorizationChangeType.OWNER_APPLICATION_PROJECTION,
                AGGREGATE_PROJECTION_PREFIX + projection.getUserId() + ":" + projection.getApplicationId(), reasonCode, payload);
    }

    private void save(IamAkskAuthorizationChangeType changeType, String aggregateKey, String reasonCode,
                      Map<String, Object> payload) {
        try {
            IamAkskAuthorizationChangeEntity entity = new IamAkskAuthorizationChangeEntity();
            entity.setEventId(UUID.randomUUID().toString());
            entity.setChangeType(changeType);
            entity.setAggregateKey(aggregateKey);
            entity.setReasonCode(reasonCode);
            entity.setSchemaVersion(SCHEMA_VERSION);
            entity.setPayloadJson(PAYLOAD_OBJECT_MAPPER.writeValueAsString(payload));
            entity.setOccurredAt(Instant.now());
            changeRepository.save(entity);
        } catch (Exception exception) {
            throw new SimpleIamServerException(ErrorCode.APPLICATION_AUTHORIZATION_CHANGE_PAYLOAD_INVALID,
                    ServerErrorMessage.APPLICATION_AUTHORIZATION_CHANGE_PAYLOAD_INVALID, exception);
        }
    }

    private long positiveOrOne(Long value) {
        return value == null || value.longValue() < 1L ? 1L : value.longValue();
    }

    private boolean isActive(IamUserEntity owner) {
        return owner != null && owner.getStatus() != null
                && owner.getStatus().intValue() == SimpleIamServerConstant.STATUS_ACTIVE;
    }

    private boolean isActive(IamTrustedApplicationEntity application) {
        return application != null && application.getStatus() != null
                && application.getStatus().intValue() == SimpleIamServerConstant.STATUS_ACTIVE;
    }

    private boolean isInheritanceEnabled(IamApplicationAuthorizationStateEntity state) {
        return state != null && state.getOwnerInheritanceEnabled() != null
                && state.getOwnerInheritanceEnabled().intValue() == SimpleIamServerConstant.STATUS_ACTIVE;
    }

    private boolean isActiveAndAdmitted(IamApplicationAuthorizationEntity projection) {
        return projection.getStatus() != null && projection.getAdmitted() != null
                && projection.getStatus().intValue() == SimpleIamServerConstant.STATUS_ACTIVE
                && projection.getAdmitted().intValue() == SimpleIamServerConstant.STATUS_ACTIVE;
    }
}
