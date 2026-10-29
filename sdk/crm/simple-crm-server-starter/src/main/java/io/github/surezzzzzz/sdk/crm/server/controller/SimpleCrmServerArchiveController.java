package io.github.surezzzzzz.sdk.crm.server.controller;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.annotation.RequireApiPermission;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.DataPermissionOperation;
import io.github.surezzzzzz.sdk.auth.data.permission.core.model.DataAccessPlan;
import io.github.surezzzzzz.sdk.auth.data.permission.core.annotation.CurrentDataAccessPlan;
import io.github.surezzzzzz.sdk.crm.server.annotation.SimpleCrmServerComponent;
import io.github.surezzzzzz.sdk.crm.server.constant.SimpleCrmServerConstant;
import io.github.surezzzzzz.sdk.crm.server.core.command.ActivateCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateContactCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CreateOfferingCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.CrmCommandMetadata;
import io.github.surezzzzzz.sdk.crm.server.core.command.DeactivateCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.DisableOfferingCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.MergeCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.PublishOfferingCommand;
import io.github.surezzzzzz.sdk.crm.server.core.command.TransferCustomerCommand;
import io.github.surezzzzzz.sdk.crm.server.core.domain.commercial.CommercialCapabilityType;
import io.github.surezzzzzz.sdk.crm.server.core.domain.contact.Contact;
import io.github.surezzzzzz.sdk.crm.server.core.domain.customer.Customer;
import io.github.surezzzzzz.sdk.crm.server.core.domain.identity.CrmOwner;
import io.github.surezzzzzz.sdk.crm.server.core.domain.offering.Offering;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmListCriteria;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPage;
import io.github.surezzzzzz.sdk.crm.server.core.model.CrmPageRequest;
import io.github.surezzzzzz.sdk.crm.server.core.model.CustomerMergeResult;
import io.github.surezzzzzz.sdk.crm.server.core.model.CustomerTransferResult;
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

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 档案控制器：客户/联系人/目录（RESTful 资源名词 + PATCH 状态变更）。
 *
 * <p>状态变更统一走 PATCH：客户停用/复启/合并/转移按 body 字段区分；
 * 目录上架/下架同理。写请求必带幂等键。</p>
 *
 * @author surezzzzzz
 */
@Slf4j
@RestController
@SimpleCrmServerComponent
public class SimpleCrmServerArchiveController {

    private final SimpleCrmServerCommandService commandService;
    private final SimpleCrmServerQueryService queryService;
    private final SimpleCrmServerOwnerResolverBridge ownerResolver;

    /**
     * 创建档案控制器。
     *
     * @param commandService 命令服务
     * @param queryService 查询服务
     * @param ownerResolver owner 解析桥
     */
    public SimpleCrmServerArchiveController(SimpleCrmServerCommandService commandService, SimpleCrmServerQueryService queryService,
                                SimpleCrmServerOwnerResolverBridge ownerResolver) {
        this.commandService = commandService;
        this.queryService = queryService;
        this.ownerResolver = ownerResolver;
    }

    // ===== 客户 =====

