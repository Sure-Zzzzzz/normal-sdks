package io.github.surezzzzzz.sdk.crm.server.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import io.github.surezzzzzz.sdk.crm.server.constant.SimpleCrmServerConstant;
import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.command.ReportFulfillmentResultCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.ResetFulfillmentItemCommand;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentConsumer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmListCriteria;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPage;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerCommandService;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerOwnerResolverBridge;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerQueryService;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaginationHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 履约控制器（RESTful：PATCH 履约项覆盖进度回报与重置）+ 消费者注册 + 操作记录查询。
 *
 * <p>履约回报=PATCH 履约项资源（body 含 sequence/state/resultData）；
 * 重置=PATCH 状态回 PENDING_DISPATCH。消费者注册为独立资源 POST/GET。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@RestController
@SimpleCrmServerComponent
public class SimpleCrmServerFulfillmentController {

    private final SimpleCrmServerCommandService commandService;
    private final SimpleCrmServerQueryService queryService;
    private final SimpleCrmServerOwnerResolverBridge ownerResolver;

    /**
     * 创建履约控制器。
     *
     * @param commandService 命令服务
     * @param queryService 查询服务
     * @param ownerResolver owner 解析桥
     */
    public SimpleCrmServerFulfillmentController(SimpleCrmServerCommandService commandService, SimpleCrmServerQueryService queryService,
                                    SimpleCrmServerOwnerResolverBridge ownerResolver) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.ownerResolver = ownerResolver;
    }

    // ===== 履约项 =====

    /**
     * 点查履约项（含冻结义务模板与协议版本）。
     *
     * @param fulfillmentId 履约项标识
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_FULFILLMENT_READ)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_READ)
    @GetMapping("/api/crm/fulfillment-items/{fulfillmentId}")
    public ResponseEntity<Map<String, Object>> getFulfillmentItem(
            @PathVariable String fulfillmentId,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        FulfillmentItem item = queryService.findFulfillmentItem(ownerResolver.resolve(), plan,
                fulfillmentId);
        return ResponseEntity.ok(itemView(item));
    }

    /**
     * 履约项列表（人=owner 维度、机器=派发归属维度）。
     *
     * @param state 状态过滤（可空）
     * @param orderId 订单过滤（可空）
     * @param cursor 游标（可空）
     * @param limit 每页上限
     * @param pageNumber 页码（可空，默认 1）
     * @param pageSize 页码模式每页条数（可空，默认 20）
     * @param parameters 原始查询参数，验证分页参数是否出现及空值
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_FULFILLMENT_LIST)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_READ)
    @GetMapping("/api/crm/fulfillment-items")
    public ResponseEntity<Map<String, Object>> listFulfillmentItems(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String orderId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(name = "page", required = false) Integer pageNumber,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam Map<String, String> parameters,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        CrmPageRequest pageRequest = SimpleCrmServerPaginationHelper.request(
                pageNumber, pageSize, cursor, limit, parameters);
        // 履约列表双轨：机器=派发归属谓词，人=DATA 范围（等值兜底∪授权范围）
        CrmPage<FulfillmentItem> page = queryService.listFulfillmentItems(
                ownerResolver.resolve(), plan,
                new CrmListCriteria(state, orderId, null, null), cursor,
                SimpleCrmServerPaginationHelper.limit(limit, pageRequest), pageRequest);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (FulfillmentItem item : page.getItems()) {
            items.add(itemView(item));
        }
        body.put("items", items);
        SimpleCrmServerPaginationHelper.metadata(body, page);
        return ResponseEntity.ok(body);
    }

    /**
     * 修改履约项（PATCH 动作分派：?sequence=N → 进度回报、?state=PENDING_DISPATCH → 人工重置；
     * 逐动作权限码，请求体对应字段携带时须与查询参数一致）。
     *
     * @author surezzzzzz
     */

    /**
     * 回报履约进度（序号严格递增）。
     *
     * @param fulfillmentId 履约项标识
     * @param sequence 回报序号（查询参数承载）
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体（state=IN_PROGRESS/COMPLETED，resultData/note 可选）
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_FULFILLMENT_REPORT)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_WRITE)
    @PatchMapping(value = "/api/crm/fulfillment-items/{fulfillmentId}",
            params = {"sequence", "!state"})
    public ResponseEntity<Map<String, Object>> reportFulfillmentResult(
            @PathVariable String fulfillmentId,
            @RequestParam int sequence,
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        requireAgrees(request, "sequence", sequence);
        io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner reporter =
                ownerResolver.resolve();
        FulfillmentItem advanced = commandService.reportFulfillmentResult(reporter,
                metadata(idempotencyKey, correlationId),
                new ReportFulfillmentResultCommand(fulfillmentId, sequence,
                        requiredText(request, "state"), text(request, "resultData"),
                        text(request, "note")));
        log.info("回报履约进度：fulfillmentId={}，sequence={}，reporter={}",
                fulfillmentId, sequence, reporter.getOwner());
        return ResponseEntity.ok(itemView(advanced));
    }

    /**
     * 人工重置履约项（水位保留，治理动作）。
     *
     * @param fulfillmentId 履约项标识
     * @param state 动作判别值（PENDING_DISPATCH）
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体（state 可携带，须一致）
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_FULFILLMENT_RESET)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_WRITE)
    @PatchMapping(value = "/api/crm/fulfillment-items/{fulfillmentId}",
            params = {"state=PENDING_DISPATCH", "!sequence"})
    public ResponseEntity<Map<String, Object>> resetFulfillmentItem(
            @PathVariable String fulfillmentId,
            @RequestParam String state,
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        requireAgrees(request, "state", state);
        io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner reporter =
                ownerResolver.resolve();
        FulfillmentItem reset = commandService.resetFulfillmentItem(reporter,
                metadata(idempotencyKey, correlationId),
                new ResetFulfillmentItemCommand(fulfillmentId));
        log.info("人工重置履约项：fulfillmentId={}，reporter={}", fulfillmentId, reporter.getOwner());
        return ResponseEntity.ok(itemView(reset));
    }

    /**
     * 拒绝履约项混用分派判别参数（sequence 与 state 不得同时出现在查询串）。
     *
     * @param fulfillmentId 履约项标识
     * @return 400
     */
    @PatchMapping(value = "/api/crm/fulfillment-items/{fulfillmentId}",
            params = {"sequence", "state"})
    public ResponseEntity<Map<String, Object>> rejectFulfillmentMixedPatch(
            @PathVariable String fulfillmentId) {
        throw io.github.surezzzzzz.sdk.crm.server.core.error.CrmException.validation("sequence/state");
    }

    /**
     * 缺分派判别参数的兜底：安全 404。
     *
     * @param fulfillmentId 履约项标识
     * @return 404
     */
    @PatchMapping("/api/crm/fulfillment-items/{fulfillmentId}")
    public ResponseEntity<Map<String, Object>> patchFulfillmentMissingDiscriminator(
            @PathVariable String fulfillmentId) {
        throw new io.github.surezzzzzz.sdk.crm.server.core.error.CrmException(
                io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                "fulfillmentItem");
    }

    // ===== 消费者 =====

    /**
     * 注册或更新履约消费者（consumerId 须与其 AKP clientId 一致——由 owner 解析桥核验）。
     *
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_FULFILLMENT_CONSUMER_ADMIN)
    @PostMapping("/api/crm/fulfillment-consumers")
    public ResponseEntity<Map<String, Object>> upsertFulfillmentConsumer(
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner owner =
                ownerResolver.resolve();
        String consumerId = text(request, "consumerId");
        if (owner.isService() && !owner.getOwner().endsWith(":" + consumerId)) {
            throw io.github.surezzzzzz.sdk.crm.server.core.error.CrmException.validation(
                    "consumerId 必须与 AKP clientId 一致");
        }
        commandService.upsertFulfillmentConsumer(owner,
                metadata(idempotencyKey, correlationId), new FulfillmentConsumer(consumerId,
                text(request, "capability"), intOf(request, "protocolVersion"),
                Boolean.parseBoolean(String.valueOf(request.getOrDefault("enabled", "true")))));
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("consumerId", consumerId);
        body.put("capability", text(request, "capability"));
        return ResponseEntity.ok(body);
    }

    /**
     * 列全部消费者（管理面）。
     *
     * @param pageNumber 页码（可空，默认 1）
     * @param pageSize 页码模式每页条数（可空，默认 20）
     * @param parameters 原始查询参数，验证分页参数是否出现及空值
     * @param cursor 不受支持的游标参数，出现即拒绝
     * @param limit 不受支持的游标条数参数，出现即拒绝
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_FULFILLMENT_CONSUMER_ADMIN)
    @GetMapping("/api/crm/fulfillment-consumers")
    public ResponseEntity<Map<String, Object>> listFulfillmentConsumers(
            @RequestParam(name = "page", required = false) Integer pageNumber,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam Map<String, String> parameters,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) String limit) {
        CrmPageRequest request = SimpleCrmServerPaginationHelper.numberedRequest(
                pageNumber, pageSize, cursor, limit, parameters);
        CrmPage<FulfillmentConsumer> consumersPage = request == null ? null
                : commandService.listFulfillmentConsumers(request);
        List<FulfillmentConsumer> consumers = consumersPage == null
                ? commandService.listFulfillmentConsumers() : consumersPage.getItems();
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (FulfillmentConsumer consumer : consumers) {
            Map<String, Object> view = new LinkedHashMap<String, Object>();
            view.put("consumerId", consumer.getConsumerId());
            view.put("capability", consumer.getCapability());
            view.put("protocolVersion", consumer.getProtocolVersion());
            view.put("enabled", consumer.isEnabled());
            items.add(view);
        }
        body.put("items", items);
        if (consumersPage != null) {
            SimpleCrmServerPaginationHelper.metadata(body, consumersPage);
        }
        return ResponseEntity.ok(body);
    }

    // ===== 操作记录 =====

    /**
     * 操作记录列表（业务级：谁在何时对哪个对象做了什么）。
     *
     * @param resourceId 资源过滤（可空）
     * @param cursor 游标（可空）
     * @param limit 每页上限
     * @param pageNumber 页码（可空，默认 1）
     * @param pageSize 页码模式每页条数（可空，默认 20）
     * @param parameters 原始查询参数，验证分页参数是否出现及空值
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_AUDIT_READ)
    @GetMapping("/api/crm/operation-logs")
    public ResponseEntity<Map<String, Object>> listOperationLogs(
            @RequestParam(required = false) String resourceId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(name = "page", required = false) Integer pageNumber,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam Map<String, String> parameters) {
        CrmPageRequest pageRequest = SimpleCrmServerPaginationHelper.request(
                pageNumber, pageSize, cursor, limit, parameters);
        CrmPage<io.github.surezzzzzz.sdk.crm.server.core.domain.audit.CrmAuditFact> page =
                queryService.listAuditFacts(ownerResolver.resolve(),
                        new CrmListCriteria(null, resourceId, null, null), cursor,
                SimpleCrmServerPaginationHelper.limit(limit, pageRequest), pageRequest);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (io.github.surezzzzzz.sdk.crm.server.core.domain.audit.CrmAuditFact fact : page.getItems()) {
            Map<String, Object> view = new LinkedHashMap<String, Object>();
            view.put("owner", fact.getOwner());
            view.put("action", fact.getAction().getCode());
            view.put("targetResourceType", fact.getTargetResourceType().getCode());
            view.put("targetResourceId", fact.getTargetResourceId());
            view.put("correlationId", fact.getCorrelationId());
            view.put("resultCode", fact.getResultCode());
            view.put("occurredAt", fact.getOccurredAt().toString());
            items.add(view);
        }
        body.put("items", items);
        SimpleCrmServerPaginationHelper.metadata(body, page);
        return ResponseEntity.ok(body);
    }

    // ===== 辅助 =====

    private Map<String, Object> itemView(FulfillmentItem item) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("fulfillmentId", item.getFulfillmentId());
        body.put("orderId", item.getOrderId());
        body.put("orderLineId", item.getOrderLineId());
        body.put("owner", item.getOwner());
        body.put("assignedConsumerId", item.getConsumerId());
        body.put("state", item.getState().getCode());
        body.put("reportSequence", item.getReportSequence());
        body.put("consumerProtocolVersion", item.getConsumerProtocolVersion());
        // 冻结的履约义务模板（消费者执行依据：给谁、签什么、按什么范围）
        Map<String, Object> obligation = new LinkedHashMap<String, Object>();
        obligation.put("subjectReference", item.getObligationTemplate().getSubjectReference());
        obligation.put("fulfillmentScope", item.getObligationTemplate().getFulfillmentScope());
        obligation.put("requiredConsumerCapability",
                item.getObligationTemplate().getRequiredConsumerCapability());
        body.put("obligation", obligation);
        if (item.getResult() != null) {
            body.put("resultData", item.getResult().getResultData());
            body.put("note", item.getResult().getNote());
            body.put("reportedBy", item.getResult().getReportedBy());
        }
        return body;
    }

    private CrmCommandMetadata metadata(String idempotencyKey, String correlationId) {
        return new CrmCommandMetadata(correlationId == null || correlationId.trim().isEmpty()
                ? UUID.randomUUID().toString() : correlationId, idempotencyKey);
    }

    /**
     * 校验请求体可选字段与查询参数分派值一致（不一致说明客户端两处判别值矛盾）。
     * body 可整体省略（判别权威在查询参数）；数值以字符串化等值比较，不做类型硬解析。
     *
     * @param request 请求体（可空）
     * @param key 字段名
     * @param expected 查询参数值
     */
    private void requireAgrees(Map<String, Object> request, String key, Object expected) {
        if (request == null) {
            return;
        }
        Object bodyValue = request.get(key);
        if (bodyValue != null && !String.valueOf(bodyValue).trim()
                .equals(String.valueOf(expected).trim())) {
            throw io.github.surezzzzzz.sdk.crm.server.core.error.CrmException.validation(
                    key + " 与查询参数不一致");
        }
    }
    private String requiredText(Map<String, Object> request, String key) {
        Object value = request.get(key);
        if (value == null || String.valueOf(value).trim().isEmpty()) {
            throw io.github.surezzzzzz.sdk.crm.server.core.error.CrmException.validation(key);
        }
        return String.valueOf(value).trim();
    }

    private String text(Map<String, Object> request, String key) {
        Object value = request.get(key);
        return value == null ? null : String.valueOf(value).trim();
    }

    private int intOf(Map<String, Object> request, String key) {
        Object value = request.get(key);
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

}
