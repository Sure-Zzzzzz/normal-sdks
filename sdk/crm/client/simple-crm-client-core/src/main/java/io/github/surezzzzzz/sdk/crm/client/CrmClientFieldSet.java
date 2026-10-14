package io.github.surezzzzzz.sdk.crm.client;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * CRM wire 字段面：与服务端公开 HTTP 契约逐资源对齐（必填/允许两级），供校验器构造视图。
 *
 * @author surezzzzzz
 */
public final class CrmClientFieldSet {

    /**
     * 客户档案字段：id+displayName+state+owner。
     */
    public static final Set<String> ARCHIVE = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    "id", "displayName", "state", "owner")));
    /**
     * 档案通用必填（列表行同面）。
     */
    public static final Set<String> ARCHIVE_REQUIRED = ARCHIVE;
    /**
     * 联系人字段：客户归属使用 customerId，职务 title 可空。
     */
    public static final Set<String> CONTACT = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    "id", "displayName", "state", "customerId", "title")));
    /**
     * 联系人必填字段，不将可空职务作为必填值。
     */
    public static final Set<String> CONTACT_REQUIRED = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    "id", "displayName", "state", "customerId")));
    /**
     * 商品字段：档案信息与稳定业务引用、商业能力、消费者能力。
     */
    public static final Set<String> OFFERING = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    "id", "displayName", "state", "owner", "offeringReference", "capabilityType",
                    "requiredConsumerCapability")));
    /**
     * 商品视图的必填字段，创建、点查、列表和上下架使用同一形状。
     */
    public static final Set<String> OFFERING_REQUIRED = OFFERING;
    /**
     * 报价字段：聚合事实面（版本号/可确认版本/确认订单/取代来源可空）。
     */
    public static final Set<String> QUOTATION = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    "quotationId", "customerId", "owner", "aggregateVersion", "currentVersion",
                    "currentConfirmableVersion", "confirmedOrderId", "supersedesQuotationId",
                    "state", "orderId", "fulfillmentItemIds")));
    /**
     * 报价必填。
     */
    public static final Set<String> QUOTATION_REQUIRED = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    "quotationId", "customerId", "owner", "aggregateVersion", "currentVersion",
                    "state")));
    /**
     * 订单字段。
     */
    public static final Set<String> ORDER = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    "orderId", "sourceQuotationId", "sourceQuotationVersion", "customerId",
                    "settlementCurrency", "totalAmount", "owner", "displayState", "lines")));
    /**
     * 订单必填。
     */
    public static final Set<String> ORDER_REQUIRED = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    "orderId", "sourceQuotationId", "customerId", "settlementCurrency",
                    "totalAmount", "owner", "displayState")));
    /**
     * 履约项字段（v2：新增 consumerProtocolVersion 与冻结义务模板）。
     */
    public static final Set<String> FULFILLMENT = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    "fulfillmentId", "orderId", "orderLineId", "owner", "assignedConsumerId",
                    "state", "reportSequence", "consumerProtocolVersion", "obligation",
                    "resultData", "note", "reportedBy")));
    /**
     * 履约项必填。
     */
    public static final Set<String> FULFILLMENT_REQUIRED = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    "fulfillmentId", "orderId", "orderLineId", "owner", "assignedConsumerId",
                    "state", "reportSequence")));
    /**
     * 合并结果字段。
     */
    public static final Set<String> MERGE = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    "source", "target", "relocatedContacts")));
    /**
     * 合并必填：源客户、目标客户及改挂联系人集合（可为空）。
     */
    public static final Set<String> MERGE_REQUIRED = MERGE;
    /**
     * 转移结果字段。
     */
    public static final Set<String> TRANSFER = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    "customer", "transferredQuotations")));
    /**
     * 转移必填：转移后的客户与同事务转移的报价集合（可为空）。
     */
    public static final Set<String> TRANSFER_REQUIRED = TRANSFER;
    /**
     * 转移结果中的报价摘要字段。
     */
    public static final Set<String> TRANSFERRED_QUOTATION = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList("quotationId", "owner")));
    /**
     * 消费者注册字段。
     */
    public static final Set<String> CONSUMER = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    "consumerId", "capability", "protocolVersion", "enabled")));
    /**
     * 操作记录字段（v2：业务级操作留痕）。
     */
    public static final Set<String> OPERATION_LOG = Collections.unmodifiableSet(
            new LinkedHashSet<String>(java.util.Arrays.asList(
                    "owner", "action", "targetResourceType", "targetResourceId",
                    "correlationId", "resultCode", "occurredAt")));
    /**
     * 操作记录必填。
     */
    public static final Set<String> OPERATION_LOG_REQUIRED = OPERATION_LOG;

    private CrmClientFieldSet() {
        throw new UnsupportedOperationException("常量类不允许实例化");
    }
}
