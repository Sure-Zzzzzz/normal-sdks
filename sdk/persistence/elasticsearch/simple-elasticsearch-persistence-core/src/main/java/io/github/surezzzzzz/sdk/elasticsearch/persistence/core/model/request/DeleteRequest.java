package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option.DeleteOptions;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Delete Request
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = false)
public class DeleteRequest extends PersistenceRequest {

    private static final long serialVersionUID = 1L;

    private String index;
    private String id;
    private Class<?> documentClass;
    private DeleteOptions options;
}
