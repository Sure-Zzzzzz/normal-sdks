package io.github.surezzzzzz.sdk.license.server.controller;

import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.license.server.web.LicenseAccessScope;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.model.Decision;
import io.github.surezzzzzz.sdk.license.core.model.OnlineAuthorizationRequest;
import io.github.surezzzzzz.sdk.license.core.model.SubjectKind;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import io.github.surezzzzzz.sdk.license.server.service.OnlineDecisionService;
import io.github.surezzzzzz.sdk.license.server.constant.RevocationConstant;
import io.github.surezzzzzz.sdk.license.server.constant.LicenseValidationConstant;
import io.github.surezzzzzz.sdk.license.server.web.DefaultLicensePrincipalResolver;
import io.github.surezzzzzz.sdk.license.server.web.LicenseApi;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpSupport;
import io.github.surezzzzzz.sdk.license.server.web.LicenseRequestContext;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在线判定 HTTP 面：POST /decisions。
 *
 * <p>纯 ONLINE/HYBRID 的权威判定入口：API 权限+授权存在+对象种类一致+管理状态
 * （含吊销台账复查）+商业阶段+声明动作白名单的交集。
 * 模型额度约束必须以本次真实主体的持久额度引用满足，不以规则true补造额度。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
@lombok.extern.slf4j.Slf4j
@RestController
@RequestMapping("/api/license/v1")
public class LicenseDecisionController {

    private final LicenseHttpSupport support = new LicenseHttpSupport();
    private final DefaultLicensePrincipalResolver resolver = new DefaultLicensePrincipalResolver();
    private final OnlineDecisionService decisionService;
    private final ProductRepository products;

    public LicenseDecisionController(OnlineDecisionService decisionService,
                                     ProductRepository products) {
        this.decisionService = decisionService;
        this.products = products;
    }

