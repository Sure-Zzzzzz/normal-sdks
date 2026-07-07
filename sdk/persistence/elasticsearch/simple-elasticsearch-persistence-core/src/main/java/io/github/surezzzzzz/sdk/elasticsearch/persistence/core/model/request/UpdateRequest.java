package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.UpdateOptions;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * Update Request
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class UpdateRequest extends PersistenceRequest {

    private static final long serialVersionUID = 1L;

    private String index;
    private String id;
    private Map<String, Object> fieldMap;
    private String scriptSource;
    private Map<String, Object> scriptParamMap;
    private UpdateOptions options;
}
