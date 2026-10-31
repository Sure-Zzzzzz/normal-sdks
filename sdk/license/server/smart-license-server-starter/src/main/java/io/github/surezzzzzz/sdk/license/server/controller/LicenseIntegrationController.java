package io.github.surezzzzzz.sdk.license.server.controller;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import io.github.surezzzzzz.sdk.license.server.constant.RoleProjectionConstant;
import io.github.surezzzzzz.sdk.license.server.entity.IntegrationPlanEntity;
import io.github.surezzzzzz.sdk.license.server.integration.RoleProjectionService;
import io.github.surezzzzzz.sdk.license.server.repository.IntegrationPlanRepository;
import io.github.surezzzzzz.sdk.license.server.support.ProjectionRequestHelper;
import io.github.surezzzzzz.sdk.license.server.web.DefaultLicensePrincipalResolver;
import io.github.surezzzzzz.sdk.license.server.web.LicenseAccessScope;
import io.github.surezzzzzz.sdk.license.server.web.LicenseApi;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpSupport;
import io.github.surezzzzzz.sdk.license.server.web.LicenseRequestContext;
import java.util.Comparator;
import java.util.List;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * License 自有角色联动管理面：真实清单、严格人工回执与显式受委托执行。
 * DATA 按客户收窄读/确认，生成与执行仍为 ALL 治理动作；不转发人员令牌。
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
@RestController
@RequestMapping("/api/license/v1")
public class LicenseIntegrationController {
    private final LicenseHttpSupport support = new LicenseHttpSupport();
    private final DefaultLicensePrincipalResolver resolver = new DefaultLicensePrincipalResolver();
    private final IntegrationPlanRepository plans;
    private final RoleProjectionService projections;

    public LicenseIntegrationController(IntegrationPlanRepository plans, RoleProjectionService projections) {
        this.plans = plans;
        this.projections = projections;
    }

    /** 必须有批准的三权快照，不再把产品商业动作猜作 API 权限。 */
    @RequireApiPermission(LicenseApi.INTEGRATION_MANAGE)
    @PostMapping("/products/{productId}/role-projection-plans")
    public ResponseEntity<String> createPlan(@PathVariable String productId, @RequestBody String body,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                             HttpServletRequest request) {
        LicenseRequestContext context = require(request, LicenseApi.INTEGRATION_MANAGE);
        support.requireDataAll(context, LicenseApi.RESOURCE_INTEGRATION, "manage");
        ObjectNode input = support.object(body, "customerId", "applicationId", "desiredRules");
        ObjectNode rules = null;
        if (input.has("desiredRules")) {
            if (!input.get("desiredRules").isObject()) {
                throw LicenseHttpException.unprocessable(RoleProjectionConstant.INVALID_RULES);
            }
            rules = (ObjectNode) input.get("desiredRules");
        }
        IntegrationPlanEntity created = projections.create(support.text(input, "customerId", true), productId,
                RoleProjectionService.positiveId(support.text(input, "applicationId", true)), rules,
                ProjectionRequestHelper.requestDigest(context.getPrincipalId(), "POST /products/" + productId + "/role-projection-plans", idempotencyKey),
                ProjectionRequestHelper.inputDigest(input));
        return ResponseEntity.status(201).body(planView(created).toString());
    }

