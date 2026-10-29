package io.github.surezzzzzz.sdk.crm.client;

import io.github.surezzzzzz.sdk.crm.client.command.CrmClientCommand;
import io.github.surezzzzzz.sdk.crm.client.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.crm.client.model.CrmPageView;
import io.github.surezzzzzz.sdk.crm.client.model.CrmResourceView;

import java.util.List;
import java.util.Map;

/**
 * CRM 机器客户端契约（资源名词 + PATCH 状态变更）。
 *
 * <p>与服务端 RESTful API 一一对应：状态变更统一走 patch 方法族
 * （客户停用/复启/合并/转移=patchCustomer、目录上架/下架=patchOffering、
 * 报价签发/确认/撤回/转移=patchQuotation、履约回报/重置=patchFulfillmentItem）；
 * 重议=createQuotation 带 sourceQuotationId。</p>
 *
 * <p>实现约束：金额/数量以十进制字符串进出；时间带 offset ISO-8601；
 * 状态只用稳定 code；写方法首参元数据携带幂等键；列表以不透明 cursor 续读，
 * 不发送管理台 page/pageSize 参数。消费者列表为不带分页参数的完整列表。</p>
 *
 * @author surezzzzzz
 */
public interface CrmClient {

    // ===== 客户 =====

    /**
     * 创建客户。
     *
     * @param command     命令元数据
     * @param displayName 展示名称
     * @return 客户视图
     */
    CrmResourceView createCustomer(CrmClientCommand command, String displayName);

    /**
     * 点查客户。
     *
     * @param customerId 客户标识
     * @return 客户视图
     */
    CrmResourceView getCustomer(String customerId);

    /**
     * 客户列表。
     *
     * @param state  状态过滤（可空）
     * @param cursor 游标（可空=首页）
     * @param limit  每页上限
     * @return 列表页
     */
    CrmPageView listCustomers(String state, String cursor, int limit);

    /**
     * 客户列表（带名称或 ID 关键字过滤）。旧版实现未覆盖时，仅允许省略关键字调用。
     *
     * @param state   状态过滤（可空）
     * @param keyword 名称或 ID 包含匹配（可空）
     * @param cursor  游标（可空=首页）
     * @param limit   每页上限
     * @return 列表页
     */
    default CrmPageView listCustomers(String state, String keyword, String cursor, int limit) {
        if (keyword == null) {
            return listCustomers(state, cursor, limit);
        }
        throw new UnsupportedOperationException(ErrorMessage.OPERATION_NOT_SUPPORTED);
    }

    /**
     * 修改客户（PATCH 状态变更：停用/复启/合并/归属转移按 patch 字段区分）。
     *
     * @param command    命令元数据
     * @param customerId 客户标识
     * @param patch      补丁体（state=inactive/active/merged 或 owner=iam:xxx；合并须含 mergeTargetId）
     * @return 停用/复启为客户视图；合并为 source/target/relocatedContacts；转移为 customer/transferredQuotations
     */
    CrmResourceView patchCustomer(CrmClientCommand command, String customerId,
                                  Map<String, Object> patch);

    // ===== 联系人 =====

    /**
     * 创建联系人。
     *
     * @param command     命令元数据
     * @param customerId  归属客户
     * @param displayName 展示名称
     * @param title       职务（可空）
     * @return 联系人视图
     */
    CrmResourceView createContact(CrmClientCommand command, String customerId, String displayName,
                                  String title);

    /**
     * 点查联系人。
     *
     * @param contactId 联系人标识
     * @return 联系人视图
     */
    CrmResourceView getContact(String contactId);

    /**
     * 联系人列表（按客户）。
     *
     * @param customerId 客户标识
     * @param cursor     游标
     * @param limit      每页上限
     * @return 列表页
     */
    CrmPageView listContacts(String customerId, String cursor, int limit);

    // ===== 目录 =====

    /**
     * 创建目录项（DRAFT 录入）。
     *
     * @param command                    命令元数据
     * @param offeringReference          稳定业务引用
     * @param displayName                展示名称
     * @param capabilityType             商业能力类型稳定 code
     * @param requiredConsumerCapability 所需消费者能力
     * @return 目录视图
     */
    CrmResourceView createOffering(CrmClientCommand command, String offeringReference,
                                   String displayName, String capabilityType,
                                   String requiredConsumerCapability);

    /**
     * 点查目录。
     *
     * @param offeringId 目录标识
     * @return 目录视图
     */
    CrmResourceView getOffering(String offeringId);

    /**
     * 目录列表。
     *
     * @param state  状态过滤（可空）
     * @param cursor 游标
     * @param limit  每页上限
     * @return 列表页
     */
    CrmPageView listOfferings(String state, String cursor, int limit);

    /**
     * 目录列表（带名称、业务引用或 ID 关键字过滤）。旧版实现未覆盖时，仅允许省略关键字调用。
     *
     * @param state   状态过滤（可空）
     * @param keyword 名称、业务引用或 ID 包含匹配（可空）
     * @param cursor  游标（可空=首页）
     * @param limit   每页上限
     * @return 列表页
     */
    default CrmPageView listOfferings(String state, String keyword, String cursor, int limit) {
        if (keyword == null) {
            return listOfferings(state, cursor, limit);
        }
        throw new UnsupportedOperationException(ErrorMessage.OPERATION_NOT_SUPPORTED);
    }

