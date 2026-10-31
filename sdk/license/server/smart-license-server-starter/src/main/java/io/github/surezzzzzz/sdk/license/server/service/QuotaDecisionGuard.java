package io.github.surezzzzzz.sdk.license.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.model.OnlineAuthorizationRequest;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.constant.QuotaConstant;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.QuotaReservationEntity;
import io.github.surezzzzzz.sdk.license.server.entity.SubjectAssignmentEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository;
import io.github.surezzzzzz.sdk.license.server.repository.QuotaReservationRepository;
import io.github.surezzzzzz.sdk.license.server.repository.SubjectAssignmentRepository;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.HashSet;
import java.util.Set;

/**
 * 模型动作额度约束的只读执行围栏：规则true不能代替真实额度操作或实名分配。
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
public class QuotaDecisionGuard {

    private final GrantRepository grants;
    private final ModelVersionRepository models;
    private final QuotaReservationRepository reservations;
    private final SubjectAssignmentRepository assignments;
    private final LicenseRevocationGuard revocations;

    public QuotaDecisionGuard(GrantRepository grants, ModelVersionRepository models,
                               QuotaReservationRepository reservations, SubjectAssignmentRepository assignments,
                               LicenseRevocationGuard revocations) {
        this.grants = grants;
        this.models = models;
        this.reservations = reservations;
        this.assignments = assignments;
        this.revocations = revocations;
    }

    /** 校验本次动作全部必需ONLINE指标及显式引用；消费引用必须匹配同一业务操作。 */
    public boolean satisfied(OnlineAuthorizationRequest request, String sourceId, String subjectId,
                              String authenticationSourceId, long now) {
        return satisfied(request, sourceId, subjectId, authenticationSourceId, -1L, -1L, now);
    }

    /**
     * 带授权版本围栏的额度校验；判定流程必须使用首次装载的 revision/stateVersion，
     * 防止暂停、吊销或商业修订在额度读取期间被旧快照继续消费。
     */
    public boolean satisfied(OnlineAuthorizationRequest request, String sourceId, String subjectId,
                              String authenticationSourceId, long expectedGrantRevision,
                              long expectedGrantStateVersion, long now) {
        GrantRevisionEntity grant = grants.findById(request.getGrantId()).orElse(null);
        // 授权在首次装载后被删除或不可见时必须确定拒绝，不能继续用空快照读取额度。
        if (grant == null) {
            return false;
        }
        if (expectedGrantRevision >= 0L
                && (grant.getGrantRevision() != expectedGrantRevision
                || grant.getGrantStateVersion() != expectedGrantStateVersion)) {
            return false;
        }
        ModelVersionEntity model = grant == null || grant.getModelVersionId() == null ? null
                : models.findById(grant.getModelVersionId()).orElse(null);
        ObjectNode snapshot = QuotaPolicyService.modelSnapshot(grant, model);
        JsonNode definitions = snapshot.path("quotaDefinitions");
        JsonNode required = snapshot.path("quotaConstraints").path(request.getAction());
        Set<String> requiredMetrics = new HashSet<>();
        if (required.isArray()) {
            for (JsonNode metric : required) {
                requiredMetrics.add(metric.textValue());
            }
        }
        Set<String> satisfied = new HashSet<>();
        Set<String> seen = new HashSet<>();
        String principalId = (request.getSubjectKind() == SubjectKind.PERSON
                ? QuotaConstant.IAM_PREFIX : QuotaConstant.AKSK_PREFIX) + subjectId;
        if (request.getReservationReferences().size() > 256) {
            return false;
        }
        for (String id : request.getReservationReferences()) {
            if (id == null || !seen.add(id)) {
                return false;
            }
            QuotaReservationEntity ref = reservations.findById(id).orElse(null);
            if (ref == null || !request.getGrantId().equals(ref.getGrantId())
                    || !request.getAction().equals(ref.getAction()) || grant.getGrantRevision() != ref.getGrantRevision()
                    || !principalId.equals(ref.getRequesterSubject())
                    || authenticationSourceId == null || !authenticationSourceId.equals(ref.getAuthenticationSource())
                    || !QuotaConstant.ACTIVE.equals(ref.getState()) || ref.getValidUntil() <= now
                    || !requiredMetrics.contains(ref.getMetric()) || ref.getQuantity() <= 0L) {
                return false;
            }
            JsonNode definition = definitions.path(ref.getMetric());
            if (!QuotaConstant.ONLINE.equals(definition.path("enforcement").asText())
                    || !ref.getKind().equals(definition.path("kind").asText())
                    || !(QuotaConstant.CONCURRENT.equals(ref.getKind()) || QuotaConstant.CONSUMPTION.equals(ref.getKind()))
                    || QuotaConstant.CONSUMPTION.equals(ref.getKind())
                    && !ref.getBusinessOperationId().equals(request.getBusinessOperationId())) {
                return false;
            }
            // 用操作台账原对象复核资格，替换后的新assignment不能借旧分配或旧占用继续使用。
            OnlineAuthorizationRequest object;
            if (request.getSubjectKind() == SubjectKind.DEPLOYMENT) {
                if (!java.util.Objects.equals(request.getActivationId(), ref.getActivationId())) {
                    return false;
                }
                object = OnlineAuthorizationRequest.deployment(request.getProductId(), request.getGrantId(),
                        request.getAction(), ref.getActivationId(), request.getFacts(), java.util.Collections.emptyList());
            } else {
                if (request.getAssignmentId() != null
                        && !request.getAssignmentId().equals(ref.getAssignmentId())) {
                    return false;
                }
                object = OnlineAuthorizationRequest.personOrService(request.getProductId(), request.getGrantId(),
                        request.getSubjectKind(), request.getAction(), ref.getAssignmentId(), request.getFacts(),
                        java.util.Collections.emptyList());
            }
            if (revocations.onlineRejection(object, sourceId, subjectId) != null) {
                return false;
            }
            satisfied.add(ref.getMetric());
        }
        for (String metric : requiredMetrics) {
            JsonNode definition = definitions.path(metric);
            if (!QuotaConstant.ONLINE.equals(definition.path("enforcement").asText())) {
                return false;
            }
            if ("NAMED_SEAT".equals(definition.path("kind").asText())) {
                if (!namedSeat(request, sourceId, subjectId, metric, grant.getGrantRevision())) {
                    return false;
                }
                satisfied.add(metric);
            }
        }
        return satisfied.containsAll(requiredMetrics);
    }

    private boolean namedSeat(OnlineAuthorizationRequest request, String sourceId, String subjectId,
                              String metric, long grantRevision) {
        for (SubjectAssignmentEntity assignment : assignments.findByGrantId(request.getGrantId())) {
            if (request.getAssignmentId() != null && !request.getAssignmentId().equals(assignment.getAssignmentId())
                    || assignment.getGrantRevision() != grantRevision
                    || !request.getSubjectKind().getCode().equals(assignment.getSubjectKind())
                    || !java.util.Objects.equals(sourceId, assignment.getSourceId())
                    || !java.util.Objects.equals(subjectId, assignment.getSubjectId())
                    || !QuotaConstant.ACTIVE.equals(assignment.getState())
                    || revocations.isRevoked(io.github.surezzzzzz.sdk.license.server.constant.RevocationTargetKind.ASSIGNMENT,
                    assignment.getAssignmentId())) {
                continue;
            }
            try {
                JsonNode metrics = StrictJson.mapper().readTree(assignment.getMetrics());
                if (metrics == null || !metrics.isArray()) {
                    throw LicenseHttpException.dependencyUnavailable(QuotaConstant.UNAVAILABLE);
                }
                for (JsonNode value : metrics) {
                    if (value.isTextual() && metric.equals(value.textValue())) {
                        return true;
                    }
                }
            } catch (java.io.IOException invalid) {
                throw LicenseHttpException.dependencyUnavailable(QuotaConstant.UNAVAILABLE);
            }
        }
        return false;
    }
}
