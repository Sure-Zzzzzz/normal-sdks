package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.IndexOptions;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Index Request
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class IndexRequest extends PersistenceRequest {

    private static final long serialVersionUID = 1L;

    /** 写入文档。 */
    private Object document;
    /** 目标索引。 */
    private String index;
    /** 文档 ID。 */
    private String id;
    /** Index 写入选项。 */
    private IndexOptions options;
}
