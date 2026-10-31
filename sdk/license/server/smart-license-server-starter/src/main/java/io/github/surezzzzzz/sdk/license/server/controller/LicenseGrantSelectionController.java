package io.github.surezzzzzz.sdk.license.server.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.constant.GrantSelectionConstant;
import io.github.surezzzzzz.sdk.license.server.entity.GrantRevisionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import io.github.surezzzzzz.sdk.license.server.repository.GrantRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository;
import io.github.surezzzzzz.sdk.license.server.service.SubjectAdmissionService;
import io.github.surezzzzzz.sdk.license.server.service.LicenseRevocationGuard;
import io.github.surezzzzzz.sdk.license.server.support.GrantValidityHelper;
import io.github.surezzzzzz.sdk.license.server.support.ModelPolicyWire;
import io.github.surezzzzzz.sdk.license.core.model.Decision;
import io.github.surezzzzzz.sdk.license.core.model.OnlineAuthorizationRequest;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.server.web.DefaultLicensePrincipalResolver;
import io.github.surezzzzzz.sdk.license.server.web.LicenseAccessScope;
import io.github.surezzzzzz.sdk.license.server.web.LicenseApi;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpSupport;
import io.github.surezzzzzz.sdk.license.server.web.LicenseRequestContext;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 运行端候选授权发现。候选只是当前真实身份和 DATA 范围内的可选资格，不能替代后续判定。
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
@Slf4j
@RestController
@RequestMapping("/api/license/v1")
public class LicenseGrantSelectionController {

    private static final Set<String> SUBJECT_KINDS = new HashSet<String>();

    static {
        SUBJECT_KINDS.add("DEPLOYMENT");
        SUBJECT_KINDS.add("PERSON");
        SUBJECT_KINDS.add("SERVICE");
    }

    private final GrantRepository grants;
    private final ModelVersionRepository models;
    private final SubjectAdmissionService admission;
    private final LicenseRevocationGuard guard;
    private final LicenseHttpSupport support = new LicenseHttpSupport();
    private final DefaultLicensePrincipalResolver resolver = new DefaultLicensePrincipalResolver();

    public LicenseGrantSelectionController(GrantRepository grants, ModelVersionRepository models,
                                           @org.springframework.beans.factory.annotation.Autowired(required = false)
                                                   SubjectAdmissionService admission,
                                           LicenseRevocationGuard guard) {
        this.grants = grants;
        this.models = models;
        this.admission = admission;
        this.guard = guard;
    }

