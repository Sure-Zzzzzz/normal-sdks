package io.github.surezzzzzz.sdk.license.server.service;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.Base64Url;
import io.github.surezzzzzz.sdk.license.core.codec.Protocol2Payload;
import io.github.surezzzzzz.sdk.license.core.codec.Protocol2PayloadWire;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.model.Decision;
import io.github.surezzzzzz.sdk.license.core.model.DecisionBinding;
import io.github.surezzzzzz.sdk.license.core.model.EligibilityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.OnlineAuthorizationRequest;
import io.github.surezzzzzz.sdk.license.core.model.SubjectBinding;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.constant.GrantState;
import io.github.surezzzzzz.sdk.license.server.constant.RevocationConstant;
import io.github.surezzzzzz.sdk.license.server.constant.RevocationTargetKind;
import io.github.surezzzzzz.sdk.license.server.entity.ActivationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningOperationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SigningProfileEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SubjectAssignmentEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ActivationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.RevocationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningOperationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SigningProfileRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SubjectAssignmentRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;

/**
 * License 吊销与对象执行围栏：只读本域事实，在在线判定、签发和交付处复查。
 *
 * <p>冻结原文仅解析校验，绝不重新序列化或修改签名输入。台账即时阻断后续服务端动作；
 * 已经离线交付的文件仍需客户端安装公开吊销资料，数据库更新不代表离线文件即时失效。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@SimpleLicenseServerComponent
public class LicenseRevocationGuard {

    private final RevocationRepository revocations;
    private final GrantRepository grants;
    private final SigningOperationRepository operations;
    private final ActivationRepository activations;
    private final SubjectAssignmentRepository assignments;
    private final SigningProfileRepository profiles;
    private final SubjectAdmissionService admission;

    /** 隔离领域测试构造；生产装配使用必需准入服务的完整构造。 */
    public LicenseRevocationGuard(RevocationRepository revocations, GrantRepository grants,
                                  SigningOperationRepository operations, ActivationRepository activations,
                                  SubjectAssignmentRepository assignments, SigningProfileRepository profiles) {
        this(revocations, grants, operations, activations, assignments, profiles, null);
    }

    /** 目录资格与吊销复查复用既有业务服务，不新增身份传输或读取身份数据库。 */
    @org.springframework.beans.factory.annotation.Autowired
    public LicenseRevocationGuard(RevocationRepository revocations, GrantRepository grants,
                                  SigningOperationRepository operations, ActivationRepository activations,
                                  SubjectAssignmentRepository assignments, SigningProfileRepository profiles,
                                  SubjectAdmissionService admission) {
        this.revocations = revocations;
        this.grants = grants;
        this.operations = operations;
        this.activations = activations;
        this.assignments = assignments;
        this.profiles = profiles;
        this.admission = admission;
    }

    /** 创建吊销前确认目标属于所声明种类，空号、跨种类编号与缺失父授权均拒绝。 */
    public void requireTargetExists(RevocationTargetKind kind, String targetId) {
        boolean exists;
        switch (kind) {
            case GRANT:
                exists = grants.existsById(targetId);
                break;
            case CREDENTIAL:
                SigningOperationEntity operation = operations.findByCredentialId(targetId);
                exists = operation != null && grants.existsById(operation.getGrantId());
                break;
            case ACTIVATION:
                ActivationEntity activation = activations.findById(targetId).orElse(null);
                exists = activation != null && grants.existsById(activation.getGrantId());
                break;
            case ASSIGNMENT:
                SubjectAssignmentEntity assignment = assignments.findById(targetId).orElse(null);
                exists = assignment != null && grants.existsById(assignment.getGrantId());
                break;
            case SIGNING_KEY:
                exists = profiles.existsByKid(targetId);
                break;
            default:
                throw LicenseHttpException.unprocessable(RevocationConstant.INVALID_KIND);
        }
        if (!exists) {
            throw LicenseHttpException.notFound();
        }
    }

    /** 返回吊销目标所属授权，供跨域履约在登记吊销事实前先完成停用。 */
    public Optional<GrantRevisionEntity> findGrantForTarget(RevocationTargetKind kind, String targetId) {
        switch (kind) {
            case GRANT:
                return grants.findById(targetId);
            case CREDENTIAL:
                SigningOperationEntity operation = operations.findByCredentialId(targetId);
                return operation == null ? Optional.empty() : grants.findById(operation.getGrantId());
            case ACTIVATION:
                ActivationEntity activation = activations.findById(targetId).orElse(null);
                return activation == null ? Optional.empty() : grants.findById(activation.getGrantId());
            case ASSIGNMENT:
                SubjectAssignmentEntity assignment = assignments.findById(targetId).orElse(null);
                return assignment == null ? Optional.empty() : grants.findById(assignment.getGrantId());
            case SIGNING_KEY:
                return Optional.empty();
            default:
                throw LicenseHttpException.unprocessable(RevocationConstant.INVALID_KIND);
        }
    }

    /** 在线动作的真实对象准入；sourceId来自边界已核验的主体，不接受裸请求体作为代理证明。 */
    public Decision.ReasonCategory onlineRejection(OnlineAuthorizationRequest request, String sourceId,
                                                    String subjectId) {
        if (isRevoked(RevocationTargetKind.GRANT, request.getGrantId())) {
            return Decision.ReasonCategory.REVOKED;
        }
        GrantRevisionEntity grant = grants.findById(request.getGrantId()).orElse(null);
        if (grant == null) {
            return Decision.ReasonCategory.SUBJECT_INELIGIBLE;
        }
        if (request.getSubjectKind() == SubjectKind.DEPLOYMENT) {
            if (isRevoked(RevocationTargetKind.ACTIVATION, request.getActivationId())) {
                return Decision.ReasonCategory.REVOKED;
            }
            ActivationEntity activation = activations.findById(request.getActivationId()).orElse(null);
            if (activation == null || !grant.getGrantId().equals(activation.getGrantId())
                    || !RevocationConstant.ACTIVE.equals(activation.getState())
                    || sourceId == null || subjectId == null
                    || !sourceId.equals(activation.getCallerSourceId())
                    || !subjectId.equals(activation.getCallerSubjectId())
                    || !hasNoDeviceEvidence(activation)) {
                return Decision.ReasonCategory.SUBJECT_INELIGIBLE;
            }
            return null;
        }
        if (EligibilityPolicy.Kind.ASSIGNED_SUBJECTS.getCode().equals(grant.getEligibilityKind())) {
            // 先确认请求编号属于当前授权，再返回不可撤回吊销；不能用任意编号制造
            // 吊销命中，也不能因缺来源而把 findAssignment 降级成跨来源通配。
            if (request.getAssignmentId() != null && !request.getAssignmentId().isEmpty()) {
                SubjectAssignmentEntity requested = assignments.findById(request.getAssignmentId()).orElse(null);
                if (requested != null && grant.getGrantId().equals(requested.getGrantId())
                        && isRevoked(RevocationTargetKind.ASSIGNMENT, requested.getAssignmentId())) {
                    return Decision.ReasonCategory.REVOKED;
                }
            }
            // 固定资格的 sourceId 是主体命名空间的一部分；缺失来源不能退化成跨源通配。
            if (sourceId == null || sourceId.isEmpty() || subjectId == null || subjectId.isEmpty()) {
                return Decision.ReasonCategory.SUBJECT_INELIGIBLE;
            }
            SubjectAssignmentEntity assignment = findAssignment(grant, request.getAssignmentId(),
                    request.getSubjectKind().getCode(), sourceId, subjectId);
            if (assignment == null) {
                return Decision.ReasonCategory.SUBJECT_INELIGIBLE;
            }
            if (isRevoked(RevocationTargetKind.ASSIGNMENT, assignment.getAssignmentId())) {
                return Decision.ReasonCategory.REVOKED;
            }
            return RevocationConstant.ACTIVE.equals(assignment.getState()) ? null
                    : Decision.ReasonCategory.SUBJECT_INELIGIBLE;
        }
        // 客户成员型资格必须来自 IAM 命名空间；缺来源不能被解释为默认可信来源。
        return request.getAssignmentId() != null && !request.getAssignmentId().isEmpty()
                || sourceId == null || sourceId.isEmpty() || !RevocationConstant.IAM_SOURCE.equals(sourceId)
                || subjectId == null || subjectId.isEmpty()
                ? Decision.ReasonCategory.SUBJECT_INELIGIBLE : null;
    }

    /**
     * 回显当前请求实际对应的对象绑定。该方法只返回与授权归属、认证主体相符的持久化事实，
     * 不把请求体中的 activationId/assignmentId 当作代数来源；状态是否可用仍由
     * {@link #onlineRejection(OnlineAuthorizationRequest, String, String)} 单独裁决。
     */
    public Optional<DecisionBinding> onlineBinding(OnlineAuthorizationRequest request, String sourceId,
                                                    String subjectId) {
        GrantRevisionEntity grant = grants.findById(request.getGrantId()).orElse(null);
        if (grant == null || !grant.getGrantId().equals(request.getGrantId())
                || !GrantState.ENABLED.getCode().equals(grant.getState())
                || isRevoked(RevocationTargetKind.GRANT, grant.getGrantId())) {
            return Optional.empty();
        }
        // 规则/额度求值完成后重新走一次对象准入，阻断期间发生的暂停、撤回和资格变更。
        if (onlineRejection(request, sourceId, subjectId) != null) {
            return Optional.empty();
        }
        // onlineRejection 读取的是独立快照；再次读取授权状态，避免旧实体把绑定带回响应。
        grant = grants.findById(request.getGrantId()).orElse(null);
        if (grant == null || !GrantState.ENABLED.getCode().equals(grant.getState())
                || isRevoked(RevocationTargetKind.GRANT, grant.getGrantId())) {
            return Optional.empty();
        }
        if (request.getSubjectKind() == SubjectKind.DEPLOYMENT) {
            ActivationEntity activation = activations.findById(request.getActivationId()).orElse(null);
            if (activation == null || !grant.getGrantId().equals(activation.getGrantId())
                    || !RevocationConstant.ACTIVE.equals(activation.getState())
                    || isRevoked(RevocationTargetKind.ACTIVATION, activation.getActivationId())
                    || !hasNoDeviceEvidence(activation)
                    || sourceId == null || subjectId == null
                    || !sourceId.equals(activation.getCallerSourceId())
                    || !subjectId.equals(activation.getCallerSubjectId())) {
                return Optional.empty();
            }
            return Optional.of(DecisionBinding.deployment(activation.getActivationId(),
                    activation.getActivationGeneration()));
        }
        if (sourceId == null || sourceId.isEmpty() || subjectId == null || subjectId.isEmpty()) {
            return Optional.empty();
        }
        if (EligibilityPolicy.Kind.CUSTOMER_MEMBERS.getCode().equals(grant.getEligibilityKind())) {
            // 最终绑定回显必须重新核验成员资格，避免首次判定后主体被移出组织仍返回ALLOW。
            if (admission == null) {
                return Optional.empty();
            }
            SubjectAdmissionService.Admission admitted = admission.isAdmitted(grant.getCustomerId(), subjectId);
            if (admitted != SubjectAdmissionService.Admission.ADMITTED
                    || !RevocationConstant.IAM_SOURCE.equals(sourceId)) {
                return Optional.empty();
            }
        }
        if (EligibilityPolicy.Kind.ASSIGNED_SUBJECTS.getCode().equals(grant.getEligibilityKind())) {
            SubjectAssignmentEntity assignment = findAssignment(grant, request.getAssignmentId(),
                    request.getSubjectKind().getCode(), sourceId, subjectId);
            if (assignment == null) {
                return Optional.empty();
            }
            if (!RevocationConstant.ACTIVE.equals(assignment.getState())
                    || isRevoked(RevocationTargetKind.ASSIGNMENT, assignment.getAssignmentId())) {
                return Optional.empty();
            }
            return Optional.of(DecisionBinding.personService(request.getSubjectKind(), sourceId, subjectId,
                    assignment.getAssignmentId(), assignment.getAssignmentGeneration()));
        }
        if (EligibilityPolicy.Kind.CUSTOMER_MEMBERS.getCode().equals(grant.getEligibilityKind())
                && RevocationConstant.IAM_SOURCE.equals(sourceId)) {
            return Optional.of(DecisionBinding.personService(request.getSubjectKind(), sourceId, subjectId,
                    null, null));
        }
        return Optional.empty();
    }

    /** 当前实际设备政策仅NONE；旧非空摘要或坏数据不能冒充已验证的设备证明。 */
    private static boolean hasNoDeviceEvidence(ActivationEntity activation) {
        try {
            com.fasterxml.jackson.databind.JsonNode value = StrictJson.mapper().readTree(activation.getComponentDigests());
            return value != null && value.isObject() && value.size() == 0;
        } catch (java.io.IOException | RuntimeException failure) {
            return false;
        }
    }

    /** 校验冻结凭据当前仍可签发/交付，五类吊销、资格代数和商业授权版本都不可绕过。 */
    public void requireCredentialUsable(SigningOperationEntity operation) {
        Protocol2Payload payload;
        ObjectNode header;
        try {
            payload = StrictJson.parse(new String(Base64Url.decode(operation.getFrozenPayload()),
                    StandardCharsets.UTF_8), Protocol2PayloadWire.class).toPayload();
            header = StrictJson.parse(new String(Base64Url.decode(operation.getFrozenHeader()),
                    StandardCharsets.UTF_8), ObjectNode.class);
        } catch (RuntimeException failure) {
            log.debug("凭据执行围栏拒绝冻结输入：operationId={}，异常类型={}",
                    operation.getOperationId(), failure.getClass().getSimpleName());
            throw LicenseHttpException.conflict(RevocationConstant.INVALID_SNAPSHOT);
        }
        GrantRevisionEntity grant = grants.findById(operation.getGrantId()).orElse(null);
        SigningProfileEntity profile = profiles.findById(operation.getSigningProfileId()).orElse(null);
        // 签名执行、恢复和交付共同复查，历史错误模式的任务也不能输出离线成品。
        if (grant == null || profile == null || !EligibilityPolicy.Mode.OFFLINE.getCode().equals(grant.getMode())
                // 动态客户成员没有可离线携带的、可独立验证的成员事实；存量坏数据也必须拒绝。
                || EligibilityPolicy.Kind.CUSTOMER_MEMBERS.getCode().equals(grant.getEligibilityKind())
                || !GrantState.ENABLED.getCode().equals(grant.getState())
                || isRevoked(RevocationTargetKind.GRANT, operation.getGrantId())
                || isRevoked(RevocationTargetKind.CREDENTIAL, operation.getCredentialId())
                || isRevoked(RevocationTargetKind.SIGNING_KEY, profile.getKid())) {
            throw LicenseHttpException.conflict(RevocationConstant.UNUSABLE);
        }
        if (!operation.getCredentialId().equals(payload.getJti())
                || !grant.getGrantId().equals(payload.getGrantId())
                || !grant.getProductId().equals(payload.getProductId())
                || !grant.getCustomerId().equals(payload.getCustomerId())
                || !grant.getProductId().equals(profile.getProductId())
                || !profile.getKid().equals(header.path("kid").asText())
                || grant.getGrantRevision() != payload.getGrantRevision()
                || grant.getGrantStateVersion() != payload.getGrantStateVersion()
                || !grant.getSubjectKind().equals(payload.getSubjectBinding().getKind().getCode())) {
            throw LicenseHttpException.conflict(RevocationConstant.INVALID_SNAPSHOT);
        }
        SubjectBinding binding = payload.getSubjectBinding();
        if (binding.getKind() == SubjectKind.DEPLOYMENT) {
            ActivationEntity activation = activations.findById(binding.activationId()).orElse(null);
            if (activation == null || !grant.getGrantId().equals(activation.getGrantId())
                    || !RevocationConstant.ACTIVE.equals(activation.getState())
                    || activation.getActivationGeneration() != binding.activationGeneration()
                    || isRevoked(RevocationTargetKind.ACTIVATION, binding.activationId())) {
                throw LicenseHttpException.conflict(RevocationConstant.UNUSABLE);
            }
        } else if (binding.assignmentId().isPresent()) {
            SubjectAssignmentEntity assignment = findAssignment(grant, binding.assignmentId().get(),
                    binding.getKind().getCode(), binding.subjectRef().getSourceId(), binding.subjectRef().getSubjectId());
            if (assignment == null || assignment.getGrantRevision() != grant.getGrantRevision()
                    || !RevocationConstant.ACTIVE.equals(assignment.getState())
                    || assignment.getAssignmentGeneration() != binding.assignmentGeneration().getAsLong()
                    || isRevoked(RevocationTargetKind.ASSIGNMENT, binding.assignmentId().get())) {
                throw LicenseHttpException.conflict(RevocationConstant.UNUSABLE);
            }
        } else if (EligibilityPolicy.Kind.ASSIGNED_SUBJECTS.getCode().equals(grant.getEligibilityKind())) {
            throw LicenseHttpException.conflict(RevocationConstant.INVALID_SNAPSHOT);
        }
        log.debug("凭据执行围栏已核实：operationId={}，grantId={}", operation.getOperationId(), grant.getGrantId());
    }

    /** 读取当前不可撤回事实，数据库失败原样透传，不能按未吊销继续执行。 */
    public boolean isRevoked(RevocationTargetKind kind, String targetId) {
        return targetId != null && revocations.findByTargetKindAndTargetId(kind.getCode(), targetId).isPresent();
    }

    private SubjectAssignmentEntity findAssignment(GrantRevisionEntity grant, String assignmentId,
                                                   String subjectKind, String sourceId, String subjectId) {
        if (assignmentId != null && !assignmentId.isEmpty()) {
            SubjectAssignmentEntity assignment = assignments.findById(assignmentId).orElse(null);
            return matches(assignment, grant.getGrantId(), grant.getGrantRevision(), subjectKind, sourceId, subjectId)
                    ? assignment : null;
        }
        List<SubjectAssignmentEntity> candidates = assignments.findByGrantId(grant.getGrantId());
        SubjectAssignmentEntity found = null;
        for (SubjectAssignmentEntity candidate : candidates) {
            if (matches(candidate, grant.getGrantId(), grant.getGrantRevision(), subjectKind, sourceId, subjectId)
                    && RevocationConstant.ACTIVE.equals(candidate.getState())) {
                if (found != null) {
                    return null;
                }
                found = candidate;
            }
        }
        return found;
    }

    private boolean matches(SubjectAssignmentEntity assignment, String grantId, long grantRevision, String kind,
                             String sourceId, String subjectId) {
        return assignment != null && grantId.equals(assignment.getGrantId())
                && assignment.getGrantRevision() == grantRevision
                && kind.equals(assignment.getSubjectKind()) && Objects.equals(subjectId, assignment.getSubjectId())
                && sourceId != null && sourceId.equals(assignment.getSourceId());
    }
}
