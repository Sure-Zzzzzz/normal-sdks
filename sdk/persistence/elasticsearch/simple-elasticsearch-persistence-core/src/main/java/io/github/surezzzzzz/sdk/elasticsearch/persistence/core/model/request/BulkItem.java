package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;
import java.util.Map;

/**
 * Bulk Item
 *
 * @author surezzzzzz
 */
@Data
@Builder
public class BulkItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private BulkItemType type;
    private Object document;
    private String index;
    private String id;
    private Map<String, Object> fieldMap;
    private String scriptSource;
    private Map<String, Object> scriptParamMap;
    private Boolean docAsUpsert;
    private String routing;
}
