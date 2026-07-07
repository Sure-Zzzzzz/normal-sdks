package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * By Query Options
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class ByQueryOptions extends WriteOptions {

    private Boolean waitForCompletion;
    private Integer batchSize;
    private Integer scrollSize;
    private Integer slices;
    private String conflicts;
}
