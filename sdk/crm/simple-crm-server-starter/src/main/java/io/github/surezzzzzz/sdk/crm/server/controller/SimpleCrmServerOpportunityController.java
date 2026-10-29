package io.github.surezzzzzz.sdk.crm.server.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import io.github.surezzzzzz.sdk.crm.server.constant.SimpleCrmServerConstant;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateOpportunityCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.command.TransferOpportunityCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.TransitionOpportunityCommand;
import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.Opportunity;
import io.github.surezzzzzz.sdk.crm.server.core.domain.opportunity.OpportunityStage;
import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmListCriteria;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPage;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerCommandService;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerOwnerResolverBridge;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerQueryService;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaginationHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 商机 REST 资源。 */
@RestController
@SimpleCrmServerComponent
public class SimpleCrmServerOpportunityController {
    private final SimpleCrmServerCommandService commandService;
    private final SimpleCrmServerQueryService queryService;
    private final SimpleCrmServerOwnerResolverBridge ownerResolver;

    public SimpleCrmServerOpportunityController(SimpleCrmServerCommandService commandService,
                                                SimpleCrmServerQueryService queryService,
                                                SimpleCrmServerOwnerResolverBridge ownerResolver) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.ownerResolver = ownerResolver;
    }

    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_OPPORTUNITY_CREATE)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_WRITE)
    @PostMapping("/api/crm/opportunities")
    public ResponseEntity<Map<String, Object>> create(
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false) String correlationId,
            @RequestBody Map<String, Object> body) {
        Opportunity value = commandService.createOpportunity(ownerResolver.resolve(), metadata(idempotencyKey, correlationId),
                new CreateOpportunityCommand(required(body, "customerId"), required(body, "title"),
                        optional(body, "primaryOfferingId"), optional(body, "contactId"), date(body, "expectedCloseDate"),
                        decimal(body, "estimatedAmount"), optional(body, "currency")));
        return ResponseEntity.created(URI.create("/api/crm/opportunities/" + value.getOpportunityId()))
                .body(view(value));
    }

    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_OPPORTUNITY_READ)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_READ)
    @GetMapping("/api/crm/opportunities/{opportunityId}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable String opportunityId,
                                                   @CurrentDataAccessPlan DataAccessPlan plan) {
        return ResponseEntity.ok(view(queryService.findOpportunity(ownerResolver.resolve(), plan, opportunityId)));
    }

    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_OPPORTUNITY_LIST)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_READ)
    @GetMapping("/api/crm/opportunities")
    public ResponseEntity<Map<String, Object>> list(
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam Map<String, String> parameters,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        CrmPageRequest pageRequest = SimpleCrmServerPaginationHelper.request(page, pageSize, cursor, limit, parameters);
        CrmPage<Opportunity> result = queryService.listOpportunities(ownerResolver.resolve(), plan,
                new CrmListCriteria(stage, customerId, null, null, keyword), cursor,
                SimpleCrmServerPaginationHelper.limit(limit, pageRequest), pageRequest);
        return ResponseEntity.ok(pageOf(result));
    }

    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_OPPORTUNITY_STAGE)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_WRITE)
    @PatchMapping(value = "/api/crm/opportunities/{opportunityId}", params = {"stage", "!owner"})
    public ResponseEntity<Map<String, Object>> transition(
            @PathVariable String opportunityId, @RequestParam String stage,
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        agree(body, "stage", stage);
        Opportunity value = commandService.transitionOpportunity(ownerResolver.resolve(), metadata(idempotencyKey, correlationId),
                new TransitionOpportunityCommand(opportunityId, stageOf(stage), optional(body, "lostReason")));
        return ResponseEntity.ok(view(value));
    }

    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_OPPORTUNITY_TRANSFER)
    @PatchMapping(value = "/api/crm/opportunities/{opportunityId}", params = {"owner", "!stage"})
    public ResponseEntity<Map<String, Object>> transfer(
            @PathVariable String opportunityId, @RequestParam String owner,
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false) String correlationId,
            @RequestBody(required = false) Map<String, Object> body) {
        agree(body, "owner", owner);
        return ResponseEntity.ok(view(commandService.transferOpportunity(ownerResolver.resolve(), metadata(idempotencyKey, correlationId),
                new TransferOpportunityCommand(opportunityId, owner))));
    }

    /**
     * 拒绝商机阶段与归属同时作为 PATCH 分派参数，避免动作权限路由歧义。
     *
     * @param opportunityId 商机标识
     * @return 400
     */
    @PatchMapping(value = "/api/crm/opportunities/{opportunityId}", params = {"stage", "owner"})
    public ResponseEntity<Map<String, Object>> rejectMixedPatch(@PathVariable String opportunityId) {
        throw CrmException.validation("stage/owner");
    }

    /**
     * 缺分派判别参数的兜底：安全 404。
     *
     * @param opportunityId 商机标识
     * @return 404
     */
    @PatchMapping("/api/crm/opportunities/{opportunityId}")
    public ResponseEntity<Map<String, Object>> patchOpportunityMissingDiscriminator(
            @PathVariable String opportunityId) {
        throw new io.github.surezzzzzz.sdk.crm.server.core.error.CrmException(
                io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                "opportunity");
    }

    private CrmCommandMetadata metadata(String idempotencyKey, String correlationId) {
        return new CrmCommandMetadata(correlationId == null || correlationId.trim().isEmpty()
                ? idempotencyKey : correlationId, idempotencyKey);
    }

    private static Map<String, Object> view(Opportunity value) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("opportunityId", value.getOpportunityId());
        body.put("customerId", value.getCustomerId());
        body.put("owner", value.getOwner());
        body.put("title", value.getTitle());
        body.put("primaryOfferingId", value.getPrimaryOfferingId());
        body.put("contactId", value.getContactId());
        body.put("expectedCloseDate", value.getExpectedCloseDate() == null ? null : value.getExpectedCloseDate().toString());
        body.put("estimatedAmount", value.getEstimatedAmount() == null ? null : value.getEstimatedAmount().toPlainString());
        body.put("currency", value.getCurrency());
        body.put("stage", value.getStage().getCode());
        body.put("lostReason", value.getLostReason());
        body.put("aggregateVersion", value.getAggregateVersion());
        body.put("createdAt", value.getCreatedAt().toString());
        body.put("updatedAt", value.getUpdatedAt().toString());
        return body;
    }

    private static Map<String, Object> pageOf(CrmPage<Opportunity> page) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> items = new java.util.ArrayList<Map<String, Object>>();
        for (Opportunity item : page.getItems()) { items.add(view(item)); }
        body.put("items", items);
        SimpleCrmServerPaginationHelper.metadata(body, page);
        return body;
    }

    private static String required(Map<String, Object> body, String key) {
        String value = optional(body, key);
        if (value == null) { throw CrmException.validation(key); }
        return value;
    }

    private static String optional(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) { return null; }
        String value = String.valueOf(body.get(key)).trim();
        return value.isEmpty() ? null : value;
    }

    private static LocalDate date(Map<String, Object> body, String key) {
        String value = optional(body, key);
        if (value == null) { return null; }
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException exception) {
            throw CrmException.validation(key);
        }
    }

    private static BigDecimal decimal(Map<String, Object> body, String key) {
        String value = optional(body, key);
        if (value == null) { return null; }
        try {
            return new BigDecimal(value);
        } catch (RuntimeException exception) {
            throw CrmException.validation(key);
        }
    }

    private static OpportunityStage stageOf(String value) {
        OpportunityStage stage = OpportunityStage.fromCode(value);
        if (stage == null) { throw CrmException.validation("stage"); }
        return stage;
    }

    private static void agree(Map<String, Object> body, String key, String queryValue) {
        String bodyValue = optional(body, key);
        if (bodyValue != null && !queryValue.equals(bodyValue)) {
            throw CrmException.validation(key + " 与查询参数不一致");
        }
    }
}
