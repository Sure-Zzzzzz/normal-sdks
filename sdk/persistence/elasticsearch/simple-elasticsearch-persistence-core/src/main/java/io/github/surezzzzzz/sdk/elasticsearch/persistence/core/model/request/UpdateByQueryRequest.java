package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.ByQueryOptions;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.query.PersistenceQuery;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Update By Query Request
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class UpdateByQueryRequest extends PersistenceRequest {

    private static final long serialVersionUID = 1L;

    /** 目标索引。 */
    private String index;
    /** 查询条件。 */
    private PersistenceQuery query;
    /** Painless 脚本。 */
    private String scriptSource;
    /** 脚本参数。 */
    private Map<String, Object> scriptParamMap;
    /** ByQuery 执行选项。 */
    private ByQueryOptions options;
}
