package io.github.surezzzzzz.sdk.elasticsearch.persistence.processor;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.PersistenceOperationType;
import lombok.Builder;
import lombok.Data;

/**
 * Document Process Context
 *
 * @author surezzzzzz
 */
@Data
@Builder
public class DocumentProcessContext {

    private PersistenceOperationType operationType;
    private String rawIndex;
    private String renderedIndex;
    private String datasource;
    private boolean bulk;
    private Integer bulkItemIndex;
}
