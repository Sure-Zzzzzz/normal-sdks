package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.result;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.BulkItemType;
import lombok.Builder;
import lombok.Data;

import java.io.Serializable;

/**
 * Bulk Item Failure
 *
 * @author surezzzzzz
 */
@Data
@Builder
public class BulkItemFailure implements Serializable {

    private static final long serialVersionUID = 1L;

    private int itemIndex;
    private BulkItemType type;
    private String id;
    private String index;
    private String datasource;
    private String errorCode;
    private String errorMessage;
}
