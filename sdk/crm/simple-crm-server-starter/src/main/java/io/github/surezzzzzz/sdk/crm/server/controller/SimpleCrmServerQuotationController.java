package io.github.surezzzzzz.sdk.crm.server.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import io.github.surezzzzzz.sdk.crm.server.constant.SimpleCrmServerConstant;
import io.github.surezzzzzz.sdk.crm.server.core.command.ConfirmQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.command.EditQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.IssueQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.QuotationLineCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.RenegotiateQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.TransferQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.WithdrawQuotationCommand;
import io.github.surezzzzzz.sdk.crm.server.core.domain.common.Money;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmListCriteria;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPage;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerCommandService;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerOwnerResolverBridge;
import io.github.surezzzzzz.sdk.crm.server.service.SimpleCrmServerQueryService;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerPaginationHelper;
import io.github.surezzzzzz.sdk.crm.server.support.SimpleCrmServerQuotationTransferResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.net.URI;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 报价与订单控制器（RESTful：PATCH 状态变更覆盖签发/确认/撤回）。
 *
 * <p>报价生命周期：POST 创建草稿 → PUT 编辑草稿 → PATCH 状态变更（issued/confirmed/withdrawn）；
 * 重议=POST 新建（body 含 sourceQuotationId）。订单只读。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@RestController
@SimpleCrmServerComponent
public class SimpleCrmServerQuotationController {

    private final SimpleCrmServerCommandService commandService;
    private final SimpleCrmServerQueryService queryService;
    private final SimpleCrmServerOwnerResolverBridge ownerResolver;

