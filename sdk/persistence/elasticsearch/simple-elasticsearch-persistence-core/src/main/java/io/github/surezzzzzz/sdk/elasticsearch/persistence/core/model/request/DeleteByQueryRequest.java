package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.ByQueryOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query.PersistenceQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Delete By Query Request
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class DeleteByQueryRequest extends PersistenceRequest {

    private static final long serialVersionUID = 1L;

    /** 目标索引。 */
    private String index;
    /** 查询条件。 */
    private PersistenceQuery query;
    /** ByQuery 执行选项。 */
    private ByQueryOptions options;
}
