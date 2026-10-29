package io.github.surezzzzzz.sdk.crm.client.validation;

import io.github.surezzzzzz.sdk.crm.client.CrmClientFieldSet;
import io.github.surezzzzzz.sdk.crm.client.constant.CrmClientConstant;
import io.github.surezzzzzz.sdk.crm.client.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.crm.client.exception.CrmClientProtocolException;
import io.github.surezzzzzz.sdk.crm.client.model.CrmResourceView;

import java.util.*;

/**
 * 响应校验器：拒绝未知字段、必填/存在性字段缺失和不符合契约的类型——违反即抛协议异常，
 * 不构造半成品结果。
 *
 * <p>CRM 为事务型消费，不提供失败时保留旧响应的兜底；校验白名单与公开 HTTP 契约字段面对齐，
 * 由构造方按资源类型传入。</p>
 *
 * @author surezzzzzz
 */
public final class CrmResponseValidator {

    private static final String FIELD_SOURCE = "source";
    private static final String FIELD_TARGET = "target";
    private static final String FIELD_CUSTOMER = "customer";
    private static final String FIELD_RELOCATED_CONTACTS = "relocatedContacts";
    private static final String FIELD_TRANSFERRED_QUOTATIONS = "transferredQuotations";
    private static final String FIELD_STATE = "state";
    private static final String FIELD_TITLE = "title";
    private static final Set<String> OPPORTUNITY_STRING_FIELDS = Collections.unmodifiableSet(
            new LinkedHashSet<String>(Arrays.asList(
                    "opportunityId", "customerId", "owner", "title", "primaryOfferingId", "contactId",
                    "expectedCloseDate", "estimatedAmount", "currency", "stage", "lostReason",
                    "createdAt", "updatedAt")));

    private CrmResponseValidator() {
        throw new UnsupportedOperationException(ErrorMessage.UTILITY_CLASS);
    }