    /**
     * 创建客户。
     *
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体
     * @return 201 + Location
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_CUSTOMER_CREATE)
    @PostMapping("/api/crm/customers")
    public ResponseEntity<Map<String, Object>> createCustomer(
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        CrmOwner owner = ownerResolver.resolve();
        String displayName = requiredText(request, "displayName");
        Customer customer = commandService.createCustomer(owner, metadata(idempotencyKey,
                correlationId), new CreateCustomerCommand(displayName));
        log.info("创建客户：customerId={}，owner={}", customer.getCustomerId(), owner.getOwner());
        return created("/api/crm/customers/" + customer.getCustomerId(),
                viewOf(customer.getCustomerId(), customer.getDisplayName(),
                        customer.getState().getCode(), customer.getOwner()));
    }

    /**
     * 点查客户。
     *
     * @param customerId 客户标识
     * @param plan DATA 访问计划
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_CUSTOMER_READ)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_READ)
    @GetMapping("/api/crm/customers/{customerId}")
    public ResponseEntity<Map<String, Object>> getCustomer(@PathVariable String customerId,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        Customer customer = queryService.findCustomer(ownerResolver.resolve(), plan, customerId);
        return ResponseEntity.ok(viewOf(customer.getCustomerId(), customer.getDisplayName(),
                customer.getState().getCode(), customer.getOwner()));
    }

    /**
     * 客户列表。
     *
     * @param state 状态过滤（可空）
     * @param keyword 名称/ID 关键字（可空）
     * @param cursor 游标（可空）
     * @param limit 每页上限
     * @param pageNumber 页码（可空，默认 1）
     * @param pageSize 页码模式每页条数（可空，默认 20）
     * @param parameters 原始查询参数，验证分页参数是否出现及空值
     * @param plan DATA 访问计划
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_CUSTOMER_LIST)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_READ)
    @GetMapping("/api/crm/customers")
    public ResponseEntity<Map<String, Object>> listCustomers(
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
        CrmPage<Customer> page = queryService.listCustomers(ownerResolver.resolve(), plan,
                new CrmListCriteria(state, null, null, null, keyword), cursor,
                SimpleCrmServerPaginationHelper.limit(limit, pageRequest), pageRequest);
        return ResponseEntity.ok(pageOf(page, item -> viewOf(item.getCustomerId(),
                item.getDisplayName(), item.getState().getCode(), item.getOwner())));
    }

    /**
     * 修改客户（PATCH 动作分派入口说明）。
     *
     * <p>状态变更按查询参数路由到独立方法以挂接逐动作权限码：?state=inactive → 停用、
     * ?state=active → 复启、?state=merged → 合并、?owner=目标 → 转移。请求体为业务载荷
     * （state 字段可省略，携带时必须与查询参数一致）。</p>
     *
     * @author surezzzzzz
     */

    /**
     * 停用客户。
     *
     * @param customerId 客户标识
     * @param state 动作判别值（inactive）
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体（state 可携带，须一致）
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_CUSTOMER_DISABLE)
    @PatchMapping(value = "/api/crm/customers/{customerId}", params = {"state=inactive", "!owner"})
    public ResponseEntity<Map<String, Object>> deactivateCustomer(
            @PathVariable String customerId,
            @RequestParam String state,
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        requireAgrees(request, "state", state);
        CrmOwner owner = ownerResolver.resolve();
        Customer deactivated = commandService.deactivateCustomer(owner,
                metadata(idempotencyKey, correlationId), new DeactivateCustomerCommand(customerId));
        log.info("停用客户：customerId={}，owner={}", deactivated.getCustomerId(), owner.getOwner());
        return ResponseEntity.ok(viewOf(deactivated.getCustomerId(),
                deactivated.getDisplayName(), deactivated.getState().getCode(),
                deactivated.getOwner()));
    }

    /**
     * 复启客户。
     *
     * @param customerId 客户标识
     * @param state 动作判别值（active）
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体（state 可携带，须一致）
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_CUSTOMER_ACTIVATE)
    @PatchMapping(value = "/api/crm/customers/{customerId}", params = {"state=active", "!owner"})
    public ResponseEntity<Map<String, Object>> activateCustomer(
            @PathVariable String customerId,
            @RequestParam String state,
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        requireAgrees(request, "state", state);
        CrmOwner owner = ownerResolver.resolve();
        Customer activated = commandService.activateCustomer(owner,
                metadata(idempotencyKey, correlationId), new ActivateCustomerCommand(customerId));
        log.info("复启客户：customerId={}，owner={}", activated.getCustomerId(), owner.getOwner());
        return ResponseEntity.ok(viewOf(activated.getCustomerId(),
                activated.getDisplayName(), activated.getState().getCode(), activated.getOwner()));
    }

    /**
     * 合并客户（source 并入 target，治理动作）。
     *
     * @param customerId 源客户标识
     * @param state 动作判别值（merged）
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体（mergeTargetId 必填）
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_CUSTOMER_MERGE)
    @PatchMapping(value = "/api/crm/customers/{customerId}", params = {"state=merged", "!owner"})
    public ResponseEntity<Map<String, Object>> mergeCustomer(
            @PathVariable String customerId,
            @RequestParam String state,
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        requireAgrees(request, "state", state);
        CrmOwner owner = ownerResolver.resolve();
        CustomerMergeResult merged = commandService.mergeCustomer(owner,
                metadata(idempotencyKey, correlationId),
                new MergeCustomerCommand(customerId, requiredText(request, "mergeTargetId")));
        log.info("合并客户：sourceCustomerId={}，owner={}", customerId, owner.getOwner());
        return ResponseEntity.ok(mergeViewOf(merged));
    }

    /**
     * 转移客户归属（治理动作）。
     *
     * @param customerId 客户标识
     * @param owner 目标归属（查询参数承载）
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体（owner 可携带，须一致）
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_CUSTOMER_TRANSFER)
    @PatchMapping(value = "/api/crm/customers/{customerId}", params = {"owner", "!state"})
    public ResponseEntity<Map<String, Object>> transferCustomer(
            @PathVariable String customerId,
            @RequestParam String owner,
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        requireAgrees(request, "owner", owner);
        CrmOwner actor = ownerResolver.resolve();
        CustomerTransferResult transferred = commandService.transferCustomer(actor,
                metadata(idempotencyKey, correlationId), new TransferCustomerCommand(customerId, owner));
        log.info("转移客户：customerId={}，to={}，actor={}", customerId, owner, actor.getOwner());
        return ResponseEntity.ok(transferViewOf(transferred));
    }

    /**
     * 拒绝客户状态与归属同时作为 PATCH 分派参数，避免动作权限路由歧义。
     *
     * @param customerId 客户标识
     * @return 400
     */
    @PatchMapping(value = "/api/crm/customers/{customerId}", params = {"state", "owner"})
    public ResponseEntity<Map<String, Object>> rejectCustomerMixedPatch(@PathVariable String customerId) {
        throw io.github.surezzzzzz.sdk.crm.server.core.error.CrmException.validation("state/owner");
    }

