package io.github.surezzzzzz.sdk.license.server.controller;

import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.license.server.web.LicenseAccessScope;
import io.github.surezzzzzz.sdk.license.server.annotation.SimpleLicenseServerComponent;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.license.core.codec.RuleModelWire;
import io.github.surezzzzzz.sdk.license.core.codec.StrictJson;
import io.github.surezzzzzz.sdk.license.core.model.RuleModel;
import io.github.surezzzzzz.sdk.license.core.model.RuleModelValidator;
import io.github.surezzzzzz.sdk.license.server.entity.ModelVersionEntity;
import io.github.surezzzzzz.sdk.license.server.entity.ProductEntity;
import io.github.surezzzzzz.sdk.license.server.repository.ModelVersionRepository;
import io.github.surezzzzzz.sdk.license.server.repository.ProductRepository;
import io.github.surezzzzzz.sdk.license.server.web.DefaultLicensePrincipalResolver;
import io.github.surezzzzzz.sdk.license.server.web.LicenseApi;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpException;
import io.github.surezzzzzz.sdk.license.server.web.LicenseHttpSupport;
import io.github.surezzzzzz.sdk.license.server.web.LicenseRequestContext;
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
 * 模型 HTTP 面：模型列表、版本草稿编辑/发布/退役、受限试算。
 *
 * <p>快照按 license-condition/1 结构校验（字段类型/条件树/引用/预算经引擎发布级校验器）；
 * 发布不可变；试算只判定不签发不占额度。</p>
 *
 * @author surezzzzzz
 */
@SimpleLicenseServerComponent
@RestController
@RequestMapping("/api/license/v1")
public class LicenseModelController {

    private static final int SNAPSHOT_MAX_BYTES = 262144;

    private final LicenseHttpSupport support = new LicenseHttpSupport();
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    private void publish(Object event) {
        if (eventPublisher != null) {
            eventPublisher.publishEvent(event);
        }
    }
    private final DefaultLicensePrincipalResolver resolver = new DefaultLicensePrincipalResolver();
    private final ModelVersionRepository models;
    private final ProductRepository products;

    public LicenseModelController(ModelVersionRepository models, ProductRepository products) {
        this.models = models;
        this.products = products;
    }

