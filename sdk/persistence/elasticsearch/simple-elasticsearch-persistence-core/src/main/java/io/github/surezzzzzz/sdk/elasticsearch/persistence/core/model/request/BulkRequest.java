package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.BulkOptions;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * Bulk Request
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class BulkRequest extends PersistenceRequest {

    private static final long serialVersionUID = 1L;

    private List<BulkItem> itemList;
    private String defaultIndex;
    private BulkOptions options;
}
