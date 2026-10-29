package io.github.surezzzzzz.sdk.crm.client.feign;

import io.github.surezzzzzz.sdk.auth.aksk.feign.redis.client.annotation.AkskClientFeignClient;
import io.github.surezzzzzz.sdk.crm.client.feign.model.CrmPageResponse;
import io.github.surezzzzzz.sdk.crm.client.feign.model.CrmCompleteListResponse;
import io.github.surezzzzzz.sdk.crm.client.feign.model.CrmResourceResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * CRM 契约的 Feign 接口（认证由 aksk 底座元注解自动挂接）。
 *
 * <p>标注底座 {@code @AkskClientFeignClient}：宿主启用 {@code @EnableFeignClients} 扫描本包后，
 * Authorization 头由底座令牌链自动注入。目标地址取
 * {@code io.github.surezzzzzz.sdk.crm.client.base-url}（仅 origin），API 基路径 {@code /api/crm}。</p>
 *
 * <p>请求体为 Map：可选字段为 null 时请勿放入 Map（与契约省略语义一致）；
 * 响应为 wire Map 形态（服务端字段面），消费方经 client-core 校验器构造视图。</p>
 *
 * <p>列表方法使用机器 cursor/limit 契约，不发送管理台页码参数；消费者方法返回完整列表。
 * 分页封装严格拒绝未知字段与非法游标，行内资源不自动执行字段白名单校验。</p>
 *
 * @author surezzzzzz
 */
@AkskClientFeignClient(name = "crm", url = "${io.github.surezzzzzz.sdk.crm.client.base-url}",
        path = "/api/crm")
public interface CrmFeignClient {

    // ===== 客户 =====