    @RequireApiPermission(LicenseApi.MODEL_READ)
    @GetMapping("/models")
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_PRODUCT, action = "read")
    public ResponseEntity<String> listModels(@org.springframework.web.bind.annotation.RequestParam(value = "productId", required = false) String productId,
                                             @CurrentDataAccessPlan
                                                     DataAccessPlan plan,
                                             HttpServletRequest request) {
        require(request);
        // 模型按产品维度收窄（license-product 资源）：受限范围走 IN，与产品筛选条件叠加
        LicenseAccessScope scope =
                LicenseAccessScope.from(plan, "productId");
        java.util.function.Supplier<List<ModelVersionEntity>> allQuery = () -> productId == null || productId.isEmpty()
                ? models.findAll()
                : models.findByProductIdOrderByVersionDesc(productId);
        List<ModelVersionEntity> result = scope.resolve(allQuery, ids -> productId == null || productId.isEmpty()
                ? models.findByProductIdIn(ids)
                : models.findByProductIdIn(ids).stream()
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

    @RequireApiPermission(LicenseApi.MODEL_MANAGE)
    @PostMapping("/models")
    public ResponseEntity<String> createModel(@RequestBody String body,
                                              @RequestHeader("Idempotency-Key") String idempotencyKey,
                                              HttpServletRequest request) {
        LicenseRequestContext context = require(request);
        support.requireDataAll(context, LicenseApi.RESOURCE_PRODUCT, "manage");
        ObjectNode input = support.object(body, "productId", "snapshot");
        String productId = support.text(input, "productId", true);
        String modelId = "mdl-" + UUID.randomUUID();
        ProductEntity product = products.findById(productId).orElseThrow(LicenseHttpException::notFound);
        String snapshot = validatedSnapshot(input.get("snapshot"), modelId, 1L, product);
        ModelVersionEntity created = models.save(new ModelVersionEntity(
                "mv-" + UUID.randomUUID(), modelId, productId, 1L, snapshot));
        return ResponseEntity.status(201).body(view(created).toString());
    }

    @RequireApiPermission(LicenseApi.MODEL_READ)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_PRODUCT, action = "read")
    @GetMapping("/model-versions/{modelVersionId}")
    public ResponseEntity<String> getVersion(@PathVariable String modelVersionId,
                                            @CurrentDataAccessPlan
                                                    DataAccessPlan plan,
                                            HttpServletRequest request) {
        require(request);
        // 详情形态附带快照原文：管理面草稿编辑回填的唯一来源（列表只给摘要）
        ModelVersionEntity entity = find(modelVersionId);
        modelInScope(plan, entity);
        ObjectNode detail = view(entity);
        try {
            detail.set("snapshot", StrictJson.mapper().readTree(entity.getSnapshot()));
        } catch (java.io.IOException parseFailure) {
            throw LicenseHttpException.unprocessable("快照不可解析");
        }
        return ResponseEntity.ok(detail.toString());
    }

    @RequireApiPermission(LicenseApi.MODEL_MANAGE)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_PRODUCT, action = "manage")
    @PatchMapping("/model-versions/{modelVersionId}")
    public ResponseEntity<String> patchDraft(@PathVariable String modelVersionId,
                                             @RequestBody String body,
                                             @CurrentDataAccessPlan
                                                     DataAccessPlan plan,
                                             HttpServletRequest request) {
        require(request);
        modelInScope(plan, modelVersionId);
        ObjectNode input = support.object(body, "snapshot", "expectedRowVersion");
        ModelVersionEntity entity = find(modelVersionId);
        if (!validRowVersion(input.get("expectedRowVersion"))
                || input.get("expectedRowVersion").asLong() != entity.getRowVersion()) {
            throw LicenseHttpException.conflict("版本已变化，请回读后重试");
        }
        try {
            ProductEntity product = products.findById(entity.getProductId())
                    .orElseThrow(LicenseHttpException::notFound);
            entity.updateSnapshot(validatedSnapshot(input.get("snapshot"), entity.getModelId(),
                    entity.getVersion(), product));
        } catch (IllegalStateException immutable) {
            throw LicenseHttpException.conflict("已发布快照不可变");
        }
        return ResponseEntity.ok(view(models.save(entity)).toString());
    }

    @RequireApiPermission(LicenseApi.MODEL_PUBLISH)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_PRODUCT, action = "publish")
    @PutMapping("/model-versions/{modelVersionId}/publication")
    public ResponseEntity<String> publish(@PathVariable String modelVersionId,
                                          @RequestBody String body,
                                          @CurrentDataAccessPlan
                                                  DataAccessPlan plan,
                                          HttpServletRequest request) {
        LicenseRequestContext pubContext = require(request);
        ObjectNode input = support.object(body, "expectedRowVersion");
        ModelVersionEntity entity = find(modelVersionId);
        modelInScope(plan, entity);
        if (!validRowVersion(input.get("expectedRowVersion"))
                || input.get("expectedRowVersion").asLong() != entity.getRowVersion()) {
            throw LicenseHttpException.conflict("版本已变化，请回读后重试");
        }
        try {
            ProductEntity product = products.findById(entity.getProductId())
                    .orElseThrow(LicenseHttpException::notFound);
            entity.updateSnapshot(validatedSnapshot(StrictJson.parse(entity.getSnapshot(), ObjectNode.class),
                    entity.getModelId(), entity.getVersion(), product));
            entity.publish();
        } catch (IllegalStateException invalid) {
            throw LicenseHttpException.conflict("仅草稿可发布");
        }
        models.save(entity);
        publish(new io.github.surezzzzzz.sdk.license.server.event.LicenseAuditEvents.ModelPublished(
                pubContext.getPrincipalId(), pubContext.getRequestId(), modelVersionId,
                entity.getProductId(), entity.getVersion()));
        return ResponseEntity.ok(view(entity).toString());
    }

    @RequireApiPermission(LicenseApi.MODEL_PUBLISH)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_PRODUCT, action = "publish")
    @PatchMapping("/model-versions/{modelVersionId}/state")
    public ResponseEntity<String> retire(@PathVariable String modelVersionId,
                                         @RequestBody String body,
                                         @CurrentDataAccessPlan
                                                 DataAccessPlan plan,
                                         HttpServletRequest request) {
        require(request);
        ObjectNode input = support.object(body, "state", "expectedRowVersion");
        ModelVersionEntity entity = find(modelVersionId);
        modelInScope(plan, entity);
        if (!validRowVersion(input.get("expectedRowVersion"))
                || input.get("expectedRowVersion").asLong() != entity.getRowVersion()) {
            throw LicenseHttpException.conflict("版本已变化，请回读后重试");
        }
        if (!"RETIRED".equals(support.text(input, "state", true))) {
            throw LicenseHttpException.unprocessable("仅允许转换到 RETIRED");
        }
        try {
            entity.retire();
        } catch (IllegalStateException invalid) {
            throw LicenseHttpException.conflict("仅已发布可退役");
        }
        return ResponseEntity.ok(view(models.save(entity)).toString());
    }

    @RequireApiPermission(LicenseApi.MODEL_READ)
    @io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation(
            resource = LicenseApi.RESOURCE_PRODUCT, action = "read")
    @PostMapping("/model-versions/{modelVersionId}/evaluations")
    public ResponseEntity<String> evaluate(@PathVariable String modelVersionId,
                                           @RequestBody String body,
                                           @CurrentDataAccessPlan
                                                   DataAccessPlan plan,
                                           HttpServletRequest request) {
        require(request);
        ModelVersionEntity entity = find(modelVersionId);
        modelInScope(plan, entity);
        ObjectNode input = support.object(body, "action", "rights", "facts");
        String action = support.text(input, "action", true);
        // 受限试算（真求值）：动作声明 × 条件树对（假设权益∪事实）求值；只判定不签发；
        // 缺事实/类型不符=确定不通过（declared=false），不猜默认值
        ObjectNode result = StrictJson.mapper().createObjectNode();
        result.put("action", action);
        try {
            RuleModel model = RuleModelWire.fromSnapshot(
                    StrictJson.parse(entity.getSnapshot(), ObjectNode.class));
            if (!model.getActions().containsKey(action)) {
                result.put("declared", false);
                result.put("message", "动作未在模型声明");
                return ResponseEntity.ok(result.toString());
            }
            java.util.Map<String, Object> rights = typedValues(input.get("rights"));
            java.util.Map<String, Object> facts = typedValues(input.get("facts"));
            boolean allowed;
            try {
                allowed = io.github.surezzzzzz.sdk.license.core.model.ConditionEvaluator.evaluate(model, action,
                        io.github.surezzzzzz.sdk.license.core.model.FactValues.of(model, rights, facts, false));
            } catch (RuntimeException missingOrInvalid) {
                result.put("declared", false);
                result.put("message", "事实缺失或类型不符");
                return ResponseEntity.ok(result.toString());
            }
            result.put("declared", allowed);
            if (!allowed) {
                result.put("message", "条件树求值不满足");
            }
            return ResponseEntity.ok(result.toString());
        } catch (RuntimeException failure) {
            throw LicenseHttpException.unprocessable("快照不可求值：" + failure.getClass().getSimpleName());
        }
    }

    // ===== helpers =====

    /** 输入值域透传（数字/布尔/文本/数组原样进 FactValues；类型错由求值期拒）。 */
    private static java.util.Map<String, Object> typedValues(com.fasterxml.jackson.databind.JsonNode node) {
        java.util.Map<String, Object> values = new java.util.LinkedHashMap<String, Object>();
        if (node != null && node.isObject()) {
            node.fields().forEachRemaining(entry ->
                    values.put(entry.getKey(), StrictJson.mapper().convertValue(entry.getValue(), Object.class)));
        }
        return values;
    }

    /** 模型版本的产品维度 DATA 范围校验（license-product 资源）。 */
    private void modelInScope(DataAccessPlan plan,
                              String modelVersionId) {
        modelInScope(plan, find(modelVersionId));
    }

    private void modelInScope(DataAccessPlan plan,
                              ModelVersionEntity entity) {
        LicenseAccessScope.from(plan, "productId")
                .requireVisible(entity.getProductId());
    }

    private ModelVersionEntity find(String modelVersionId) {
        return models.findById(modelVersionId).orElseThrow(LicenseHttpException::notFound);
    }

    private LicenseRequestContext require(HttpServletRequest request) {
        LicenseRequestContext context = support.context(resolver, request);
        request.setAttribute("licenseRequestId", context.getRequestId());
        return context;
    }

    /** 快照结构校验+预算检查：经引擎发布级校验器（未知字段/坏引用/超预算拒绝）。 */
    private String validatedSnapshot(JsonNode node, String modelId, long version, ProductEntity product) {
        if (node == null || !node.isObject()) {
            throw LicenseHttpException.unprocessable("快照必须为 JSON 对象");
        }
        String json;
        try {
            json = io.github.surezzzzzz.sdk.license.server.support.ModelPolicyWire.validate(
                    node, modelId, version, product.getSupportedCapabilities());
        } catch (LicenseHttpException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw LicenseHttpException.unprocessable("快照未通过政策校验");
        }
        if (json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > SNAPSHOT_MAX_BYTES) {
            throw LicenseHttpException.payloadTooLarge();
        }
        try {
            RuleModel model = RuleModelWire.fromSnapshot(
                    StrictJson.parse(json, ObjectNode.class));
            RuleModelValidator.validate(model);
        } catch (LicenseHttpException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw LicenseHttpException.unprocessable("快照未通过结构校验（字段/条件树/引用/预算）");
        }
        return json;
    }

    private static ObjectNode view(ModelVersionEntity entity) {
        ObjectNode view = StrictJson.mapper().createObjectNode();
        view.put("modelVersionId", entity.getModelVersionId());
        view.put("modelId", entity.getModelId());
        view.put("productId", entity.getProductId());
        view.put("version", entity.getVersion());
        view.put("status", entity.getStatus());
        view.put("rowVersion", entity.getRowVersion());
        // 列表/详情给摘要（字段数/动作数）；快照原文经专用读取接口交付
        try {
            JsonNode snapshot = StrictJson.mapper().readTree(entity.getSnapshot());
            int fieldCount = snapshot.path("fields").path("rights").size()
                    + snapshot.path("fields").path("facts").size();
            int actionCount = snapshot.path("actions").size();
            view.put("fieldsSummary", fieldCount + " 个字段");
            view.put("actionsSummary", actionCount + " 个动作");
        } catch (Exception failure) {
            view.put("fieldsSummary", "—");
            view.put("actionsSummary", "—");
        }
        return view;
    }

    /** 行版本必须保持 JSON 整数语义，禁止字符串或小数被 Jackson 强制转换。 */
    private static boolean validRowVersion(com.fasterxml.jackson.databind.JsonNode value) {
        return value != null && value.isIntegralNumber() && value.canConvertToLong();
    }
}