    /**
     * 缺分派判别参数的兜底：安全 404（与点查不可见同语义，不泄露动作清单）。
     *
     * @param customerId 客户标识
     * @return 404
     */
    @PatchMapping("/api/crm/customers/{customerId}")
    public ResponseEntity<Map<String, Object>> patchCustomerMissingDiscriminator(
            @PathVariable String customerId) {
        throw new io.github.surezzzzzz.sdk.crm.server.core.error.CrmException(
                io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                "customer");
    }

    /**
     * 拒绝目录项混用分派判别参数（目录动作只有 state，owner 不得出现）。
     *
     * @param offeringId 目录标识
     * @return 400
     */
    @PatchMapping(value = "/api/crm/offerings/{offeringId}", params = {"state", "owner"})
    public ResponseEntity<Map<String, Object>> rejectOfferingMixedPatch(
            @PathVariable String offeringId) {
        throw io.github.surezzzzzz.sdk.crm.server.core.error.CrmException.validation("state/owner");
    }

    /**
     * 缺分派判别参数的兜底：安全 404。
     *
     * @param offeringId 目录标识
     * @return 404
     */
    @PatchMapping("/api/crm/offerings/{offeringId}")
    public ResponseEntity<Map<String, Object>> patchOfferingMissingDiscriminator(
            @PathVariable String offeringId) {
        throw new io.github.surezzzzzz.sdk.crm.server.core.error.CrmException(
                io.github.surezzzzzz.sdk.crm.server.core.error.CrmErrorCode.NOT_FOUND_OR_NOT_ACCESSIBLE,
                "offering");
    }

    // ===== 联系人 =====

