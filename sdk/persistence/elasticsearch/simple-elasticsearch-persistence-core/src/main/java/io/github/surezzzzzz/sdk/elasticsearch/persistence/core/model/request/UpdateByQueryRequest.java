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

    private String index;
    private PersistenceQuery query;
    private String scriptSource;
    private Map<String, Object> scriptParamMap;
    private ByQueryOptions options;
}
