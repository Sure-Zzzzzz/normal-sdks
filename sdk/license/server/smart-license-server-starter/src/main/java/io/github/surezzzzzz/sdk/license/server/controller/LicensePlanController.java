package io.github.surezzzzzz.sdk.license.server.controller;

import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.license.server.web.LicenseAccessScope;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.PlanVersionEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository;
import io.github.surezzzzzz.sdk.license.server.repository.PlanVersionRepository;
import io.github.surezzzzzz.sdk.license.server.web.DefaultLicensePrincipalResolver;
import io.github.surezzzzzz.sdk.license.server.web.LicenseApi;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpSupport;
import io.github.surezzzzzz.sdk.license.server.web.LicenseRequestContext;
import io.github.surezzzzzz.sdk.license.server.support.ModelRightsHelper;
import io.github.surezzzzzz.sdk.license.server.support.ModelPolicyWire;
import java.util.List;
import java.util.UUID;
import javax.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 授权方案 HTTP 面：方案版本草稿/发布/预填编辑。
 *
 * <p>方案=已发布模型版本的权益预填模板；授权创建时选方案带入展开值，
 * 展开值固化进授权（不依赖方案后续变化）。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
@RestController
@RequestMapping("/api/license/v1")
public class LicensePlanController {

    private final LicenseHttpSupport support = new LicenseHttpSupport();
    private final DefaultLicensePrincipalResolver resolver = new DefaultLicensePrincipalResolver();
    private final PlanVersionRepository plans;
    private final ModelVersionRepository models;

    public LicensePlanController(PlanVersionRepository plans, ModelVersionRepository models) {
        this.plans = plans;
        this.models = models;
    }