    /**
     * 创建联系人。
     *
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体
     * @return 201 + Location
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_CONTACT_CREATE)
    @PostMapping("/api/crm/contacts")
    public ResponseEntity<Map<String, Object>> createContact(
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        CrmOwner owner = ownerResolver.resolve();
        Contact contact = commandService.createContact(owner, metadata(idempotencyKey,
                correlationId), new CreateContactCommand(requiredText(request, "customerId"),
                requiredText(request, "displayName"), text(request, "title")));
        return created("/api/crm/contacts/" + contact.getContactId(),
                contactView(contact));
    }

    /**
     * 点查联系人。
     *
     * @param contactId 联系人标识
     * @param plan DATA 访问计划
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_CONTACT_READ)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_READ)
    @GetMapping("/api/crm/contacts/{contactId}")
    public ResponseEntity<Map<String, Object>> getContact(@PathVariable String contactId,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        Contact contact = queryService.findContact(ownerResolver.resolve(), plan, contactId);
        return ResponseEntity.ok(contactView(contact));
    }

    /**
     * 联系人列表。
     *
     * @param customerId 客户标识
     * @param cursor 游标（可空）
     * @param limit 每页上限
     * @param pageNumber 页码（可空，默认 1）
     * @param pageSize 页码模式每页条数（可空，默认 20）
     * @param parameters 原始查询参数，验证分页参数是否出现及空值
     * @param plan DATA 访问计划
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_CONTACT_LIST)
    @DataPermissionOperation(resource = SimpleCrmServerConstant.DATA_RESOURCE_CUSTOMER,
            action = SimpleCrmServerConstant.DATA_ACTION_READ)
    @GetMapping("/api/crm/contacts")
    public ResponseEntity<Map<String, Object>> listContacts(
            @RequestParam String customerId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(name = "page", required = false) Integer pageNumber,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam Map<String, String> parameters,
            @CurrentDataAccessPlan DataAccessPlan plan) {
        CrmPageRequest pageRequest = SimpleCrmServerPaginationHelper.request(
                pageNumber, pageSize, cursor, limit, parameters);
        CrmPage<Contact> page = queryService.listContacts(ownerResolver.resolve(), plan,
                new CrmListCriteria(null, customerId, null, null), cursor,
                SimpleCrmServerPaginationHelper.limit(limit, pageRequest), pageRequest);
        return ResponseEntity.ok(pageOf(page, this::contactView));
    }

    // ===== 目录 =====

    /**
     * 创建目录项。
     *
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体
     * @return 201 + Location
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_OFFERING_CREATE)
    @PostMapping("/api/crm/offerings")
    public ResponseEntity<Map<String, Object>> createOffering(
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        CrmOwner owner = ownerResolver.resolve();
        Offering offering = commandService.createOffering(owner, metadata(idempotencyKey,
                correlationId), new CreateOfferingCommand(requiredText(request, "offeringReference"),
                requiredText(request, "displayName"),
                CommercialCapabilityType.fromCode(requiredText(request, "capabilityType")),
                requiredText(request, "requiredConsumerCapability")));
        return created("/api/crm/offerings/" + offering.getOfferingId(),
                offeringView(offering));
    }

    /**
     * 点查目录项。
     *
     * @param offeringId 目录标识
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_OFFERING_READ)
    @GetMapping("/api/crm/offerings/{offeringId}")
    public ResponseEntity<Map<String, Object>> getOffering(@PathVariable String offeringId) {
        Offering offering = queryService.findOffering(ownerResolver.resolve(), offeringId);
        return ResponseEntity.ok(offeringView(offering));
    }

    /**
     * 目录列表（读无 DATA，全体持码者可见）。
     *
     * @param state 状态过滤（可空）
     * @param keyword 名称/业务键（可空）
     * @param cursor 游标（可空）
     * @param limit 每页上限
     * @param pageNumber 页码（可空，默认 1）
     * @param pageSize 页码模式每页条数（可空，默认 20）
     * @param parameters 原始查询参数，验证分页参数是否出现及空值
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_OFFERING_LIST)
    @GetMapping("/api/crm/offerings")
    public ResponseEntity<Map<String, Object>> listOfferings(
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer limit,
            @RequestParam(name = "page", required = false) Integer pageNumber,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam Map<String, String> parameters) {
        CrmPageRequest pageRequest = SimpleCrmServerPaginationHelper.request(
                pageNumber, pageSize, cursor, limit, parameters);
        CrmPage<Offering> page = queryService.listOfferings(ownerResolver.resolve(),
                new CrmListCriteria(state, null, null, null, keyword), cursor,
                SimpleCrmServerPaginationHelper.limit(limit, pageRequest), pageRequest);
        return ResponseEntity.ok(pageOf(page, this::offeringView));
    }

    /**
     * 修改目录项（PATCH 动作分派：?state=published → 上架、?state=disabled → 下架；
     * 逐动作权限码，请求体 state 可携带但须与查询参数一致）。
     *
     * @author surezzzzzz
     */

