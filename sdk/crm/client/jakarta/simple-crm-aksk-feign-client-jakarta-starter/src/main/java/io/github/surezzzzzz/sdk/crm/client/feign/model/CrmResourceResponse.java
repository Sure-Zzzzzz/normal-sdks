package io.github.surezzzzzz.sdk.crm.client.feign.model;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * CRM wire 单资源响应：保留全部顶层字段及嵌套对象，字段校验由调用方显式执行。
 *
 * @author surezzzzzz
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CrmResourceResponse {

    private Map<String, Object> fields = new LinkedHashMap<String, Object>();

    /**
     * wire 顶层字段逐键收入 fields；合并、转移的嵌套对象及数组保留原始形状。
     *
     * @param key 字段键
     * @param value 字段值
     */
    @JsonAnySetter
    public void put(String key, Object value) {
        fields.put(key, value);
    }

    /**
     * 取字段值。
     *
     * @param key 字段键
     * @return 值或 null
     */
    public Object get(String key) {
        return fields == null ? null : fields.get(key);
    }
}
