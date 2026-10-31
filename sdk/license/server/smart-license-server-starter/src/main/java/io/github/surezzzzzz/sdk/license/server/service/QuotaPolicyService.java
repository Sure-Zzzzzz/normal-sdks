package io.github.surezzzzzz.sdk.license.server.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.model.Decision;
import io.github.surezzzzzz.sdk.license.core.model.EligibilityPolicy;
import io.github.surezzzzzz.sdk.license.core.model.OnlineAuthorizationRequest;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.core.model.ValidityPolicy;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.constant.GrantState;
import io.github.surezzzzzz.sdk.license.server.constant.QuotaConstant;
import io.github.surezzzzzz.sdk.license.server.constant.RevocationConstant;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository;
import io.github.surezzzzzz.sdk.license.server.support.GrantValidityHelper;
import io.github.surezzzzzz.sdk.license.server.support.ModelPolicyWire;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import java.util.Collections;
import lombok.Getter;

/**
 * 在线额度准入：在余额写入前核授权、商业期限、真实对象以及不可变模型中的额度定义。
 *
 * <p>DATA由入口执行；本服务不把DATA范围当主体代授权，也不读取IAM/CRM内部数据。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
public class QuotaPolicyService {

    private final GrantRepository grants;
    private final ModelVersionRepository models;
    private final LicenseRevocationGuard guard;
    private final SubjectAdmissionService admission;

    public QuotaPolicyService(GrantRepository grants, ModelVersionRepository models,
                              LicenseRevocationGuard guard, SubjectAdmissionService admission) {
        this.grants = grants;
        this.models = models;
        this.guard = guard;
        this.admission = admission;
    }

    /** 认证字段均由资源上下文传入；返回服务器确定的截止、限额与消费窗口。 */
    public Policy require(String productId, String requestedSubjectKind, String action, long expectedGrantRevision,
                          String grantId, String metric, String kind, String principalId, String sourceId,
                          String subjectType, String assignmentId, String activationId, long now) {
        GrantRevisionEntity grant = grants.findById(grantId).orElseThrow(LicenseHttpException::notFound);
        if (productId != null && !productId.equals(grant.getProductId())
                || requestedSubjectKind != null && !requestedSubjectKind.equals(grant.getSubjectKind())
                || expectedGrantRevision > 0L && expectedGrantRevision != grant.getGrantRevision()) {
            throw LicenseHttpException.conflict(QuotaConstant.UNUSABLE);
        }
        if (!GrantState.ENABLED.getCode().equals(grant.getState())
                || EligibilityPolicy.Mode.OFFLINE.getCode().equals(grant.getMode())) {
            throw LicenseHttpException.conflict(QuotaConstant.UNUSABLE);
        }
        ValidityPolicy validity;
        try {
            validity = GrantValidityHelper.fromGrant(grant);
        } catch (RuntimeException invalidStoredGrant) {
            throw LicenseHttpException.dependencyUnavailable(QuotaConstant.UNAVAILABLE);
        }
        if (validity.commercialPhaseAt(now) != Decision.CommercialPhase.ACTIVE) {
            throw LicenseHttpException.conflict(QuotaConstant.UNUSABLE);
        }
        if (!SubjectKind.isValid(grant.getSubjectKind())) {
            throw LicenseHttpException.dependencyUnavailable(QuotaConstant.UNAVAILABLE);
        }
        SubjectKind subjectKind = SubjectKind.fromCode(grant.getSubjectKind());
        String subjectId;
        String actualSource;
        if (QuotaConstant.HUMAN.equals(subjectType) && subjectKind == SubjectKind.PERSON
                && principalId.startsWith(QuotaConstant.IAM_PREFIX)) {
            subjectId = principalId.substring(QuotaConstant.IAM_PREFIX.length());
            actualSource = RevocationConstant.IAM_SOURCE;
        } else if (QuotaConstant.SERVICE.equals(subjectType) && subjectKind != SubjectKind.PERSON
                && principalId.startsWith(QuotaConstant.AKSK_PREFIX) && sourceId != null && !sourceId.isEmpty()) {
            subjectId = principalId.substring(QuotaConstant.AKSK_PREFIX.length());
            actualSource = sourceId;
        } else {
            throw LicenseHttpException.forbidden();
        }
        if (subjectKind == SubjectKind.DEPLOYMENT ? assignmentId != null : activationId != null) {
            throw LicenseHttpException.unprocessable(QuotaConstant.INVALID_INPUT);
        }
        OnlineAuthorizationRequest object = subjectKind == SubjectKind.DEPLOYMENT
                ? OnlineAuthorizationRequest.deployment(grant.getProductId(), grantId, action, activationId,
                Collections.emptyMap(), Collections.emptyList())
                : OnlineAuthorizationRequest.personOrService(grant.getProductId(), grantId, subjectKind, action,
                assignmentId, Collections.emptyMap(), Collections.emptyList());
        if (guard.onlineRejection(object, actualSource, subjectId) != null) {
            throw LicenseHttpException.forbidden();
        }
        if (EligibilityPolicy.Kind.CUSTOMER_MEMBERS.getCode().equals(grant.getEligibilityKind())) {
            SubjectAdmissionService.Admission result = admission.isAdmitted(grant.getCustomerId(), subjectId);
            if (result == SubjectAdmissionService.Admission.UNAVAILABLE) {
                throw LicenseHttpException.dependencyUnavailable(QuotaConstant.UNAVAILABLE);
            }
            if (result != SubjectAdmissionService.Admission.ADMITTED) {
                throw LicenseHttpException.forbidden();
            }
        }
        ModelVersionEntity model = grant.getModelVersionId() == null ? null
                : models.findById(grant.getModelVersionId()).orElse(null);
        ObjectNode snapshot = modelSnapshot(grant, model);
        boolean actionRequiresMetric = false;
        for (JsonNode required : snapshot.path("quotaConstraints").path(action)) {
            actionRequiresMetric |= metric.equals(required.textValue());
        }
        if (!snapshot.path("actions").has(action) || !actionRequiresMetric) {
            throw LicenseHttpException.unprocessable(QuotaConstant.INVALID_POLICY);
        }
        JsonNode definition = snapshot.path("quotaDefinitions").path(metric);
        if (!definition.isObject() || !kind.equals(definition.path("kind").asText())
                || !QuotaConstant.ONLINE.equals(definition.path("enforcement").asText())
                || !(QuotaConstant.CONCURRENT.equals(kind) || QuotaConstant.CONSUMPTION.equals(kind))) {
            throw LicenseHttpException.unprocessable(QuotaConstant.INVALID_POLICY);
        }
        ObjectNode rights;
        try {
            rights = StrictJson.parse(grant.getRightsJson(), ObjectNode.class);
        } catch (RuntimeException invalidStoredRights) {
            throw LicenseHttpException.dependencyUnavailable(QuotaConstant.UNAVAILABLE);
        }
        JsonNode limit = rights.path(definition.path("limitRight").asText());
        if (!limit.isIntegralNumber() || !limit.canConvertToLong() || limit.longValue() < 0L) {
            throw LicenseHttpException.conflict(QuotaConstant.INVALID_POLICY);
        }
        long commercialUntil = validity.getServiceUntil() == null ? Long.MAX_VALUE : validity.getServiceUntil();
        long validUntil = commercialUntil;
        long periodSeconds = QuotaConstant.NO_WINDOW;
        if (QuotaConstant.CONCURRENT.equals(kind)) {
            validUntil = Math.min(commercialUntil,
                    future(now, positiveInteger(definition, "maxReservationSeconds")));
        } else if (QuotaConstant.FIXED_WINDOW.equals(definition.path("window").asText())) {
            periodSeconds = positiveInteger(definition, "periodSeconds");
        } else if (!QuotaConstant.TOTAL.equals(definition.path("window").asText())) {
            throw LicenseHttpException.unprocessable(QuotaConstant.INVALID_POLICY);
        }
        return new Policy(limit.longValue(), validUntil, periodSeconds, validity.getStartsAt());
    }

    private static long future(long now, long seconds) {
        return now > Long.MAX_VALUE - seconds ? Long.MAX_VALUE : now + seconds;
    }

    /** 持久模型或授权选择漂移是依赖故障；不得冒充调用方参数错误或规则拒绝。 */
    static ObjectNode modelSnapshot(GrantRevisionEntity grant, ModelVersionEntity model) {
        if (grant == null || model == null || !grant.getProductId().equals(model.getProductId())
                || model.getVersion() < 1L
                || !("PUBLISHED".equals(model.getStatus()) || "RETIRED".equals(model.getStatus()))) {
            throw LicenseHttpException.dependencyUnavailable(QuotaConstant.UNAVAILABLE);
        }
        ObjectNode snapshot;
        try {
            snapshot = ModelPolicyWire.parse(model.getSnapshot());
        } catch (RuntimeException invalidStoredModel) {
            throw LicenseHttpException.dependencyUnavailable(QuotaConstant.UNAVAILABLE);
        }
        if (!contains(snapshot.path("subjectPolicy").path("allowedKinds"), grant.getSubjectKind())
                || !contains(snapshot.path("subjectPolicy").path("allowedEligibilityKinds"), grant.getEligibilityKind())
                || !contains(snapshot.path("connectivityPolicy").path("allowedModes"), grant.getMode())) {
            throw LicenseHttpException.dependencyUnavailable(QuotaConstant.UNAVAILABLE);
        }
        return snapshot;
    }

    private static boolean contains(JsonNode values, String expected) {
        for (JsonNode value : values) {
            if (expected != null && expected.equals(value.textValue())) {
                return true;
            }
        }
        return false;
    }

    private static long positiveInteger(JsonNode definition, String field) {
        JsonNode value = definition.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.longValue() <= 0L) {
            throw LicenseHttpException.conflict(QuotaConstant.INVALID_POLICY);
        }
        return value.longValue();
    }

    /** 同次模型与授权装载产生的额度执行参数，不接受请求体覆盖。 */
    @Getter
    public static final class Policy {

        private final long limit;
        private final long validUntil;
        private final long periodSeconds;
        private final long initialAnchorAt;

        public Policy(long limit, long validUntil, long periodSeconds, long initialAnchorAt) {
            this.limit = limit;
            this.validUntil = validUntil;
            this.periodSeconds = periodSeconds;
            this.initialAnchorAt = initialAnchorAt;
        }
    }
}
