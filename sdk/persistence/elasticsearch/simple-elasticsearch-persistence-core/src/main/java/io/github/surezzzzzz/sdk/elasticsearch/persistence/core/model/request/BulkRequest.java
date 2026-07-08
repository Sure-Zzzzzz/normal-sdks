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

    /** Bulk item 列表。 */
    private List<BulkItem> itemList;
    /** 默认目标索引。 */
    private String defaultIndex;
    /** Bulk 写入选项。 */
    private BulkOptions options;
}