    /**
     * 先按 product/subjectKind 从 SQL 取候选，再应用真实身份和当前商业阶段过滤，最后分页。
     * 这样 total 与 items 使用同一过滤事实，不把不可用授权误报为候选。
     */
    @RequireApiPermission(LicenseApi.DECISION_EVALUATE)
    @PostMapping("/grant-selections")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_GRANT, action = "use")
    public ResponseEntity<String> select(@RequestBody String body,
                                         @CurrentDataAccessPlan DataAccessPlan plan,
                                         HttpServletRequest request) {
        LicenseRequestContext context = require(request);
        ObjectNode input = support.object(body, "productId", "subjectKind", "action", "subjectEvidence",
                "activationId", "page", "size");
        String productId = support.text(input, "productId", true);
        String subjectKind = support.text(input, "subjectKind", true);
        String action = support.text(input, "action", true);
        if (!SUBJECT_KINDS.contains(subjectKind) || action.trim().isEmpty()) {
            throw LicenseHttpException.unprocessable(GrantSelectionConstant.INVALID_REQUEST);
        }
        int page = positiveInt(input, "page", GrantSelectionConstant.DEFAULT_PAGE, 1, Integer.MAX_VALUE);
        int size = positiveInt(input, "size", GrantSelectionConstant.DEFAULT_SIZE, 1, GrantSelectionConstant.MAX_SIZE);
        String activationId = support.text(input, "activationId", false);
        String subjectId = authenticatedSubject(context, subjectKind, activationId);
        LicenseAccessScope scope = LicenseAccessScope.from(plan, "grantId");
        if (input.has("subjectEvidence")) {
            // 本人通道不需要代理证明；尚无可信代理适配时不能忽略传入证明冒充已核验。
            throw LicenseHttpException.unprocessable(GrantSelectionConstant.PROXY_UNSUPPORTED);
        }
        Pageable pageable = PageRequest.of(0, GrantSelectionConstant.SCAN_BATCH_SIZE, Sort.by("grantId"));
        ArrayNode items = StrictJson.mapper().createArrayNode();
        long offset = ((long) page - 1L) * size;
        long total = 0L;
        long now = Instant.now().getEpochSecond();
        String afterId = null;
        try {
            while (true) {
                Page<GrantRevisionEntity> source = grants.findSelectionBatch(productId, subjectKind,
                        scope.restrictedIdsOrNull(), afterId, pageable);
                for (GrantRevisionEntity grant : source.getContent()) {
                    afterId = grant.getGrantId();
                    ModelVersionEntity model = model(grant);
                    ObjectNode snapshot = snapshot(model);
                    Decision.CommercialPhase phase = commercialPhase(grant, now);
                    if (!modelAllows(snapshot, grant) || !snapshot.path("actions").has(action)
                            || phase == Decision.CommercialPhase.NOT_STARTED
                            || phase == Decision.CommercialPhase.ENDED
                            || !eligible(grant, subjectId)
                            || !objectEligible(grant, subjectKind, action, activationId, context, subjectId)) {
                        continue;
                    }
                    if (phase == Decision.CommercialPhase.COMMERCIAL_GRACE
                            && !graceAction(snapshot, action)) {
                        continue;
                    }
                    if (total >= offset && items.size() < size) {
                        items.add(candidate(grant, model, phase));
                    }
                    total++;
                }
                if (source.getNumberOfElements() < pageable.getPageSize()) {
                    break;
                }
            }
        } catch (DataAccessException unavailable) {
            throw LicenseHttpException.dependencyUnavailable(GrantSelectionConstant.DEPENDENCY_UNAVAILABLE);
        }
        ObjectNode response = StrictJson.mapper().createObjectNode();
        response.set("items", items);
        response.put("total", total);
        response.put("page", page);
        response.put("size", size);
        response.put("totalPages", (total + size - 1L) / size);
        log.debug("候选授权发现完成：requestId={}，productId={}，subjectKind={}，total={}",
                context.getRequestId(), productId, subjectKind, total);
        return ResponseEntity.ok(response.toString());
    }

    private LicenseRequestContext require(HttpServletRequest request) {
        LicenseRequestContext context = support.context(resolver, request);
        if (context == null) {
            throw LicenseHttpException.unauthenticated();
        }
        request.setAttribute("licenseRequestId", context.getRequestId());
        return support.requireApiPermission(context, LicenseApi.DECISION_EVALUATE);
    }

    private static String authenticatedSubject(LicenseRequestContext context, String subjectKind,
                                               String activationId) {
        if ("PERSON".equals(subjectKind)) {
            if (!"HUMAN".equals(context.getSubjectType()) || !context.getPrincipalId().startsWith("iam:")) {
                throw LicenseHttpException.forbidden();
            }
            if (activationId != null) {
                throw LicenseHttpException.unprocessable(GrantSelectionConstant.UNEXPECTED_ACTIVATION);
            }
            return context.getPrincipalId().substring("iam:".length());
        }
        if ("SERVICE".equals(subjectKind)) {
            if (!"SERVICE".equals(context.getSubjectType()) || !context.getPrincipalId().startsWith("aksk:")) {
                throw LicenseHttpException.forbidden();
            }
            if (activationId != null) {
                throw LicenseHttpException.unprocessable(GrantSelectionConstant.UNEXPECTED_ACTIVATION);
            }
            return context.getPrincipalId().substring("aksk:".length());
        }
        if (activationId == null || activationId.trim().isEmpty()) {
            throw LicenseHttpException.unprocessable(GrantSelectionConstant.ACTIVATION_REQUIRED);
        }
        if (!"SERVICE".equals(context.getSubjectType()) || !context.getPrincipalId().startsWith("aksk:")) {
            throw LicenseHttpException.forbidden();
        }
        return context.getPrincipalId().substring("aksk:".length());
    }

    private boolean eligible(GrantRevisionEntity grant, String subjectId) {
        if ("CUSTOMER_MEMBERS".equals(grant.getEligibilityKind())) {
            if (admission == null) {
                throw LicenseHttpException.dependencyUnavailable(GrantSelectionConstant.DIRECTORY_UNAVAILABLE);
            }
            SubjectAdmissionService.Admission result = admission.isAdmitted(grant.getCustomerId(), subjectId);
            if (result == SubjectAdmissionService.Admission.UNAVAILABLE) {
                throw LicenseHttpException.dependencyUnavailable(GrantSelectionConstant.DIRECTORY_UNAVAILABLE);
            }
            return result == SubjectAdmissionService.Admission.ADMITTED;
        }
        return true;
    }

    private ModelVersionEntity model(GrantRevisionEntity grant) {
        if (grant.getModelVersionId() == null) {
            throw LicenseHttpException.dependencyUnavailable(GrantSelectionConstant.DEPENDENCY_UNAVAILABLE);
        }
        ModelVersionEntity model = models.findById(grant.getModelVersionId()).orElse(null);
        // 退役仅禁止新引用；已关联授权仍使用其不可变历史模型，不能因退役丢掉候选。
        if (model == null || !grant.getProductId().equals(model.getProductId()) || model.getVersion() < 1L
                || !("PUBLISHED".equals(model.getStatus()) || "RETIRED".equals(model.getStatus()))) {
            throw LicenseHttpException.dependencyUnavailable(GrantSelectionConstant.DEPENDENCY_UNAVAILABLE);
        }
        return model;
    }

    private static boolean graceAction(ObjectNode snapshot, String action) {
        for (JsonNode item : snapshot.path("validityPolicy").path("graceActions")) {
            if (item.isTextual() && action.equals(item.textValue())) {
                return true;
            }
        }
        return false;
    }

    /** 存量授权也要满足冻结模型声明，避免治理外的坏组合进入候选。 */
    private static boolean modelAllows(ObjectNode snapshot, GrantRevisionEntity grant) {
        JsonNode subject = snapshot.path("subjectPolicy");
        if (!contains(subject.path("allowedKinds"), grant.getSubjectKind())
                || !contains(subject.path("allowedEligibilityKinds"), grant.getEligibilityKind())
                || !contains(snapshot.path("connectivityPolicy").path("allowedModes"), grant.getMode())) {
            return false;
        }
        if ("DEPLOYMENT".equals(grant.getSubjectKind())) {
            return "ALLOCATED_DEPLOYMENTS".equals(grant.getEligibilityKind());
        }
        if ("ASSIGNED_SUBJECTS".equals(grant.getEligibilityKind())) {
            return true;
        }
        return "PERSON".equals(grant.getSubjectKind()) && "CUSTOMER_MEMBERS".equals(grant.getEligibilityKind())
                && !"OFFLINE".equals(grant.getMode()) && snapshot.path("organizationPolicy").path("required").asBoolean();
    }

    private static boolean contains(JsonNode values, String value) {
        for (JsonNode item : values) {
            if (item.isTextual() && item.textValue().equals(value)) return true;
        }
        return false;
    }

    private static ObjectNode snapshot(ModelVersionEntity model) {
        try {
            ObjectNode value = ModelPolicyWire.parse(model.getSnapshot());
            if (!model.getModelId().equals(value.path("id").asText())
                    || model.getVersion() != value.path("version").longValue()) {
                throw new IllegalArgumentException();
            }
            return value;
        } catch (RuntimeException invalid) {
            throw LicenseHttpException.dependencyUnavailable(GrantSelectionConstant.DEPENDENCY_UNAVAILABLE);
        }
    }

    /** 持久期限坏数据属于依赖异常，不能把请求本身合法的发现请求误报为 422。 */
    private static Decision.CommercialPhase commercialPhase(GrantRevisionEntity grant, long now) {
        try {
            return GrantValidityHelper.fromGrant(grant).commercialPhaseAt(now);
        } catch (LicenseHttpException invalid) {
            throw LicenseHttpException.dependencyUnavailable(GrantSelectionConstant.DEPENDENCY_UNAVAILABLE);
        }
    }

    private boolean objectEligible(GrantRevisionEntity grant, String kind, String action, String activationId,
                                    LicenseRequestContext context, String subjectId) {
        OnlineAuthorizationRequest candidate = "DEPLOYMENT".equals(kind)
                ? OnlineAuthorizationRequest.deployment(grant.getProductId(), grant.getGrantId(), action,
                activationId, java.util.Collections.emptyMap(), java.util.Collections.emptyList())
                : OnlineAuthorizationRequest.personOrService(grant.getProductId(), grant.getGrantId(),
                SubjectKind.valueOf(kind), action, null, java.util.Collections.emptyMap(),
                java.util.Collections.emptyList());
        String sourceId = "HUMAN".equals(context.getSubjectType()) ? "iam" : context.getAuthenticationSourceId();
        if (sourceId == null || guard == null) {
            throw LicenseHttpException.dependencyUnavailable(GrantSelectionConstant.DEPENDENCY_UNAVAILABLE);
        }
        return guard.onlineRejection(candidate, sourceId, subjectId) == null;
    }

    private ObjectNode candidate(GrantRevisionEntity grant, ModelVersionEntity model,
                                 Decision.CommercialPhase phase) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("grantId", grant.getGrantId());
        view.put("mode", grant.getMode());
        view.put("modelVersion", model.getVersion());
        view.put("commercialPhase", phase.name());
        return view;
    }

    private static int positiveInt(ObjectNode input, String field, int defaultValue, int min, int max) {
        JsonNode node = input.get(field);
        if (node == null || node.isNull()) {
            return defaultValue;
        }
        if (!node.isIntegralNumber() || !node.canConvertToInt()) {
            throw LicenseHttpException.unprocessable(String.format(GrantSelectionConstant.INVALID_PAGE_TYPE, field));
        }
        int value = node.intValue();
        if (value < min || value > max) {
            throw LicenseHttpException.unprocessable(String.format(GrantSelectionConstant.INVALID_PAGE_RANGE, field));
        }
        return value;
    }
}