    @RequireApiPermission(LicenseApi.PLAN_READ)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_PRODUCT, action = "read")
    @GetMapping("/plans")
    public ResponseEntity<String> listPlans(@RequestParam(value = "productId", required = false) String productId,
                                            @CurrentDataAccessPlan
                                                    DataAccessPlan plan,
                                            HttpServletRequest request) {
        require(request);
        // 方案按产品维度收窄（license-product 资源）：受限范围走 IN，与产品筛选叠加
        LicenseAccessScope scope =
                LicenseAccessScope.from(plan, "productId");
        List<PlanVersionEntity> result = scope.resolve(
                () -> productId == null || productId.isEmpty()
                        ? plans.findAll()
                        : plans.findByProductId(productId),
                ids -> productId == null || productId.isEmpty()
                        ? plans.findByProductIdIn(ids)
                        : plans.findByProductIdIn(ids).stream()
                                .filter(item -> productId.equals(item.getProductId()))
                                .collect(java.util.stream.Collectors.toList()));
        ObjectNode body = StrictJson.mapper().createObjectNode();
        ArrayNode data = body.putArray("data");
        result.forEach(item -> data.add(view(item)));
        body.put("total", data.size());
        body.put("page", 1);
        body.put("size", Math.max(1, data.size()));
        body.put("totalPages", 1);
        return ResponseEntity.ok(body.toString());
    }

    @RequireApiPermission(LicenseApi.PLAN_MANAGE)
    @PostMapping("/plans")
    public ResponseEntity<String> createPlan(@RequestBody String body,
                                             @RequestHeader("Idempotency-Key") String idempotencyKey,
                                             HttpServletRequest request) {
        LicenseRequestContext context = require(request);
        support.requireDataAll(context, LicenseApi.RESOURCE_PRODUCT, "manage");
        ObjectNode input = support.object(body, "productId", "modelVersionId", "prefillRights");
        String productId = support.text(input, "productId", true);
        String modelVersionId = support.text(input, "modelVersionId", true);
        ModelVersionEntity model = models.findById(modelVersionId)
                .orElseThrow(LicenseHttpException::notFound);
        if (!model.getProductId().equals(productId)) {
            throw LicenseHttpException.unprocessable("模型版本不属于该产品");
        }
        if (!"PUBLISHED".equals(model.getStatus())) {
            throw LicenseHttpException.unprocessable("方案只能引用已发布模型版本");
        }
        ModelPolicyWire.parse(model.getSnapshot());
        String prefill = ModelRightsHelper.validate(input.get("prefillRights"),
                ModelRightsHelper.model(model.getSnapshot()), false);
        PlanVersionEntity created = plans.save(new PlanVersionEntity(
                "pv-" + UUID.randomUUID(), "plan-" + UUID.randomUUID(), productId,
                modelVersionId, 1L, prefill));
        return ResponseEntity.status(201).body(view(created).toString());
    }

    @RequireApiPermission(LicenseApi.PLAN_READ)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_PRODUCT, action = "read")
    @GetMapping("/plan-versions/{planVersionId}")
    public ResponseEntity<String> getVersion(@PathVariable String planVersionId,
                                            @CurrentDataAccessPlan
                                                    DataAccessPlan plan,
                                             HttpServletRequest request) {
        require(request);
        planInScope(plan, planVersionId);
        return ResponseEntity.ok(view(find(planVersionId)).putPOJO("prefillRights",
                parseObject(find(planVersionId).getPrefillRights())).toString());
    }

    @RequireApiPermission(LicenseApi.PLAN_MANAGE)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_PRODUCT, action = "manage")
    @PatchMapping("/plan-versions/{planVersionId}")
    public ResponseEntity<String> patchDraft(@PathVariable String planVersionId, @RequestBody String body,
                                             @CurrentDataAccessPlan
                                                     DataAccessPlan plan,
                                             HttpServletRequest request) {
        require(request);
        planInScope(plan, planVersionId);
        ObjectNode input = support.object(body, "prefillRights", "expectedRowVersion");
        PlanVersionEntity entity = find(planVersionId);
        if (!validRowVersion(input.get("expectedRowVersion"))
                || input.get("expectedRowVersion").asLong() != entity.getRowVersion()) {
            throw LicenseHttpException.conflict("版本已变化，请回读后重试");
        }
        try {
            ModelVersionEntity model = models.findById(entity.getModelVersionId())
                    .orElseThrow(LicenseHttpException::notFound);
            ModelPolicyWire.parse(model.getSnapshot());
            entity.updatePrefill(ModelRightsHelper.validate(input.get("prefillRights"),
                    ModelRightsHelper.model(model.getSnapshot()), false));
        } catch (IllegalStateException immutable) {
            throw LicenseHttpException.conflict("已发布方案不可变");
        }
        return ResponseEntity.ok(view(plans.save(entity)).toString());
    }

    @RequireApiPermission(LicenseApi.PLAN_MANAGE)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_PRODUCT, action = "manage")
    @PutMapping("/plan-versions/{planVersionId}/publication")
    public ResponseEntity<String> publish(@PathVariable String planVersionId, @RequestBody String body,
                                          @CurrentDataAccessPlan
                                                  DataAccessPlan plan,
                                          HttpServletRequest request) {
        require(request);
        ObjectNode input = support.object(body, "expectedRowVersion");
        PlanVersionEntity entity = find(planVersionId);
        planInScope(plan, entity);
        if (!validRowVersion(input.get("expectedRowVersion"))
                || input.get("expectedRowVersion").asLong() != entity.getRowVersion()) {
            throw LicenseHttpException.conflict("版本已变化，请回读后重试");
        }
        try {
            entity.publish();
        } catch (IllegalStateException invalid) {
            throw LicenseHttpException.conflict("仅草稿可发布");
        }
        return ResponseEntity.ok(view(plans.save(entity)).toString());
    }

    // ===== helpers =====

    /** 方案版本的产品维度 DATA 范围校验（license-product 资源）。 */
    private void planInScope(DataAccessPlan plan,
                             String planVersionId) {
        planInScope(plan, find(planVersionId));
    }

    private void planInScope(DataAccessPlan plan,
                             PlanVersionEntity entity) {
        LicenseAccessScope.from(plan, "productId")
                .requireVisible(entity.getProductId());
    }

    private PlanVersionEntity find(String planVersionId) {
        return plans.findById(planVersionId).orElseThrow(LicenseHttpException::notFound);
    }

    private LicenseRequestContext require(HttpServletRequest request) {
        LicenseRequestContext context = support.context(resolver, request);
        request.setAttribute("licenseRequestId", context.getRequestId());
        return context;
    }

    private Object parseObject(String json) {
        try {
            return StrictJson.mapper().readTree(json);
        } catch (Exception failure) {
            return java.util.Collections.emptyMap();
        }
    }

    private static ObjectNode view(PlanVersionEntity entity) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("planVersionId", entity.getPlanVersionId());
        view.put("planId", entity.getPlanId());
        view.put("productId", entity.getProductId());
        view.put("modelVersionId", entity.getModelVersionId());
        view.put("version", entity.getVersion());
        view.put("status", entity.getStatus());
        view.put("rowVersion", entity.getRowVersion());
        return view;
    }

    /** 行版本必须保持 JSON 整数语义，禁止字符串或小数被 Jackson 强制转换。 */
    private static boolean validRowVersion(com.fasterxml.jackson.databind.JsonNode value) {
        return value != null && value.isIntegralNumber() && value.canConvertToLong();
    }
}