    /**
     * 上架目录项。
     *
     * @param offeringId 目录标识
     * @param state 动作判别值（published）
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体（state 可携带，须一致）
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_OFFERING_PUBLISH)
    @PatchMapping(value = "/api/crm/offerings/{offeringId}",
            params = {"state=published", "!owner"})
    public ResponseEntity<Map<String, Object>> publishOffering(
            @PathVariable String offeringId,
            @RequestParam String state,
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        requireAgrees(request, "state", state);
        CrmOwner owner = ownerResolver.resolve();
        Offering published = commandService.publishOffering(owner,
                metadata(idempotencyKey, correlationId), new PublishOfferingCommand(offeringId));
        log.info("上架目录项：offeringId={}，owner={}", published.getOfferingId(), owner.getOwner());
        return ResponseEntity.ok(offeringView(published));
    }

    /**
     * 下架目录项。
     *
     * @param offeringId 目录标识
     * @param state 动作判别值（disabled）
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空）
     * @param request 请求体（state 可携带，须一致）
     * @return 200
     */
    @RequireApiPermission(SimpleCrmServerConstant.API_CODE_OFFERING_DISABLE)
    @PatchMapping(value = "/api/crm/offerings/{offeringId}",
            params = {"state=disabled", "!owner"})
    public ResponseEntity<Map<String, Object>> disableOffering(
            @PathVariable String offeringId,
            @RequestParam String state,
            @RequestHeader(SimpleCrmServerConstant.HEADER_IDEMPOTENCY_KEY) String idempotencyKey,
            @RequestHeader(value = SimpleCrmServerConstant.HEADER_CORRELATION_ID, required = false)
                    String correlationId,
            @RequestBody Map<String, Object> request) {
        requireAgrees(request, "state", state);
        CrmOwner owner = ownerResolver.resolve();
        Offering disabled = commandService.disableOffering(owner,
                metadata(idempotencyKey, correlationId), new DisableOfferingCommand(offeringId));
        log.info("下架目录项：offeringId={}，owner={}", disabled.getOfferingId(), owner.getOwner());
        return ResponseEntity.ok(offeringView(disabled));
    }

    private Map<String, Object> offeringView(Offering offering) {
        Map<String, Object> body = viewOf(offering.getOfferingId(), offering.getDisplayName(),
                offering.getState().getCode(), offering.getOwner());
        body.put("offeringReference", offering.getOfferingReference());
        body.put("capabilityType", offering.getCapabilityType().getCode());
        body.put("requiredConsumerCapability", offering.getRequiredConsumerCapability());
        return body;
    }

    // ===== 辅助 =====

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
    private Map<String, Object> viewOf(String id, String displayName, String state, String owner) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("id", id);
        view.put("displayName", displayName);
        view.put("state", state);
        view.put("owner", owner);
        return view;
    }

    private Map<String, Object> mergeViewOf(CustomerMergeResult result) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("source", viewOf(result.getMergedSourceCustomer().getCustomerId(),
                result.getMergedSourceCustomer().getDisplayName(),
                result.getMergedSourceCustomer().getState().getCode(),
                result.getMergedSourceCustomer().getOwner()));
        view.put("target", viewOf(result.getTargetCustomer().getCustomerId(),
                result.getTargetCustomer().getDisplayName(),
                result.getTargetCustomer().getState().getCode(),
                result.getTargetCustomer().getOwner()));
        List<Map<String, Object>> contacts = new ArrayList<Map<String, Object>>();
        for (Contact contact : result.getRelocatedContacts()) {
            contacts.add(contactView(contact));
        }
        view.put("relocatedContacts", contacts);
        return view;
    }

    private Map<String, Object> transferViewOf(CustomerTransferResult result) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("customer", viewOf(result.getCustomer().getCustomerId(),
                result.getCustomer().getDisplayName(),
                result.getCustomer().getState().getCode(),
                result.getCustomer().getOwner()));
        List<Map<String, Object>> quotations = new ArrayList<Map<String, Object>>();
        for (io.github.surezzzzzz.sdk.crm.server.core.domain.quotation.Quotation q
                : result.getTransferredQuotations()) {
            Map<String, Object> qv = new LinkedHashMap<String, Object>();
            qv.put("quotationId", q.getQuotationId());
            qv.put("owner", q.getOwner());
            quotations.add(qv);
        }
        view.put("transferredQuotations", quotations);
        return view;
    }

    private Map<String, Object> contactView(Contact contact) {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("id", contact.getContactId());
        view.put("displayName", contact.getDisplayName());
        view.put("state", contact.getState().getCode());
        view.put("customerId", contact.getCustomerId());
        view.put("title", contact.getTitle());
        return view;
    }

    private <T> Map<String, Object> pageOf(CrmPage<T> page,
            java.util.function.Function<T, Map<String, Object>> mapper) {
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        for (T item : page.getItems()) {
            items.add(mapper.apply(item));
        }
        body.put("items", items);
        SimpleCrmServerPaginationHelper.metadata(body, page);
        return body;
    }

    private ResponseEntity<Map<String, Object>> created(String location,
            Map<String, Object> body) {
        return ResponseEntity.created(URI.create(location)).body(body);
    }

    private CrmCommandMetadata metadata(String idempotencyKey, String correlationId) {
        return new CrmCommandMetadata(correlationId == null || correlationId.trim().isEmpty()
                ? UUID.randomUUID().toString() : correlationId, idempotencyKey);
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

}