    /**
     * 修改目录项（PATCH 状态变更：上架/下架按 patch.state 区分）。
     *
     * @param command    命令元数据
     * @param offeringId 目录标识
     * @param patch      补丁体（state=published/disabled）
     * @return 目录视图
     */
    CrmResourceView patchOffering(CrmClientCommand command, String offeringId,
                                  Map<String, Object> patch);

    // ===== 商机 =====

    /**
     * 创建商机。
     *
     * @param command 命令元数据
     * @param customerId 客户标识
     * @param title 商机标题
     * @param primaryOfferingId 主商品标识（可空）
     * @param contactId 主要联系人标识（可空）
     * @param expectedCloseDate 预计成交日期（ISO-8601 日期，可空）
     * @param estimatedAmount 预计金额十进制字符串（可空）
     * @param currency 金额币种（可空）
     * @return 商机视图
     */
    default CrmResourceView createOpportunity(CrmClientCommand command, String customerId, String title,
                                              String primaryOfferingId, String contactId,
                                              String expectedCloseDate, String estimatedAmount, String currency) {
        throw new UnsupportedOperationException(ErrorMessage.OPERATION_NOT_SUPPORTED);
    }

    /**
     * 点查商机。
     *
     * @param opportunityId 商机标识
     * @return 商机视图
     */
    default CrmResourceView getOpportunity(String opportunityId) {
        throw new UnsupportedOperationException(ErrorMessage.OPERATION_NOT_SUPPORTED);
    }

    /**
     * 商机列表（机器游标模式）。
     *
     * @param stage 阶段过滤（可空）
     * @param customerId 客户过滤（可空）
     * @param keyword 标题关键字（可空）
     * @param cursor 不透明游标（可空）
     * @param limit 每页上限
     * @return 列表页
     */
    default CrmPageView listOpportunities(String stage, String customerId, String keyword,
                                          String cursor, int limit) {
        throw new UnsupportedOperationException(ErrorMessage.OPERATION_NOT_SUPPORTED);
    }

    /**
     * 推进商机阶段；阶段为 won 时服务端只接受报价确认事务派生，人工调用会被拒绝。
     *
     * @param command 命令元数据
     * @param opportunityId 商机标识
     * @param stage 目标阶段
     * @param lostReason 输单原因（仅 lost 必填）
     * @return 商机视图
     */
    default CrmResourceView patchOpportunityStage(CrmClientCommand command, String opportunityId,
                                                  String stage, String lostReason) {
        throw new UnsupportedOperationException(ErrorMessage.OPERATION_NOT_SUPPORTED);
    }

    /**
     * 转移商机归属。
     *
     * @param command 命令元数据
     * @param opportunityId 商机标识
     * @param owner 新归属主体
     * @return 商机视图
     */
    default CrmResourceView patchOpportunityOwner(CrmClientCommand command, String opportunityId,
                                                  String owner) {
        throw new UnsupportedOperationException(ErrorMessage.OPERATION_NOT_SUPPORTED);
    }

    // ===== 报价 =====

    /**
     * 创建报价草稿。
     *
     * @param command    命令元数据
     * @param customerId 客户标识
     * @param validUntil 有效截止（offset ISO-8601）
     * @param lines      行命令（offeringId/quantity/unit/unitPrice{amount,currency}/subjectReference/fulfillmentScope）；
     *                   同商品可出现多行，各行保留独立数量、价格、授权对象和履约范围
     * @return 报价视图
     */
    CrmResourceView createQuotation(CrmClientCommand command, String customerId,
                                    String validUntil, List<Map<String, Object>> lines);

    /**
     * 创建报价草稿并可选关联商机。保留旧重载以兼容未使用商机的调用方。
     *
     * @param command 命令元数据
     * @param customerId 客户标识
     * @param opportunityId 商机标识（可空）
     * @param validUntil 有效截止（offset ISO-8601）
     * @param lines 行命令
     * @return 报价视图
     */
    default CrmResourceView createQuotation(CrmClientCommand command, String customerId,
                                            String opportunityId, String validUntil,
                                            List<Map<String, Object>> lines) {
        if (opportunityId == null) {
            return createQuotation(command, customerId, validUntil, lines);
        }
        // 旧实现未声明商机参数，不能静默降级为未关联商机的报价。
        throw new UnsupportedOperationException(ErrorMessage.OPERATION_NOT_SUPPORTED);
    }

    /**
     * 重议派生新草稿（POST /quotations 带 sourceQuotationId）。
     *
     * @param command           命令元数据
     * @param sourceQuotationId 源报价标识
     * @return 新报价视图
     */
    CrmResourceView renegotiateQuotation(CrmClientCommand command, String sourceQuotationId);

    /**
     * 点查报价。
     *
     * @param quotationId 报价标识
     * @return 报价视图
     */
    CrmResourceView getQuotation(String quotationId);

