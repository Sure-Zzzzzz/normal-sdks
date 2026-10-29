package io.github.surezzzzzz.sdk.crm.client.feign.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import io.github.surezzzzzz.sdk.crm.client.constant.CrmClientConstant;
import io.github.surezzzzzz.sdk.crm.client.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.crm.client.exception.CrmClientProtocolException;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** CRM 完整列表 wire 响应；只允许 items，不携带分页游标。 */
@Data
public class CrmCompleteListResponse {

    private final List<Map<String, Object>> items;

    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public CrmCompleteListResponse(Map<String, Object> payload) {
        if (payload == null || !payload.containsKey(CrmClientConstant.FIELD_ITEMS)) {
            throw new CrmClientProtocolException(CrmClientConstant.SUCCESS_HTTP_STATUS,
                    ErrorMessage.FIELD_MUST_BE_ARRAY + ": " + CrmClientConstant.FIELD_ITEMS);
        }
        for (String key : payload.keySet()) {
            if (!CrmClientConstant.FIELD_ITEMS.equals(key)) {
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