    /** 计划详情仅允许本客户 DATA 范围。 */
    @RequireApiPermission(LicenseApi.INTEGRATION_READ)
    @GetMapping("/role-projection-plans/{planId}")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_INTEGRATION, action = "read")
    public ResponseEntity<String> getPlan(@PathVariable String planId, @CurrentDataAccessPlan DataAccessPlan plan,
                                          HttpServletRequest request) {
        require(request, LicenseApi.INTEGRATION_READ);
        IntegrationPlanEntity entity = findPlan(planId);
        LicenseAccessScope.from(plan, "customerId").requireVisible(entity.getCustomerId());
        return ResponseEntity.ok(planView(entity).toString());
    }

    /** 回执确认环境/代数和实际目标；人工回执不展示为自动核验。 */
    @RequireApiPermission(LicenseApi.INTEGRATION_MANAGE)
    @PostMapping("/role-projection-plans/{planId}/confirmations")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_INTEGRATION, action = "manage")
    public ResponseEntity<String> confirm(@PathVariable String planId, @RequestBody String body,
                                          @CurrentDataAccessPlan DataAccessPlan plan, HttpServletRequest request) {
        require(request, LicenseApi.INTEGRATION_MANAGE);
        ObjectNode input = support.object(body, "expectedRevision", "organizationInstanceId", "bindingGeneration", "openRoleId");
        IntegrationPlanEntity entity = findPlan(planId);
        LicenseAccessScope.from(plan, "customerId").requireVisible(entity.getCustomerId());
        if (!input.path("expectedRevision").isIntegralNumber() || !input.path("expectedRevision").canConvertToLong()
                || !input.path("bindingGeneration").isIntegralNumber() || !input.path("bindingGeneration").canConvertToInt()) {
            throw LicenseHttpException.conflict(RoleProjectionConstant.STALE_PLAN);
        }
        IntegrationPlanEntity confirmed = projections.confirm(planId, input.path("expectedRevision").longValue(),
                support.text(input, "organizationInstanceId", true), input.path("bindingGeneration").intValue(),
                support.text(input, "openRoleId", false));
        return ResponseEntity.ok(planView(confirmed).toString());
    }

    /** 命令列表先按 DATA 在仓储中筛选，再形成页码视图。 */
    @RequireApiPermission(LicenseApi.INTEGRATION_READ)
    @GetMapping("/integration-commands")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_INTEGRATION, action = "read")
    public ResponseEntity<String> listCommands(HttpServletRequest request, @CurrentDataAccessPlan DataAccessPlan plan,
                                               @RequestParam(defaultValue = "1") int page,
                                               @RequestParam(defaultValue = "20") int size) {
        require(request, LicenseApi.INTEGRATION_READ);
        if (page < 1 || size < 1 || size > 200) { throw LicenseHttpException.unprocessable(RoleProjectionConstant.STALE_PLAN); }
        List<IntegrationPlanEntity> result = LicenseAccessScope.from(plan, "customerId")
                .resolve(plans::findAll, plans::findByCustomerIdIn);
        result.sort(Comparator.comparingLong(IntegrationPlanEntity::getDesiredRevision).reversed()
                .thenComparing(IntegrationPlanEntity::getPlanId));
        int from = (int) Math.min(((long) page - 1) * size, result.size());
        int to = Math.min(from + size, result.size());
        ObjectNode body = StrictJson.mapper().createObjectNode();
        ArrayNode data = body.putArray("data");
        result.subList(from, to).forEach(item -> data.add(planView(item)));
        body.put("total", result.size()); body.put("page", page); body.put("size", size);
        body.put("totalPages", ((long) result.size() + size - 1) / size);
        return ResponseEntity.ok(body.toString());
    }

    /** 命令详情与计划详情使用相同客户范围。 */
    @RequireApiPermission(LicenseApi.INTEGRATION_READ)
    @GetMapping("/integration-commands/{commandId}")
    @DataPermissionOperation(resource = LicenseApi.RESOURCE_INTEGRATION, action = "read")
    public ResponseEntity<String> getCommand(@PathVariable String commandId, @CurrentDataAccessPlan DataAccessPlan plan,
                                             HttpServletRequest request) {
        require(request, LicenseApi.INTEGRATION_READ);
        IntegrationPlanEntity entity = plans.findByCommandId(commandId).orElseThrow(LicenseHttpException::notFound);
        LicenseAccessScope.from(plan, "customerId").requireVisible(entity.getCustomerId());
        return ResponseEntity.ok(planView(entity).toString());
    }

    /** 缺请求体时只刷新 MANUAL；显式 AUTO 才进行实际 IAM 写入。 */
    @RequireApiPermission(LicenseApi.INTEGRATION_MANAGE)
    @PostMapping("/integration-commands/{commandId}/attempts")
    public ResponseEntity<String> attempt(@PathVariable String commandId,
                                          @RequestHeader("Idempotency-Key") String idempotencyKey,
                                          @RequestBody(required = false) String body, HttpServletRequest request) {
        LicenseRequestContext context = require(request, LicenseApi.INTEGRATION_MANAGE);
        support.requireDataAll(context, LicenseApi.RESOURCE_INTEGRATION, "manage");
        ObjectNode input = body == null || body.trim().isEmpty() ? StrictJson.mapper().createObjectNode()
                : support.object(body, "executionMode");
        String mode = input.has("executionMode") ? support.text(input, "executionMode", true) : RoleProjectionConstant.MANUAL;
        ObjectNode fingerprint = StrictJson.mapper().createObjectNode(); fingerprint.put("executionMode", mode);
        IntegrationPlanEntity attempted = projections.attempt(commandId, mode,
                ProjectionRequestHelper.requestDigest(context.getPrincipalId(), "POST /integration-commands/" + commandId + "/attempts", idempotencyKey),
                ProjectionRequestHelper.inputDigest(fingerprint));
        return ResponseEntity.ok(planView(attempted).toString());
    }

    /** 未知或漂移只能由治理者确认旧调用终止并回读实际事实后收口。 */
    @RequireApiPermission(LicenseApi.INTEGRATION_MANAGE)
    @PostMapping("/integration-commands/{commandId}/reconciliations")
    public ResponseEntity<String> reconcile(@PathVariable String commandId, @RequestBody String body,
                                            HttpServletRequest request) {
        LicenseRequestContext context = require(request, LicenseApi.INTEGRATION_MANAGE);
        support.requireDataAll(context, LicenseApi.RESOURCE_INTEGRATION, "manage");
        ObjectNode input = support.object(body, "expectedRevision", "organizationInstanceId", "bindingGeneration",
                "openRoleId", "priorCallTerminated");
        requireRecoveryFields(input);
        return ResponseEntity.ok(planView(projections.reconcile(commandId, input.path("expectedRevision").longValue(),
                support.text(input, "organizationInstanceId", true), input.path("bindingGeneration").intValue(),
                support.text(input, "openRoleId", false), input.path("priorCallTerminated").booleanValue())).toString());
    }

    /** 已知IAM清库/恢复后显式核对当前绑定；旧外部角色编号不被自动续用。 */
    @RequireApiPermission(LicenseApi.INTEGRATION_MANAGE)
    @PostMapping("/products/{productId}/role-projection-reconfirmations")
    public ResponseEntity<String> reconfirmEnvironment(@PathVariable String productId, @RequestBody String body,
                                                       HttpServletRequest request) {
        LicenseRequestContext context = require(request, LicenseApi.INTEGRATION_MANAGE);
        support.requireDataAll(context, LicenseApi.RESOURCE_INTEGRATION, "manage");
        ObjectNode input = support.object(body, "customerId", "applicationId", "organizationInstanceId",
                "bindingGeneration", "desiredRules", "priorCallTerminated");
        if (!input.path("bindingGeneration").isIntegralNumber() || !input.path("bindingGeneration").canConvertToInt()
                || !input.path("priorCallTerminated").isBoolean()
                || input.has("desiredRules") && !input.get("desiredRules").isObject()) {
            throw LicenseHttpException.unprocessable(RoleProjectionConstant.INVALID_BINDING);
        }
        ObjectNode rules = input.has("desiredRules") ? (ObjectNode) input.get("desiredRules") : null;
        return ResponseEntity.status(201).body(planView(projections.reconfirmEnvironment(
                support.text(input, "customerId", true), productId,
                RoleProjectionService.positiveId(support.text(input, "applicationId", true)),
                support.text(input, "organizationInstanceId", true), input.path("bindingGeneration").intValue(), rules,
                input.path("priorCallTerminated").booleanValue())).toString());
    }

    private static void requireRecoveryFields(ObjectNode input) {
        if (!input.path("expectedRevision").isIntegralNumber() || !input.path("expectedRevision").canConvertToLong()
                || !input.path("bindingGeneration").isIntegralNumber() || !input.path("bindingGeneration").canConvertToInt()
                || !input.path("priorCallTerminated").isBoolean()) {
            throw LicenseHttpException.unprocessable(RoleProjectionConstant.STALE_PLAN);
        }
    }

    private IntegrationPlanEntity findPlan(String planId) {
        return plans.findById(planId).orElseThrow(LicenseHttpException::notFound);
    }

    private LicenseRequestContext require(HttpServletRequest request, String permission) {
        LicenseRequestContext context = support.context(resolver, request);
        request.setAttribute("licenseRequestId", context.getRequestId());
        return support.requireApiPermission(context, permission);
    }

    private static ObjectNode planView(IntegrationPlanEntity entity) {
        ObjectNode view = entity.getProjectionSnapshot() == null ? StrictJson.mapper().createObjectNode()
                : StrictJson.parse(entity.getProjectionSnapshot(), ObjectNode.class);
        view.put("planId", entity.getPlanId()); view.put("commandId", entity.getCommandId());
        view.put("customerId", entity.getCustomerId()); view.put("productId", entity.getProductId());
        view.put("applicationId", entity.getApplicationId()); view.put("desiredRevision", entity.getDesiredRevision());
        view.put("state", entity.getState()); view.put("executionMode", entity.getExecutionMode());
        view.put("validUntil", entity.getValidUntil()); view.put("planValidUntil", entity.getValidUntil());
        if (entity.getConfirmedRevision() == null) { view.putNull("confirmedRevision"); }
        else { view.put("confirmedRevision", entity.getConfirmedRevision()); }
        if (entity.getFailureReason() == null) { view.putNull("failureReason"); }
        else { view.put("failureReason", entity.getFailureReason().matches(RoleProjectionConstant.EXECUTION_TOKEN_PATTERN)
                ? RoleProjectionConstant.EXECUTION_IN_PROGRESS : entity.getFailureReason()); }
        view.set("desiredRules", StrictJson.parse(entity.getDesiredRules(), ObjectNode.class));
        view.set("departmentDiff", StrictJson.parse(entity.getDepartmentDiff(), ObjectNode.class));
        view.put("rowVersion", entity.getRowVersion());
        return view;
    }
}