    /**
     * 创建客户（幂等）。
     *
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID（可空省略）
     * @param body {displayName}
     * @return 客户
     */
    @PostMapping("/customers")
    CrmResourceResponse createCustomer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody Map<String, Object> body);

    /**
     * 点查客户。
     *
     * @param customerId 客户标识
     * @return 客户
     */
    @GetMapping("/customers/{customerId}")
    CrmResourceResponse getCustomer(@PathVariable("customerId") String customerId);

    /**
     * 客户列表。
     *
     * @param state 状态过滤（可空）
     * @param cursor 游标（可空）
     * @param limit 每页上限
     * @return 列表页
     */
    @GetMapping("/customers")
    CrmPageResponse listCustomers(@RequestParam(value = "state", required = false) String state,
                                  @RequestParam(value = "cursor", required = false) String cursor,
                                  @RequestParam("limit") int limit);

    /**
     * 客户列表（带名称或 ID 关键字过滤）。
     *
     * @param state 状态过滤
     * @param keyword 名称或 ID 包含匹配
     * @param cursor 游标
     * @param limit 每页上限
     * @return 列表页
     */
    @GetMapping("/customers")
    CrmPageResponse listCustomers(@RequestParam(value = "state", required = false) String state,
                                  @RequestParam(value = "keyword", required = false) String keyword,
                                  @RequestParam(value = "cursor", required = false) String cursor,
                                  @RequestParam("limit") int limit);

    // ===== 联系人 =====

    /**
     * 创建联系人。
     *
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID
     * @param body {customerId, displayName, title?}
     * @return 联系人
     */
    @PostMapping("/contacts")
    CrmResourceResponse createContact(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody Map<String, Object> body);

    /**
     * 联系人列表（按客户）。
     *
     * @param customerId 客户标识
     * @param cursor 游标
     * @param limit 每页上限
     * @return 列表页
     */
    @GetMapping("/contacts")
    CrmPageResponse listContacts(@RequestParam("customerId") String customerId,
                                 @RequestParam(value = "cursor", required = false) String cursor,
                                 @RequestParam("limit") int limit);

    // ===== 目录 =====

    /**
     * 创建目录项（DRAFT 录入）。
     *
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID
     * @param body {offeringReference, displayName, capabilityType, requiredConsumerCapability}
     * @return 目录
     */
    @PostMapping("/offerings")
    CrmResourceResponse createOffering(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody Map<String, Object> body);

    /**
     * 目录列表。
     *
     * @param state 状态过滤
     * @param cursor 游标
     * @param limit 每页上限
     * @return 列表页
     */
    @GetMapping("/offerings")
    CrmPageResponse listOfferings(@RequestParam(value = "state", required = false) String state,
                                  @RequestParam(value = "cursor", required = false) String cursor,
                                  @RequestParam("limit") int limit);

    /**
     * 目录列表（带名称、业务引用或 ID 关键字过滤）。
     *
     * @param state 状态过滤
     * @param keyword 名称、业务引用或 ID 包含匹配
     * @param cursor 游标
     * @param limit 每页上限
     * @return 列表页
     */
    @GetMapping("/offerings")
    CrmPageResponse listOfferings(@RequestParam(value = "state", required = false) String state,
                                  @RequestParam(value = "keyword", required = false) String keyword,
                                  @RequestParam(value = "cursor", required = false) String cursor,
                                  @RequestParam("limit") int limit);

    // ===== 商机 =====

    /**
     * 创建商机（幂等）。body 必须含 customerId、title，可选 primaryOfferingId、contactId、
     * expectedCloseDate、estimatedAmount、currency。
     *
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID
     * @param body 商机创建体
     * @return 商机
     */
    @PostMapping("/opportunities")
    CrmResourceResponse createOpportunity(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody Map<String, Object> body);

    /**
     * 点查商机。
     *
     * @param opportunityId 商机标识
     * @return 商机
     */
    @GetMapping("/opportunities/{opportunityId}")
    CrmResourceResponse getOpportunity(@PathVariable("opportunityId") String opportunityId);

    /**
     * 商机列表（机器游标模式）。
     *
     * @param stage 阶段过滤
     * @param customerId 客户过滤
     * @param keyword 标题关键字
     * @param cursor 不透明游标
     * @param limit 每页上限
     * @return 列表页
     */
    @GetMapping("/opportunities")
    CrmPageResponse listOpportunities(@RequestParam(value = "stage", required = false) String stage,
                                      @RequestParam(value = "customerId", required = false) String customerId,
                                      @RequestParam(value = "keyword", required = false) String keyword,
                                      @RequestParam(value = "cursor", required = false) String cursor,
                                      @RequestParam("limit") int limit);

    /**
     * 推进商机阶段；won 仅允许由报价确认事务派生。
     *
     * @param opportunityId 商机标识
     * @param stage 目标阶段
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID
     * @param body 可选 lostReason，stage 与查询参数必须一致
     * @return 商机
     */
    @PatchMapping(value = "/opportunities/{opportunityId}", params = "stage")
    CrmResourceResponse patchOpportunityStage(
            @PathVariable("opportunityId") String opportunityId,
            @RequestParam("stage") String stage,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody(required = false) Map<String, Object> body);

    /**
     * 转移商机归属；owner 与查询参数必须一致。
     *
     * @param opportunityId 商机标识
     * @param owner 新归属主体
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID
     * @param body 可选 owner 镜像字段
     * @return 商机
     */
    @PatchMapping(value = "/opportunities/{opportunityId}", params = "owner")
    CrmResourceResponse patchOpportunityOwner(
            @PathVariable("opportunityId") String opportunityId,
            @RequestParam("owner") String owner,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody(required = false) Map<String, Object> body);

    // ===== 报价 =====

    /**
     * 创建报价草稿。
     *
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID
     * @param body {customerId, opportunityId?, validUntil, lines[]}
     * @return 报价
     */
    @PostMapping("/quotations")
    CrmResourceResponse createQuotation(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody Map<String, Object> body);

    /**
     * 编辑报价草稿（整单替换）。
     *
     * @param quotationId 报价标识
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID
     * @param body {validUntil, lines[]}
     * @return 报价
     */
    @PutMapping("/quotations/{quotationId}")
    CrmResourceResponse editQuotation(@PathVariable("quotationId") String quotationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody Map<String, Object> body);

    /**
     * 点查报价。
     *
     * @param quotationId 报价标识
     * @return 报价
     */
    @GetMapping("/quotations/{quotationId}")
    CrmResourceResponse getQuotation(@PathVariable("quotationId") String quotationId);

    /**
     * 报价列表。
     *
     * @param state 状态过滤
     * @param cursor 游标
     * @param limit 每页上限
     * @return 列表页
     */
    @GetMapping("/quotations")
    CrmPageResponse listQuotations(@RequestParam(value = "state", required = false) String state,
                                   @RequestParam(value = "cursor", required = false) String cursor,
                                   @RequestParam("limit") int limit);

    /**
     * 报价列表（带单号或客户关键字过滤）。
     *
     * @param state 状态过滤
     * @param keyword 单号或客户显示名包含匹配
     * @param cursor 游标
     * @param limit 每页上限
     * @return 列表页
     */
    @GetMapping("/quotations")
    CrmPageResponse listQuotations(@RequestParam(value = "state", required = false) String state,
                                   @RequestParam(value = "keyword", required = false) String keyword,
                                   @RequestParam(value = "cursor", required = false) String cursor,
                                   @RequestParam("limit") int limit);

    // ===== 订单与履约 =====

    /**
     * 订单列表。
     *
     * @param cursor 游标
     * @param limit 每页上限
     * @return 列表页
     */
    @GetMapping("/orders")
    CrmPageResponse listOrders(@RequestParam(value = "cursor", required = false) String cursor,
                               @RequestParam("limit") int limit);

    /**
     * 订单列表（带订单号或客户 ID 关键字过滤）。
     *
     * @param keyword 订单号或客户 ID 包含匹配
     * @param cursor 游标
     * @param limit 每页上限
     * @return 列表页
     */
    @GetMapping("/orders")
    CrmPageResponse listOrders(@RequestParam(value = "keyword", required = false) String keyword,
                               @RequestParam(value = "cursor", required = false) String cursor,
                               @RequestParam("limit") int limit);

    /**
     * 履约项列表（消费者拉取待执行项）。
     *
     * @param state 状态过滤
     * @param orderId 订单过滤（人员面）
     * @param cursor 游标
     * @param limit 每页上限
     * @return 列表页
     */
    @GetMapping("/fulfillment-items")
    CrmPageResponse listFulfillmentItems(@RequestParam(value = "state", required = false) String state,
                                         @RequestParam(value = "orderId", required = false) String orderId,
                                         @RequestParam(value = "cursor", required = false) String cursor,
                                         @RequestParam("limit") int limit);

    /**
     * 点查联系人。
     *
     * @param contactId 联系人标识
     * @return 联系人档案
     */
    @GetMapping("/contacts/{contactId}")
    CrmResourceResponse getContact(@PathVariable("contactId") String contactId);

    /**
     * 点查商品，包含稳定业务引用与能力字段。
     *
     * @param offeringId 商品标识
     * @return 商品
     */
    @GetMapping("/offerings/{offeringId}")
    CrmResourceResponse getOffering(@PathVariable("offeringId") String offeringId);

    /**
     * 点查报价版本，含冻结行项。
     *
     * @param quotationId 报价标识
     * @param version 版本号
     * @return 报价版本
     */
    @GetMapping("/quotations/{quotationId}/versions/{version}")
    CrmResourceResponse getQuotationVersion(@PathVariable("quotationId") String quotationId,
            @PathVariable("version") int version);

    /**
     * 点查订单及行明细。
     *
     * @param orderId 订单标识
     * @return 订单
     */
    @GetMapping("/orders/{orderId}")
    CrmResourceResponse getOrder(@PathVariable("orderId") String orderId);

    /**
     * 点查履约项，机器主体按消费者派发归属可见。
     *
     * @param fulfillmentId 履约项标识
     * @return 履约项
     */
    @GetMapping("/fulfillment-items/{fulfillmentId}")
    CrmResourceResponse getFulfillmentItem(@PathVariable("fulfillmentId") String fulfillmentId);

    /**
     * 查询消费者完整列表，不发送分页参数。
     *
     * @return 完整消费者列表，游标为空
     */
    @GetMapping("/fulfillment-consumers")
    CrmCompleteListResponse listFulfillmentConsumers();

    /**
     * 登记或更新履约消费者。
     *
     * @param idempotencyKey 幂等键
     * @param command consumerId、capability、protocolVersion 与 enabled
     * @return 消费者档案
     */
    @PostMapping("/fulfillment-consumers")
    CrmResourceResponse upsertFulfillmentConsumer(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody Map<String, Object> command);

    /**
     * 登记或更新履约消费者并传递操作关联标识。
     *
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID
     * @param command consumerId、capability、protocolVersion 与 enabled
     * @return 消费者档案
     */
    @PostMapping("/fulfillment-consumers")
    CrmResourceResponse upsertFulfillmentConsumer(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody Map<String, Object> command);


    /**
     * 修改客户：停用/复启返回档案，合并和转移返回嵌套治理结果。
     *
     * @param customerId 客户标识
     * @param idempotencyKey 幂等键
     * @param patch state=inactive/active/merged 或目标 owner；合并带 mergeTargetId
     * @param routing 由 {@link io.github.surezzzzzz.sdk.crm.client.CrmPatchRouting#of(Map)} 推导
     * @return 客户档案或治理结果
     */

    @PatchMapping("/customers/{customerId}")
    CrmResourceResponse patchCustomer(@PathVariable("customerId") String customerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody Map<String, Object> patch,
            @RequestParam Map<String, String> routing);

    /**
     * 修改客户并传递操作关联标识；保留无关联标识重载供旧版调用方使用。
     *
     * @param customerId 客户标识
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID
     * @param patch 状态或归属补丁
     * @param routing 分派查询参数
     * @return 客户档案或治理结果
     */
    @PatchMapping("/customers/{customerId}")
    CrmResourceResponse patchCustomer(@PathVariable("customerId") String customerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody Map<String, Object> patch,
            @RequestParam Map<String, String> routing);

    /**
     * 商品上下架。
     *
     * @param offeringId 商品标识
     * @param idempotencyKey 幂等键
     * @param patch state=published/disabled
     * @param routing 从补丁体推导的动作分派参数
     * @return 商品
     */
    @PatchMapping("/offerings/{offeringId}")
    CrmResourceResponse patchOffering(@PathVariable("offeringId") String offeringId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody Map<String, Object> patch,
            @RequestParam Map<String, String> routing);

    /**
     * 商品上下架并传递操作关联标识。
     *
     * @param offeringId 商品标识
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID
     * @param patch 状态补丁
     * @param routing 分派查询参数
     * @return 商品
     */
    @PatchMapping("/offerings/{offeringId}")
    CrmResourceResponse patchOffering(@PathVariable("offeringId") String offeringId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody Map<String, Object> patch,
            @RequestParam Map<String, String> routing);

    /**
     * 报价签发、确认、撤回或归属转移。
     *
     * @param quotationId 报价标识
     * @param idempotencyKey 幂等键
     * @param patch 状态或目标 owner；签发/确认带 quotationVersion/expectedAggregateVersion
     * @param routing 从补丁体推导的动作分派参数
     * @return 报价
     */
    @PatchMapping("/quotations/{quotationId}")
    CrmResourceResponse patchQuotation(@PathVariable("quotationId") String quotationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody Map<String, Object> patch,
            @RequestParam Map<String, String> routing);

    /**
     * 报价状态或归属变更并传递操作关联标识。
     *
     * @param quotationId 报价标识
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID
     * @param patch 状态或归属补丁
     * @param routing 分派查询参数
     * @return 报价
     */
    @PatchMapping("/quotations/{quotationId}")
    CrmResourceResponse patchQuotation(@PathVariable("quotationId") String quotationId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody Map<String, Object> patch,
            @RequestParam Map<String, String> routing);

    /**
     * 回报履约进度或人工重置失败项，首次 in_progress 即认领。
     *
     * @param fulfillmentId 履约项标识
     * @param idempotencyKey 幂等键
     * @param patch 回报含 sequence/state/resultData；重置仅 state=PENDING_DISPATCH
     * @param routing 从补丁体推导的动作分派参数
     * @return 履约项
     */
    @PatchMapping("/fulfillment-items/{fulfillmentId}")
    CrmResourceResponse patchFulfillmentItem(@PathVariable("fulfillmentId") String fulfillmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody Map<String, Object> patch,
            @RequestParam Map<String, String> routing);

    /**
     * 回报履约进度或人工重置并传递操作关联标识。
     *
     * @param fulfillmentId 履约项标识
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID
     * @param patch 回报或重置补丁
     * @param routing 分派查询参数
     * @return 履约项
     */
    @PatchMapping("/fulfillment-items/{fulfillmentId}")
    CrmResourceResponse patchFulfillmentItem(@PathVariable("fulfillmentId") String fulfillmentId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestBody Map<String, Object> patch,
            @RequestParam Map<String, String> routing);

    /**
     * 根据源报价派生新的重议草稿。
     *
     * @param idempotencyKey 幂等键
     * @param sourceQuotationId 源报价标识
     * @return 新报价
     */
    @PostMapping(value = "/quotations", params = "sourceQuotationId")
    CrmResourceResponse renegotiateQuotation(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestParam("sourceQuotationId") String sourceQuotationId);

    /**
     * 根据源报价派生新的重议草稿并传递操作关联标识。
     *
     * @param idempotencyKey 幂等键
     * @param correlationId 关联 ID
     * @param sourceQuotationId 源报价标识
     * @return 新报价
     */
    @PostMapping(value = "/quotations", params = "sourceQuotationId")
    CrmResourceResponse renegotiateQuotation(@RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestHeader(value = "X-Correlation-Id", required = false) String correlationId,
            @RequestParam("sourceQuotationId") String sourceQuotationId);

    /**
     * 查询业务操作记录。
     *
     * @param resourceId 资源标识过滤，可空
     * @param cursor 不透明续读游标，可空
     * @param limit 每页条数，1～100
     * @return 机器游标页
     */
    @GetMapping("/operation-logs")
    CrmPageResponse listOperationLogs(@RequestParam(value = "resourceId", required = false) String resourceId,
            @RequestParam(value = "cursor", required = false) String cursor,
            @RequestParam("limit") int limit);

}