    @RequireApiPermission(LicenseApi.DECISION_EVALUATE)
    @PostMapping("/decisions")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_GRANT, action = "use")
    public ResponseEntity<String> decide(@RequestBody String body,
                                         @CurrentDataAccessPlan
                                                 DataAccessPlan plan,
                                         HttpServletRequest request) {
        LicenseRequestContext context = require(request);
        ObjectNode input = support.object(body, "productId", "grantId", "subjectKind",
                "action", "sourceId", "subjectId", "activationId", "assignmentId", "businessOperationId", "facts",
                "reservationReferences");
        String productId = support.text(input, "productId", true);
        String grantId = support.text(input, "grantId", true);
        // 运行时判定按授权范围收窄：受限调用者只能判定自己范围内的授权（fail-closed）
        LicenseAccessScope.from(plan, "grantId")
                .requireAllowed(grantId);
        String subjectKind = support.text(input, "subjectKind", true);
        if (!SubjectKind.isValid(subjectKind)) {
            throw LicenseHttpException.unprocessable(RevocationConstant.INVALID_SUBJECT_KIND);
        }
        String action = support.text(input, "action", true);
        Map<String, Object> facts = factsOf(input);
        ProductEntity product;
        try {
            product = products.findById(productId).orElseThrow(LicenseHttpException::notFound);
        } catch (org.springframework.dao.DataAccessException unavailable) {
            throw LicenseHttpException.dependencyUnavailable(LicenseValidationConstant.DECISION_DEPENDENCY_UNAVAILABLE);
        }
        OnlineAuthorizationRequest authorization = buildRequest(productId, grantId, subjectKind,
                action, input, facts);
        String subjectId = support.text(input, "subjectId", false);
        String sourceId = support.text(input, "sourceId", false);
        if ("HUMAN".equals(context.getSubjectType())) {
            if (!"PERSON".equals(subjectKind)) {
                throw LicenseHttpException.forbidden();
            }
            String authenticatedSubject = context.getPrincipalId().substring("iam:".length());
            if (subjectId != null && !authenticatedSubject.equals(subjectId)
                    || sourceId != null && !RevocationConstant.IAM_SOURCE.equals(sourceId)) {
                throw LicenseHttpException.forbidden();
            }
            subjectId = authenticatedSubject;
            sourceId = RevocationConstant.IAM_SOURCE;
        } else if ("SERVICE".equals(context.getSubjectType()) && !"PERSON".equals(subjectKind)) {
            // DATA不是主体代授权；服务资格与部署callerBinding都核本次官方认证的服务及真实来源。
            String authenticatedSubject = context.getPrincipalId().substring("aksk:".length());
            String authenticatedSource = context.getAuthenticationSourceId();
            if (authenticatedSource == null || authenticatedSource.isEmpty()
                    || subjectId != null && !authenticatedSubject.equals(subjectId)
                    || sourceId != null && !authenticatedSource.equals(sourceId)) {
                throw LicenseHttpException.forbidden();
            }
            subjectId = authenticatedSubject;
            sourceId = authenticatedSource;
        } else {
            // 现有SPI只提供人员或服务两种官方主体；未知形态不能凭部署字段继续判定。
            throw LicenseHttpException.forbidden();
        }
        long now = java.time.Instant.now().getEpochSecond();
        // 身份与请求形态先核验，声明外动作也使用完整 DENY 契约；尚未求值的模型与商业阶段未知。
        Decision decision;
        try {
            decision = declaredActions(product).contains(action)
                    ? decisionService.decide(authorization, sourceId, subjectId, context.getAuthenticationSourceId(), now)
                    : decisionService.denyUndeclaredAction(authorization, sourceId, subjectId, now);
        } catch (org.springframework.dao.DataAccessException unavailable) {
            throw LicenseHttpException.dependencyUnavailable(LicenseValidationConstant.DECISION_DEPENDENCY_UNAVAILABLE);
        }
        log.debug("在线判定已完成：requestId={}，allowed={}，reasonCategory={}，modelVersion={}",
                context.getRequestId(), decision.isAllowed(), decision.getReasonCategory(), decision.getModelVersion());
        return ResponseEntity.ok(view(decision, context.getRequestId()).toString());
    }

    private OnlineAuthorizationRequest buildRequest(String productId, String grantId,
                                                    String subjectKind, String action,
                                                    ObjectNode input, Map<String, Object> facts) {
        String activationId = support.text(input, "activationId", false);
        String assignmentId = support.text(input, "assignmentId", false);
        String businessOperationId = support.text(input, "businessOperationId", false);
        if ("DEPLOYMENT".equals(subjectKind) ? assignmentId != null : activationId != null) {
            throw LicenseHttpException.unprocessable(RevocationConstant.INVALID_SUBJECT);
        }
        java.util.List<String> reservations = referencesOf(input);
        OnlineAuthorizationRequest request;
        try {
            if ("DEPLOYMENT".equals(subjectKind)) {
                request = OnlineAuthorizationRequest.deployment(productId, grantId, action,
                        activationId, facts, reservations);
            } else {
                request = OnlineAuthorizationRequest.personOrService(productId, grantId,
                        SubjectKind.valueOf(subjectKind), action, assignmentId, facts, reservations);
            }
            if (businessOperationId != null) {
                request = OnlineAuthorizationRequest.consumption(request, businessOperationId);
            }
        } catch (IllegalArgumentException failure) {
            throw LicenseHttpException.unprocessable(RevocationConstant.INVALID_SUBJECT);
        }
        return request;
    }

    private static java.util.List<String> referencesOf(ObjectNode input) {
        JsonNode node = input.get("reservationReferences");
        if (node == null) {
            return java.util.Collections.emptyList();
        }
        if (!node.isArray() || node.size() > 256) {
            throw LicenseHttpException.unprocessable("reservationReferences必须为最多256项的编号数组");
        }
        java.util.List<String> result = new java.util.ArrayList<>();
        java.util.Set<String> unique = new java.util.HashSet<>();
        for (JsonNode reference : node) {
            if (!reference.isTextual() || reference.textValue().trim().isEmpty()
                    || reference.textValue().length() > 128 || !unique.add(reference.textValue())) {
                throw LicenseHttpException.unprocessable("额度引用编号无效或重复");
            }
            result.add(reference.textValue());
        }
        return result;
    }

    private LicenseRequestContext require(HttpServletRequest request) {
        LicenseRequestContext context = support.context(resolver, request);
        request.setAttribute("licenseRequestId", context.getRequestId());
        return support.requireApiPermission(context, LicenseApi.DECISION_EVALUATE);
    }

    private static Map<String, Object> factsOf(ObjectNode input) {
        Map<String, Object> facts = new LinkedHashMap<>();
        JsonNode node = input.get("facts");
        if (node != null) {
            if (!node.isObject()) {
                throw LicenseHttpException.unprocessable(RevocationConstant.INVALID_FACTS);
            }
            node.fields().forEachRemaining(entry -> facts.put(entry.getKey(),
                    StrictJson.mapper().convertValue(entry.getValue(), Object.class)));
        }
        return facts;
    }

    private static java.util.Set<String> declaredActions(ProductEntity product) {
        java.util.Set<String> values = new java.util.HashSet<>();
        try {
            JsonNode declared = StrictJson.mapper().readTree(product.getDeclaredActions());
            if (declared == null || !declared.isArray()) {
                throw LicenseHttpException.dependencyUnavailable(LicenseValidationConstant.DECISION_DEPENDENCY_UNAVAILABLE);
            }
            for (JsonNode item : declared) {
                if (!item.isTextual()) {
                    throw LicenseHttpException.dependencyUnavailable(LicenseValidationConstant.DECISION_DEPENDENCY_UNAVAILABLE);
                }
                values.add(item.textValue());
            }
        } catch (Exception failure) {
            throw LicenseHttpException.dependencyUnavailable(LicenseValidationConstant.DECISION_DEPENDENCY_UNAVAILABLE);
        }
        return values;
    }

    private static ObjectNode view(Decision decision, String requestId) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("allowed", decision.isAllowed());
        view.put("action", decision.getAction());
        view.put("modelVersion", decision.getModelVersion());
        if (decision.getCommercialPhase() != null) {
            view.put("commercialPhase", decision.getCommercialPhase().name());
        } else {
            view.putNull("commercialPhase");
        }
        if (decision.getConnectivityPhase() != null) {
            view.put("connectivityPhase", decision.getConnectivityPhase().name());
        }
        view.put("evaluatedAt", decision.getEvaluatedAt());
        if (decision.getRecheckAt() != null) {
            view.put("recheckAt", decision.getRecheckAt());
        } else {
            view.putNull("recheckAt");
        }
        view.put("reasonCategory", decision.getReasonCategory() == null ? ""
                : decision.getReasonCategory().name());
        view.put("requestId", requestId == null ? "" : requestId);
        com.fasterxml.jackson.databind.node.ArrayNode fences = view.putArray("reservationFenceReferences");
        for (String fence : decision.getReservationFenceReferences()) {
            fences.add(fence);
        }
        if (decision.getProductId() != null) {
            view.put("productId", decision.getProductId());
            view.put("grantId", decision.getGrantId());
            view.set("subjectBinding", subjectBinding(decision.getSubjectBinding()));
            view.put("grantRevision", decision.getGrantRevision());
            view.put("grantStateVersion", decision.getGrantStateVersion());
        }
        return view;
    }

    private static ObjectNode subjectBinding(io.github.surezzzzzz.sdk.license.core.model.DecisionBinding binding) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("kind", binding.getKind().name());
        if (binding.getKind() == SubjectKind.DEPLOYMENT) {
            view.put("activationId", binding.getActivationId());
            if (binding.getActivationGeneration() != null) {
                view.put("activationGeneration", binding.getActivationGeneration());
            }
            return view;
        }
        ObjectNode subjectRef = view.putObject("subjectRef");
        subjectRef.put("kind", binding.getKind().name());
        subjectRef.put("sourceId", binding.getSourceId());
        subjectRef.put("subjectId", binding.getSubjectId());
        if (binding.getAssignmentId() != null) {
            view.put("assignmentId", binding.getAssignmentId());
            if (binding.getAssignmentGeneration() != null) {
                view.put("assignmentGeneration", binding.getAssignmentGeneration());
            }
        }
        return view;
    }
}