    /**
     * 点查报价版本（含行明细）。
     *
     * @param quotationId 报价标识
     * @param version     版本号
     * @return 版本视图
     */
    CrmResourceView getQuotationVersion(String quotationId, int version);

    /**
     * 报价列表。
     *
     * @param state  状态过滤（可空）
     * @param cursor 游标
     * @param limit  每页上限
     * @return 列表页
     */
    CrmPageView listQuotations(String state, String cursor, int limit);

    /**
     * 报价列表（带单号或客户关键字过滤）。旧版实现未覆盖时，仅允许省略关键字调用。
     *
     * @param state   状态过滤（可空）
     * @param keyword 单号或客户显示名包含匹配（可空）
     * @param cursor  游标（可空=首页）
     * @param limit   每页上限
     * @return 列表页
     */
    default CrmPageView listQuotations(String state, String keyword, String cursor, int limit) {
        if (keyword == null) {
            return listQuotations(state, cursor, limit);
        }
        throw new UnsupportedOperationException(ErrorMessage.OPERATION_NOT_SUPPORTED);
    }

    /**
     * 编辑报价草稿（整单替换行，版本号不变）。
     *
     * @param command     命令元数据
     * @param quotationId 报价标识
     * @param validUntil  有效截止
     * @param lines       行命令，同商品多行保留独立业务行，不合并或去重
     * @return 报价视图
     */
    CrmResourceView editQuotation(CrmClientCommand command, String quotationId,
                                  String validUntil, List<Map<String, Object>> lines);

    /**
     * 修改报价（PATCH 状态变更：签发/确认/撤回/转移按 patch 字段区分）。
     *
     * @param command     命令元数据
     * @param quotationId 报价标识
     * @param patch       补丁体（state=issued/confirmed/withdrawn 或 owner=iam:xxx；
     *                    签发/确认须含 quotationVersion 与 expectedAggregateVersion）
     * @return 报价视图
     */
    CrmResourceView patchQuotation(CrmClientCommand command, String quotationId,
                                   Map<String, Object> patch);

    // ===== 订单 =====

    /**
     * 点查订单（含行明细与履约义务）。
     *
     * @param orderId 订单标识
     * @return 订单视图
     */
    CrmResourceView getOrder(String orderId);

    /**
     * 订单列表。
     *
     * @param cursor 游标
     * @param limit  每页上限
     * @return 列表页
     */
    CrmPageView listOrders(String cursor, int limit);

    /**
     * 订单列表（带订单号或客户 ID 关键字过滤）。旧版实现未覆盖时，仅允许省略关键字调用。
     *
     * @param keyword 订单号或客户 ID 包含匹配（可空）
     * @param cursor  游标（可空=首页）
     * @param limit   每页上限
     * @return 列表页
     */
    default CrmPageView listOrders(String keyword, String cursor, int limit) {
        if (keyword == null) {
            return listOrders(cursor, limit);
        }
        throw new UnsupportedOperationException(ErrorMessage.OPERATION_NOT_SUPPORTED);
    }

    // ===== 履约 =====

    /**
     * 点查履约项（机器按派发归属可见）。
     *
     * @param fulfillmentId 履约项标识
     * @return 履约项视图
     */
    CrmResourceView getFulfillmentItem(String fulfillmentId);

    /**
     * 履约项列表（消费者拉取待执行项：state=pending_dispatch/in_progress）。
     *
     * @param state   状态过滤
     * @param orderId 订单过滤（人员面）
     * @param cursor  游标
     * @param limit   每页上限
     * @return 列表页
     */
    CrmPageView listFulfillmentItems(String state, String orderId, String cursor, int limit);

    /**
     * 修改履约项（PATCH：回报进度或人工重置按 patch 区分）。
     *
     * @param command       命令元数据
     * @param fulfillmentId 履约项标识
     * @param patch         补丁体（回报含 sequence/state/resultData；重置仅 state=PENDING_DISPATCH）
     * @return 履约项视图
     */
    CrmResourceView patchFulfillmentItem(CrmClientCommand command, String fulfillmentId,
                                         Map<String, Object> patch);

    // ===== 消费者 =====

    /**
     * 注册或更新履约消费者（consumerId 须与 AKP clientId 一致）。
     *
     * @param command         命令元数据
     * @param consumerId      消费者标识
     * @param capability      能力
     * @param protocolVersion 协议版本
     * @param enabled         是否启用
     * @return 注册结果视图
     */
    CrmResourceView upsertFulfillmentConsumer(CrmClientCommand command, String consumerId,
                                              String capability, int protocolVersion,
                                              boolean enabled);

    /**
     * 消费者完整列表（不使用分页参数，返回结果无续读游标）。
     *
     * @return 列表页
     */
    CrmPageView listFulfillmentConsumers();

    // ===== 操作记录 =====

    /**
     * 操作记录列表（业务级：谁在何时对哪个对象做了什么）。
     *
     * @param resourceId 资源过滤（可空）
     * @param cursor     游标
     * @param limit      每页上限
     * @return 列表页
     */
    CrmPageView listOperationLogs(String resourceId, String cursor, int limit);
}