    /**
     * 创建报价控制器。
     *
     * @param commandService 命令服务
     * @param queryService 查询服务
     * @param ownerResolver owner 解析桥
     */
    public SimpleCrmServerQuotationController(SimpleCrmServerCommandService commandService, SimpleCrmServerQueryService queryService,
                                  SimpleCrmServerOwnerResolverBridge ownerResolver) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.ownerResolver = ownerResolver;
    }

    // ===== 报价 =====

    /**
     * 创建报价草稿（body 含 sourceQuotationId 时为重议派生）。
     *
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体
     * @return 201 + Location
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_QUOTATION_CREATE)
    @PostMapping(value = "/api/crm/quotations", params = "!sourceQuotationId")
    public ResponseEntity<Map<String, Object>> createQuotation(
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        CrmOwner owner = ownerResolver.resolve();
        CrmCommandMetadata meta = metadata(idempotencyKey, correlationId);
        List<QuotationLineCommand> lines = lineCommands(request);
        io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation draft =
                commandService.createQuotationResource(owner, meta,
                        new CreateQuotationCommand(requiredText(request, "customerId"),
                                text(request, "opportunityId"),
                                OffsetDateTime.parse(requiredText(request, "validUntil")).toInstant(),
                                lines));
        log.info("创建报价：quotationId={}，owner={}", draft.getQuotationId(),
                owner.getOwner());
        return created("/api/crm/quotations/" + draft.getQuotationId(),
                currentQuotationView(draft));
    }

    /**
     * 重议派生新草稿（以源报价为蓝本，确认时旧报价同事务 SUPERSEDED；查询参数分派挂专属码，D10 同构）。
     *
     * @param sourceQuotationId 源报价标识（查询参数承载；body 携带同名字段时须一致）
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体（sourceQuotationId 可携带，须一致）
     * @return 201 + Location
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_QUOTATION_RENEGOTIATE)
    @PostMapping(value = "/api/crm/quotations", params = "sourceQuotationId")
    public ResponseEntity<Map<String, Object>> renegotiateQuotation(
            @RequestParam String sourceQuotationId,
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody(required = false) Map<String, Object> request) {
        requireAgrees(request, "sourceQuotationId", sourceQuotationId);
        CrmOwner owner = ownerResolver.resolve();
        io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation derived =
                commandService.renegotiateQuotationResource(owner,
                        metadata(idempotencyKey, correlationId),
                        new RenegotiateQuotationCommand(sourceQuotationId));
        log.info("重议派生新草稿：source={}，derived={}，owner={}", sourceQuotationId,
                derived.getQuotationId(), owner.getOwner());
        return created("/api/crm/quotations/" + derived.getQuotationId(),
                currentQuotationView(derived));
    }

    /**
     * 点查报价。
     *
     * @param quotationId 报价标识
     * @param plan DATA 访问计划
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_QUOTATION_READ)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_READ)
    @GetMapping("/api/crm/quotations/{quotationId}")
    public ResponseEntity<Map<String, Object>> getQuotation(@PathVariable String quotationId,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation quotation =
                queryService.findQuotation(ownerResolver.resolve(), plan, quotationId);
        java.util.Map<String, Object[]> facts = queryService.quotationVersionFacts(
                java.util.Collections.singletonList(quotation));
        return ResponseEntity.ok(quotationView(quotation,
                queryService.deriveDisplayState(quotation, facts.get(quotation.getQuotationId()))));
    }

    /**
     * 点查报价版本（含行明细）。
     *
     * @param quotationId 报价标识
     * @param version 版本号
     * @param plan DATA 访问计划
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_QUOTATION_READ)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_READ)
    @GetMapping("/api/crm/quotations/{quotationId}/versions/{version}")
    public ResponseEntity<Map<String, Object>> getQuotationVersion(
            @PathVariable String quotationId, @PathVariable int version,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationVersion v =
                queryService.findQuotationVersion(ownerResolver.resolve(), plan, quotationId,
                        version);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("quotationId", v.getQuotationId());
        body.put("version", v.getVersion());
        body.put("state", v.getState().getCode());
        body.put("settlementCurrency", v.getSettlementCurrency());
        body.put("validUntil", v.getValidUntil().toString());
        body.put("totalAmount", money(v.getTotalAmount()));
        body.put("issuedAt", v.getIssuedAt() == null ? null : v.getIssuedAt().toString());
        body.put("terminalAt", v.getTerminalAt() == null ? null : v.getTerminalAt().toString());
        List<Map<String, Object>> lineViews = new ArrayList<Map<String, Object>>();
        for (io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationLine line
                : v.getLines()) {
            Map<String, Object> lineView = new LinkedHashMap<String, Object>();
            lineView.put("quotationLineId", line.getQuotationLineId());
            lineView.put("offeringId", line.getOfferingId());
            lineView.put("offeringReference", line.getOfferingReference());
            lineView.put("quantity", line.getQuantity().toPlainString());
            lineView.put("unit", line.getUnit());
            lineView.put("unitPrice", money(line.getUnitPrice()));
            lineView.put("lineTotal", money(line.getLineTotal()));
            Map<String, Object> obligation = new LinkedHashMap<String, Object>();
            obligation.put("subjectReference",
                    line.getFulfillmentObligationTemplate().getSubjectReference());
            obligation.put("fulfillmentScope",
                    line.getFulfillmentObligationTemplate().getFulfillmentScope());
            obligation.put("requiredConsumerCapability",
                    line.getFulfillmentObligationTemplate().getRequiredConsumerCapability());
            lineView.put("obligation", obligation);
            lineViews.add(lineView);
        }
        body.put("lines", lineViews);
        return ResponseEntity.ok(body);
    }

    /**
     * 报价列表。
     *
     * @param state 状态过滤（可空）
     * @param keyword 单号/客户名（可空）
     * @param cursor 游标（可空）
     * @param limit 每页上限
     * @param pageNumber 页码（可空，默认 1）
     * @param pageSize 页码模式每页条数（可空，默认 20）
     * @param parameters 原始查询参数，验证分页参数是否出现及空值
     * @param plan DATA 访问计划
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_QUOTATION_LIST)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_READ)
    @GetMapping("/api/crm/quotations")
    public ResponseEntity<Map<String, Object>> listQuotations(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(name = "page", required = false) Integer pageNumber,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam Map<String, String> parameters,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        CrmPageRequest pageRequest = SimpleCrmServerPaginationHelper.request(
                pageNumber, pageSize, cursor, limit, parameters);
        CrmPage<io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation> page =
                queryService.listQuotations(ownerResolver.resolve(), plan,
                        new CrmListCriteria(state, null, null, null, keyword), cursor,
                        SimpleCrmServerPaginationHelper.limit(limit, pageRequest), pageRequest);
        // 终态（撤回/取代）真相在版本行 state：批量取事实后逐行派生展示态
        java.util.Map<String, Object[]> versionFacts =
                queryService.quotationVersionFacts(page.getItems());
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation quotation
                : page.getItems()) {
            items.add(quotationView(quotation, queryService.deriveDisplayState(quotation,
                    versionFacts.get(quotation.getQuotationId()))));
        }
        body.put("items", items);
        SimpleCrmServerPaginationHelper.metadata(body, page);
        return ResponseEntity.ok(body);
    }

    /**
     * 编辑报价草稿（整单替换，版本号不变）。
     *
     * @param quotationId 报价标识
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_QUOTATION_EDIT)
    @PutMapping("/api/crm/quotations/{quotationId}")
    public ResponseEntity<Map<String, Object>> editQuotation(
            @PathVariable String quotationId,
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        CrmOwner owner = ownerResolver.resolve();
        List<QuotationLineCommand> lines = lineCommands(request);
        io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationDraft edited =
                commandService.editQuotationDraft(owner, metadata(idempotencyKey, correlationId),
                        new EditQuotationCommand(quotationId,
                                OffsetDateTime.parse(requiredText(request, "validUntil")).toInstant(),
                                lines));
        return ResponseEntity.ok(quotationView(edited.getQuotation(), "draft"));
    }

    /**
     * 修改报价（PATCH 动作分派入口说明）。
     *
     * <p>按查询参数路由到独立方法以挂接逐动作权限码：?state=issued → 签发、
     * ?state=confirmed → 确认成交、?state=withdrawn → 撤回、?owner=目标 → 转移。
     * 请求体为业务载荷（state 字段可省略，携带时必须与查询参数一致）。</p>
     *
     * @author surezzzzzz
     */

    /**
     * 签发报价。
     *
     * @param quotationId 报价标识
     * @param state 动作判别值（issued）
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体（quotationVersion/expectedAggregateVersion 必填）
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_QUOTATION_ISSUE)
    @PatchMapping(value = "/api/crm/quotations/{quotationId}", params = {"state=issued", "!owner"})
    public ResponseEntity<Map<String, Object>> issueQuotation(
            @PathVariable String quotationId,
            @RequestParam String state,
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        requireAgrees(request, "state", state);
        CrmOwner owner = ownerResolver.resolve();
        io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationIssuance
                issuance = commandService.issueQuotation(owner,
                metadata(idempotencyKey, correlationId), new IssueQuotationCommand(quotationId,
                intOf(request, "quotationVersion"), longOf(request, "expectedAggregateVersion")));
        log.info("签发报价：quotationId={}，owner={}", quotationId, owner.getOwner());
        return ResponseEntity.ok(quotationView(issuance.getQuotation(),
                issuance.getQuotationVersion().getState().getCode()));
    }

    /**
     * 确认成交（同事务生成订单与履约项）。
     *
     * @param quotationId 报价标识
     * @param state 动作判别值（confirmed）
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体（quotationVersion/expectedAggregateVersion 必填）
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_QUOTATION_CONFIRM)
    @PatchMapping(value = "/api/crm/quotations/{quotationId}", params = {"state=confirmed", "!owner"})
    public ResponseEntity<Map<String, Object>> confirmQuotation(
            @PathVariable String quotationId,
            @RequestParam String state,
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        requireAgrees(request, "state", state);
        CrmOwner owner = ownerResolver.resolve();
        io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationConfirmation
                confirmation = commandService.confirmQuotation(owner,
                metadata(idempotencyKey, correlationId), new ConfirmQuotationCommand(quotationId,
                intOf(request, "quotationVersion"), longOf(request, "expectedAggregateVersion")));
        log.info("确认成交：quotationId={}，owner={}", quotationId, owner.getOwner());
        return ResponseEntity.ok(confirmationView(confirmation));
    }

    /**
     * 撤回报价（终态）。
     *
     * @param quotationId 报价标识
     * @param state 动作判别值（withdrawn）
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体（state 可携带，须一致）
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_QUOTATION_WITHDRAW)
    @PatchMapping(value = "/api/crm/quotations/{quotationId}", params = {"state=withdrawn", "!owner"})
    public ResponseEntity<Map<String, Object>> withdrawQuotation(
            @PathVariable String quotationId,
            @RequestParam String state,
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        requireAgrees(request, "state", state);
        CrmOwner owner = ownerResolver.resolve();
        io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationWithdrawal
                withdrawal = commandService.withdrawQuotation(owner,
                metadata(idempotencyKey, correlationId), new WithdrawQuotationCommand(quotationId));
        log.info("撤回报价：quotationId={}，owner={}", quotationId, owner.getOwner());
        return ResponseEntity.ok(quotationView(withdrawal.getQuotation(),
                withdrawal.getQuotationVersion().getState().getCode()));
    }

    /**
     * 转移报价归属（治理动作）。
     *
     * @param quotationId 报价标识
     * @param owner 目标归属（查询参数承载）
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体（owner 可携带，须一致）
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_QUOTATION_TRANSFER)
    @PatchMapping(value = "/api/crm/quotations/{quotationId}", params = {"owner", "!state"})
    public ResponseEntity<Map<String, Object>> transferQuotation(
            @PathVariable String quotationId,
            @RequestParam String owner,
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        requireAgrees(request, "owner", owner);
        CrmOwner actor = ownerResolver.resolve();
        SimpleCrmServerQuotationTransferResult transferred =
                commandService.transferQuotationResult(actor,
                metadata(idempotencyKey, correlationId), new TransferQuotationCommand(quotationId,
                owner));
        log.info("转移报价：quotationId={}，to={}，actor={}", quotationId, owner, actor.getOwner());
        return ResponseEntity.ok(quotationView(transferred.getQuotation(), transferred.getDisplayState()));
    }

    /**
     * 拒绝报价状态与归属同时作为 PATCH 分派参数，避免动作权限路由歧义。
     *
     * @param quotationId 报价标识
     * @return 400
     */
    @PatchMapping(value = "/api/crm/quotations/{quotationId}", params = {"state", "owner"})
    public ResponseEntity<Map<String, Object>> rejectMixedPatch(@PathVariable String quotationId) {
        throw io.github.surezzzzzz.sdk.crm.server.core.error.CrmException.validation("state/owner");
    }

    /**
     * 缺分派判别参数的兜底：安全 404。
     *
     * @param quotationId 报价标识
     * @return 404
     */
    @PatchMapping("/api/crm/quotations/{quotationId}")
    public ResponseEntity<Map<String, Object>> patchQuotationMissingDiscriminator(
            @PathVariable String quotationId) {
        throw new io.github.surezzzzzz.sdk.crm.server.core.error.CrmException(
                io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                "quotation");
    }

    // ===== 订单 =====

    /**
     * 点查订单（含行明细与履约义务）。
     *
     * @param orderId 订单标识
     * @param plan DATA 访问计划
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_ORDER_READ)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_READ)
    @GetMapping("/api/crm/orders/{orderId}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable String orderId,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order order =
                queryService.findOrder(ownerResolver.resolve(), plan, orderId);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("orderId", order.getOrderId());
        body.put("sourceQuotationId", order.getSourceQuotationId());
        body.put("sourceQuotationVersion", order.getSourceQuotationVersion());
        body.put("customerId", order.getCustomerId());
        body.put("settlementCurrency", order.getSettlementCurrency());
        body.put("totalAmount", money(order.getTotalAmount()));
        body.put("owner", order.getOwner());
        body.put("displayState", order.getDisplayState().getCode());
        List<Map<String, Object>> lineViews = new ArrayList<Map<String, Object>>();
        for (io.github.surezzzzzz.sdk.crm.server.core.domain.order.OrderLine line
                : order.getLines()) {
            Map<String, Object> lineView = new LinkedHashMap<String, Object>();
            lineView.put("orderLineId", line.getOrderLineId());
            lineView.put("sourceQuotationLineId", line.getSourceQuotationLineId());
            lineView.put("offeringReference", line.getOfferingReference());
            lineView.put("quantity", line.getQuantity().toPlainString());
            lineView.put("unit", line.getUnit());
            lineView.put("unitPrice", money(line.getUnitPrice()));
            lineView.put("lineTotal", money(line.getLineTotal()));
            Map<String, Object> obligation = new LinkedHashMap<String, Object>();
            obligation.put("subjectReference",
                    line.getFulfillmentObligationTemplate().getSubjectReference());
            obligation.put("fulfillmentScope",
                    line.getFulfillmentObligationTemplate().getFulfillmentScope());
            obligation.put("requiredConsumerCapability",
                    line.getFulfillmentObligationTemplate().getRequiredConsumerCapability());
            lineView.put("obligation", obligation);
            lineViews.add(lineView);
        }
        body.put("lines", lineViews);
        return ResponseEntity.ok(body);
    }

    /**
     * 订单列表。
     *
     * @param keyword 订单号/客户 ID 关键字（可空）
     * @param cursor 游标（可空）
     * @param limit 每页上限
     * @param pageNumber 页码（可空，默认 1）
     * @param pageSize 页码模式每页条数（可空，默认 20）
     * @param parameters 原始查询参数，验证分页参数是否出现及空值
     * @param plan DATA 访问计划
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_ORDER_LIST)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_READ)
    @GetMapping("/api/crm/orders")
    public ResponseEntity<Map<String, Object>> listOrders(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(name = "page", required = false) Integer pageNumber,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam Map<String, String> parameters,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        CrmPageRequest pageRequest = SimpleCrmServerPaginationHelper.request(
                pageNumber, pageSize, cursor, limit, parameters);
        CrmPage<io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order> page =
                queryService.listOrders(ownerResolver.resolve(), plan,
                        new CrmListCriteria(null, null, null, null, keyword),
                        cursor, SimpleCrmServerPaginationHelper.limit(limit, pageRequest), pageRequest);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (io.github.surezzzzzz.sdk.crm.server.core.domain.order.Order order
                : page.getItems()) {
            Map<String, Object> view = new LinkedHashMap<String, Object>();
            view.put("orderId", order.getOrderId());
            view.put("sourceQuotationId", order.getSourceQuotationId());
            view.put("customerId", order.getCustomerId());
            view.put("totalAmount", money(order.getTotalAmount()));
            view.put("displayState", order.getDisplayState().getCode());
            items.add(view);
        }
        body.put("items", items);
        SimpleCrmServerPaginationHelper.metadata(body, page);
        return ResponseEntity.ok(body);
    }

    // ===== 辅助 =====

    /** 创建重放回读当前事实，展示态不能固定为草稿。 */
    private Map<String, Object> currentQuotationView(
            io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation quotation) {
        Object[] versionFact = queryService.quotationVersionFacts(
                java.util.Collections.singletonList(quotation)).get(quotation.getQuotationId());
        return quotationView(quotation, queryService.deriveDisplayState(quotation, versionFact));
    }

    private Map<String, Object> quotationView(
            io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation quotation,
            String state) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("state", state);
        view.put("quotationId", quotation.getQuotationId());
        view.put("customerId", quotation.getCustomerId());
        view.put("opportunityId", quotation.getOpportunityId());
        view.put("owner", quotation.getOwner());
        view.put("aggregateVersion", quotation.getAggregateVersion());
        view.put("currentVersion", quotation.getCurrentVersion());
        view.put("currentConfirmableVersion", quotation.getCurrentConfirmableVersion());
        view.put("confirmedOrderId", quotation.getConfirmedOrderId());
        view.put("supersedesQuotationId", quotation.getSupersedesQuotationId());
        return view;
    }

    private Map<String, Object> confirmationView(
            io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.QuotationConfirmation c) {
        Map<String, Object> body = quotationView(c.getQuotation(), "confirmed");
        body.put("orderId", c.getOrder().getOrderId());
        List<String> fulfillmentIds = new ArrayList<String>();
        for (io.github.surezzzzzz.sdk.crm.server.core.domain.fulfillment.FulfillmentItem item
                : c.getFulfillmentItems()) {
            fulfillmentIds.add(item.getFulfillmentId());
        }
        body.put("fulfillmentItemIds", fulfillmentIds);
        return body;
    }

    private Map<String, Object> money(Money money) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("amount", money.getAmount().toPlainString());
        view.put("currency", money.getCurrency());
        return view;
    }

    private List<QuotationLineCommand> lineCommands(Map<String, Object> request) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawLines = (List<Map<String, Object>>) request.get("lines");
        if (rawLines == null || rawLines.isEmpty()) {
            throw io.github.surezzzzzz.sdk.crm.server.core.error.CrmException.validation("lines");
        }
        List<QuotationLineCommand> lines = new ArrayList<QuotationLineCommand>();
        for (Map<String, Object> rawLine : rawLines) {
            // 单价与响应侧 money 形态同构：嵌套 {amount, currency}（非平铺键）
            Map<String, Object> unitPrice = nestedMap(rawLine, "unitPrice");
            lines.add(new QuotationLineCommand(
                    requiredText(rawLine, "offeringId"),
                    new BigDecimal(requiredText(rawLine, "quantity")),
                    requiredText(rawLine, "unit"),
                    new Money(new BigDecimal(requiredText(unitPrice, "amount")),
                            requiredText(unitPrice, "currency")),
                    requiredText(rawLine, "subjectReference"),
                    requiredText(rawLine, "fulfillmentScope")));
        }
        return lines;
    }

    /**
     * 取嵌套对象字段（缺失或非对象按空映射处理，交给后续 requiredText 报具体缺失键）。
     *
     * @param source 上层对象
     * @param key 嵌套字段名
     * @return 嵌套映射；缺失时为空映射
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> nestedMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        return value instanceof Map ? (Map<String, Object>) value
                : new LinkedHashMap<String, Object>();
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

    private long longOf(Map<String, Object> request, String key) {
        Object value = request.get(key);
        return value == null ? 0L : Long.parseLong(String.valueOf(value));
    }

    private int intOf(Map<String, Object> request, String key) {
        Object value = request.get(key);
        return value == null ? 0 : Integer.parseInt(String.valueOf(value));
    }

    private ResponseEntity<Map<String, Object>> created(String location,
            Map<String, Object> body) {
        return ResponseEntity.created(URI.create(location)).body(body);
    }

}
