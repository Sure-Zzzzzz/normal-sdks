package io.github.surezzzzzz.sdk.crm.client.feign.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.github.surezzzzzz.sdk.crm.client.constant.CrmClientConstant;
import io.github.surezzzzzz.sdk.crm.client.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.crm.client.exception.CrmClientProtocolException;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * CRM 机器 wire 列表页响应（统一 items 键 + 不透明 nextCursor）。
 *
 * <p>该模型只表示机器游标页；消费者完整列表使用 {@code CrmCompleteListResponse}。
 * 分页封装拒绝未知字段与非法游标，不接收管理台页码响应。行内资源保持原始 Map，
 * 不自动执行资源字段白名单校验。</p>
 *
 * @author surezzzzzz
 */
@Data
public class CrmPageResponse {

    private List<Map<String, Object>> items;

    private String nextCursor;

    /** 整包解析后显式校验，确保契约异常不会被 Jackson 的缺字段异常替代。 */
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public CrmPageResponse(Map<String, Object> payload) {
        if (payload == null || !payload.containsKey(CrmClientConstant.FIELD_ITEMS)) {
            throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                    ErrorMessage.FIELD_MUST_BE_ARRAY + ": " + CrmClientConstant.FIELD_ITEMS);
        }
        for (String key : payload.keySet()) {
            if (!CrmClientConstant.FIELD_ITEMS.equals(key)
                    && !CrmClientConstant.FIELD_NEXT_CURSOR.equals(key)) {
                throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                        String.format(ErrorMessage.UNKNOWN_PAGE_FIELD, key));
            }
        }
        Object rawItems = payload.get(CrmClientConstant.FIELD_ITEMS);
        if (!(rawItems instanceof List)) {
            throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                    ErrorMessage.FIELD_MUST_BE_ARRAY + ": " + CrmClientConstant.FIELD_ITEMS);
        }
        this.items = castItems((List<?>) rawItems);
        if (!payload.containsKey(CrmClientConstant.FIELD_NEXT_CURSOR)) {
            throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                    String.format(ErrorMessage.REQUIRED_FIELD_MISSING, CrmClientConstant.FIELD_NEXT_CURSOR));
        }
        setNextCursor(payload.get(CrmClientConstant.FIELD_NEXT_CURSOR));
    }

    /**
     * 网络游标只允许非空字符串或 null，禁止 Jackson 将数字自动转换为字符串。
     *
     * @param value 原始 JSON 游标值
     */
    public void setNextCursor(Object value) {
        if (value != null && (!(value instanceof String) || ((String) value).trim().isEmpty())) {
            throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                    ErrorMessage.CURSOR_MUST_BE_STRING_OR_NULL);
        }
        this.nextCursor = (String) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> castItems(List<?> rawItems) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>(rawItems.size());
        for (Object item : rawItems) {
            if (!(item instanceof Map)) {
                throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                        ErrorMessage.ITEM_MUST_BE_OBJECT);
            }
            result.add((Map<String, Object>) item);
        }
        return result;
    }
}