    /**
     * 校验并构造单资源视图。
     *
     * @param payload        响应 JSON 对象
     * @param requiredFields 必填字段键集
     * @param allowedFields  允许字段键集（含必填）
     * @return 已校验资源视图
     */
    public static CrmResourceView object(Map<String, Object> payload, Set<String> requiredFields,
                                         Set<String> allowedFields) {
        if (payload == null) {
            throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS, ErrorMessage.EMPTY_RESPONSE);
        }
        for (String required : requiredFields) {
            Object value = payload.get(required);
            if (!payload.containsKey(required)
                    || (value == null && !isNullableRequiredField(allowedFields, required))
                    || value instanceof Map && ((Map<?, ?>) value).isEmpty()) {
                throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                        String.format(ErrorMessage.REQUIRED_FIELD_MISSING, required));
            }
        }
        for (String key : payload.keySet()) {
            if (!allowedFields.contains(key)) {
                throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                        String.format(ErrorMessage.UNKNOWN_FIELD, key));
            }
        }
        Object state = payload.get(FIELD_STATE);
        if (payload.containsKey(FIELD_STATE) && !(state instanceof String)) {
            throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                    ErrorMessage.STATE_MUST_BE_STRING);
        }
        // 联系人的职务和其他 OpenAPI nullable 字段可以为 null，非空值仍必须符合类型。
        if (allowedFields.equals(CrmClientFieldSet.ARCHIVE)
                || allowedFields.equals(CrmClientFieldSet.CONTACT)
                || allowedFields.equals(CrmClientFieldSet.OFFERING)
                || allowedFields.equals(CrmClientFieldSet.TRANSFERRED_QUOTATION)
                || allowedFields.equals(CrmClientFieldSet.OBLIGATION)) {
            validateStringFields(payload, allowedFields);
        }
        if (allowedFields.equals(CrmClientFieldSet.OPPORTUNITY)) {
            validateStringFields(payload, OPPORTUNITY_STRING_FIELDS);
            validateNumberFields(payload, Collections.singleton("aggregateVersion"));
        }
        if (allowedFields.equals(CrmClientFieldSet.QUOTATION)
                || allowedFields.equals(CrmClientFieldSet.QUOTATION_CONFIRMATION)) {
            validateStringFields(payload, quotationStringFields(allowedFields));
            validateNumberFields(payload, Arrays.asList("aggregateVersion", "currentVersion",
                    "currentConfirmableVersion"));
            if (allowedFields.equals(CrmClientFieldSet.QUOTATION_CONFIRMATION)) {
                validateStringList(payload, "fulfillmentItemIds");
            }
        }
        if (allowedFields.equals(CrmClientFieldSet.QUOTATION_VERSION)) {
            validateStringFields(payload, Arrays.asList("quotationId", "state", "settlementCurrency",
                    "validUntil", "issuedAt", "terminalAt"));
            validateNumberFields(payload, Collections.singleton("version"));
            nestedResource(payload.get("totalAmount"), CrmClientFieldSet.MONEY);
            nestedList(payload.get("lines"), CrmClientFieldSet.QUOTATION_LINE);
        }
        if (allowedFields.equals(CrmClientFieldSet.QUOTATION_LINE)) {
            validateStringFields(payload, Arrays.asList("quotationLineId", "offeringId", "offeringReference",
                    "quantity", "unit"));
            nestedResource(payload.get("unitPrice"), CrmClientFieldSet.MONEY);
            nestedResource(payload.get("lineTotal"), CrmClientFieldSet.MONEY);
            nestedResource(payload.get("obligation"), CrmClientFieldSet.OBLIGATION);
        }
        if (allowedFields.equals(CrmClientFieldSet.ORDER)
                || allowedFields.equals(CrmClientFieldSet.ORDER_LIST)) {
            validateStringFields(payload, Arrays.asList("orderId", "sourceQuotationId", "customerId",
                    "settlementCurrency", "owner", "displayState"));
            validateNumberFields(payload, Collections.singleton("sourceQuotationVersion"));
            nestedResource(payload.get("totalAmount"), CrmClientFieldSet.MONEY);
            if (allowedFields.equals(CrmClientFieldSet.ORDER)) {
                nestedList(payload.get("lines"), CrmClientFieldSet.ORDER_LINE);
            }
        }
        if (allowedFields.equals(CrmClientFieldSet.ORDER_LINE)) {
            validateStringFields(payload, Arrays.asList("orderLineId", "sourceQuotationLineId",
                    "offeringReference", "quantity", "unit"));
            nestedResource(payload.get("unitPrice"), CrmClientFieldSet.MONEY);
            nestedResource(payload.get("lineTotal"), CrmClientFieldSet.MONEY);
            nestedResource(payload.get("obligation"), CrmClientFieldSet.OBLIGATION);
        }
        if (allowedFields.equals(CrmClientFieldSet.MONEY)) {
            validateStringFields(payload, CrmClientFieldSet.MONEY);
        }
        if (allowedFields.equals(CrmClientFieldSet.FULFILLMENT)) {
            validateStringFields(payload, Arrays.asList("fulfillmentId", "orderId", "orderLineId", "owner",
                    "assignedConsumerId", "state", "resultData", "note", "reportedBy"));
            validateNumberFields(payload, Arrays.asList("reportSequence", "consumerProtocolVersion"));
            nestedResource(payload.get("obligation"), CrmClientFieldSet.OBLIGATION);
        }
        if (allowedFields.equals(CrmClientFieldSet.CONSUMER)) {
            validateStringFields(payload, Arrays.asList("consumerId", "capability"));
            validateNumberFields(payload, Collections.singleton("protocolVersion"));
            validateBooleanFields(payload, Collections.singleton("enabled"));
        }
        if (allowedFields.equals(CrmClientFieldSet.OPERATION_LOG)) {
            validateStringFields(payload, Arrays.asList("owner", "action", "targetResourceType",
                    "targetResourceId", "correlationId", "resultCode", "occurredAt"));
        }
        return new CrmResourceView(new LinkedHashMap<String, Object>(payload));
    }

    private static boolean isNullableRequiredField(Set<String> allowedFields, String field) {
        return allowedFields.equals(CrmClientFieldSet.OPERATION_LOG)
                && ("correlationId".equals(field) || "resultCode".equals(field));
    }

    private static Set<String> quotationStringFields(Set<String> allowedFields) {
        Set<String> fields = new LinkedHashSet<String>(Arrays.asList(
                "state", "quotationId", "customerId", "opportunityId", "owner", "confirmedOrderId",
                "supersedesQuotationId"));
        if (allowedFields.equals(CrmClientFieldSet.QUOTATION_CONFIRMATION)) {
            fields.add("orderId");
        }
        return fields;
    }

    private static void validateStringFields(Map<String, Object> payload, Iterable<String> fields) {
        for (String field : fields) {
            if (payload.containsKey(field) && payload.get(field) != null
                    && !(payload.get(field) instanceof String)) {
                throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                        String.format(ErrorMessage.FIELD_MUST_BE_STRING, field));
            }
        }
    }

    private static void validateNumberFields(Map<String, Object> payload, Iterable<String> fields) {
        for (String field : fields) {
            if (payload.containsKey(field) && payload.get(field) != null
                    && !(payload.get(field) instanceof Number)) {
                throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                        String.format(ErrorMessage.FIELD_MUST_BE_NUMBER, field));
            }
        }
    }

    private static void validateBooleanFields(Map<String, Object> payload, Iterable<String> fields) {
        for (String field : fields) {
            if (payload.containsKey(field) && payload.get(field) != null
                    && !(payload.get(field) instanceof Boolean)) {
                throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                        String.format(ErrorMessage.FIELD_MUST_BE_BOOLEAN, field));
            }
        }
    }

    private static void validateStringList(Map<String, Object> payload, String field) {
        Object raw = payload.get(field);
        if (!(raw instanceof List)) {
            throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                    String.format(ErrorMessage.FIELD_MUST_BE_ARRAY, field));
        }
        for (Object item : (List<?>) raw) {
            if (!(item instanceof String)) {
                throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                        String.format(ErrorMessage.ARRAY_ITEM_MUST_BE_STRING, field));
            }
        }
    }

    /**
     * 校验客户合并结果，客户与改挂联系人分别执行各自字段契约。
     *
     * @param payload 合并响应
     * @return 已校验的不可变结果
     */
    public static CrmResourceView customerMerge(Map<String, Object> payload) {
        object(payload, CrmClientFieldSet.MERGE_REQUIRED, CrmClientFieldSet.MERGE);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put(FIELD_SOURCE, nestedResource(payload.get(FIELD_SOURCE), CrmClientFieldSet.ARCHIVE));
        result.put(FIELD_TARGET, nestedResource(payload.get(FIELD_TARGET), CrmClientFieldSet.ARCHIVE));
        result.put(FIELD_RELOCATED_CONTACTS,
                nestedList(payload.get(FIELD_RELOCATED_CONTACTS), CrmClientFieldSet.CONTACT_REQUIRED,
                        CrmClientFieldSet.CONTACT));
        return new CrmResourceView(result);
    }

    /**
     * 校验客户归属转移结果，包含客户档案与转移报价的最小摘要。
     *
     * @param payload 转移响应
     * @return 已校验的不可变结果
     */
    public static CrmResourceView customerTransfer(Map<String, Object> payload) {
        object(payload, CrmClientFieldSet.TRANSFER_REQUIRED, CrmClientFieldSet.TRANSFER);
        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put(FIELD_CUSTOMER, nestedResource(payload.get(FIELD_CUSTOMER), CrmClientFieldSet.ARCHIVE));
        result.put(FIELD_TRANSFERRED_QUOTATIONS,
                nestedList(payload.get(FIELD_TRANSFERRED_QUOTATIONS), CrmClientFieldSet.TRANSFERRED_QUOTATION));
        return new CrmResourceView(result);
    }

    private static Map<String, Object> nestedResource(Object raw, Set<String> fields) {
        return nestedResource(raw, fields, fields);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> nestedResource(Object raw, Set<String> requiredFields,
                                                      Set<String> allowedFields) {
        if (!(raw instanceof Map)) {
            throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                    ErrorMessage.NESTED_RESOURCE_MUST_BE_OBJECT);
        }
        return object((Map<String, Object>) raw, requiredFields, allowedFields).asMap();
    }

    private static List<Map<String, Object>> nestedList(Object raw, Set<String> fields) {
        return nestedList(raw, fields, fields);
    }

    private static List<Map<String, Object>> nestedList(Object raw, Set<String> requiredFields,
                                                        Set<String> allowedFields) {
        if (!(raw instanceof List)) {
            throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                    ErrorMessage.NESTED_RESOURCES_MUST_BE_ARRAY);
        }
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        for (Object item : (List<?>) raw) {
            result.add(nestedResource(item, requiredFields, allowedFields));
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * 校验并构造机器游标列表页，调用方法不发送管理台页码参数。
     *
     * @param payload        响应 JSON 对象
     * @param itemsKey       行数组键
     * @param requiredFields 每行必填字段
     * @param allowedFields  每行允许字段
     * @return 列表页视图
     */
    @SuppressWarnings("unchecked")
    public static io.github.surezzzzzz.sdk.crm.client.model.CrmPageView page(
            Map<String, Object> payload, String itemsKey, Set<String> requiredFields,
            Set<String> allowedFields) {
        Object raw = payload == null ? null : payload.get(itemsKey);
        if (!(raw instanceof List)) {
            throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                    String.format(ErrorMessage.ITEMS_MISSING, itemsKey));
        }
        // 机器方法不能误消费页码响应，或把新增分页字段静默解释为末页。
        for (String key : payload.keySet()) {
            if (!itemsKey.equals(key) && !CrmClientConstant.FIELD_NEXT_CURSOR.equals(key)) {
                throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                        String.format(ErrorMessage.UNKNOWN_PAGE_FIELD, key));
            }
        }
        if (!payload.containsKey(CrmClientConstant.FIELD_NEXT_CURSOR)) {
            throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                    String.format(ErrorMessage.REQUIRED_FIELD_MISSING, CrmClientConstant.FIELD_NEXT_CURSOR));
        }
        Object cursor = payload.get(CrmClientConstant.FIELD_NEXT_CURSOR);
        if (cursor != null && (!(cursor instanceof String) || ((String) cursor).trim().isEmpty())) {
            throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                    ErrorMessage.CURSOR_MUST_BE_STRING_OR_NULL);
        }
        List<CrmResourceView> items = new ArrayList<CrmResourceView>();
        for (Object item : (List<Object>) raw) {
            if (!(item instanceof Map)) {
                throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                        ErrorMessage.ITEM_MUST_BE_OBJECT);
            }
            items.add(object((Map<String, Object>) item, requiredFields, allowedFields));
        }
        return new io.github.surezzzzzz.sdk.crm.client.model.CrmPageView(items,
                (String) cursor);
    }

    /**
     * 校验不分页的完整列表响应。该形状只允许 {@code items}，不复用游标页校验，
     * 以免把缺少 nextCursor 的完整列表误判为截断的游标页。
     *
     * @param payload 响应 JSON 对象
     * @param itemsKey 行数组键
     * @param requiredFields 每行必填字段
     * @param allowedFields 每行允许字段
     * @return 完整列表视图
     */
    @SuppressWarnings("unchecked")
    public static io.github.surezzzzzz.sdk.crm.client.model.CrmPageView completeList(
            Map<String, Object> payload, String itemsKey, Set<String> requiredFields,
            Set<String> allowedFields) {
        Object raw = payload == null ? null : payload.get(itemsKey);
        if (!(raw instanceof List)) {
            throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                    String.format(ErrorMessage.ITEMS_MISSING, itemsKey));
        }
        for (String key : payload.keySet()) {
            if (!itemsKey.equals(key)) {
                throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                        String.format(ErrorMessage.UNKNOWN_PAGE_FIELD, key));
            }
        }
        List<CrmResourceView> items = new ArrayList<CrmResourceView>();
        for (Object item : (List<Object>) raw) {
            if (!(item instanceof Map)) {
                throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                        ErrorMessage.ITEM_MUST_BE_OBJECT);
            }
            items.add(object((Map<String, Object>) item, requiredFields, allowedFields));
        }
        return new io.github.surezzzzzz.sdk.crm.client.model.CrmPageView(items, null);
    }
}
