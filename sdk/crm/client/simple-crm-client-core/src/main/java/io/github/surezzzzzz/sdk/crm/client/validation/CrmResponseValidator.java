package io.github.surezzzzzz.sdk.crm.client.validation;

import io.github.surezzzzzz.sdk.crm.client.CrmClientFieldSet;
import io.github.surezzzzzz.sdk.crm.client.constant.CrmClientConstant;
import io.github.surezzzzzz.sdk.crm.client.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.crm.client.exception.CrmClientProtocolException;
import io.github.surezzzzzz.sdk.crm.client.model.CrmResourceView;

import java.util.*;

/**
 * 响应校验器：拒绝未知关键字段、必填缺失、非字符串状态值——违反即抛协议异常，不构造半成品结果。
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
            if (value == null || value instanceof Map && ((Map<?, ?>) value).isEmpty()) {
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
        // 联系人的职务可空，其余档案、商品和转移报价摘要字段必须为字符串。
        if (allowedFields.equals(CrmClientFieldSet.ARCHIVE)
                || allowedFields.equals(CrmClientFieldSet.CONTACT)
                || allowedFields.equals(CrmClientFieldSet.OFFERING)
                || allowedFields.equals(CrmClientFieldSet.TRANSFERRED_QUOTATION)) {
            for (String key : payload.keySet()) {
                if (allowedFields.equals(CrmClientFieldSet.CONTACT)
                        && FIELD_TITLE.equals(key) && payload.get(key) == null) {
                    continue;
                }
                if (!(payload.get(key) instanceof String)) {
                    throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                            String.format(ErrorMessage.FIELD_MUST_BE_STRING, key));
                }
            }
        }
        return new CrmResourceView(new LinkedHashMap<String, Object>(payload));
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
}
