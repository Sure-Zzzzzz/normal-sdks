package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option;

import lombok.Data;
import lombok.experimental.SuperBuilder;

/**
 * Write Options
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
public class WriteOptions {

    private Boolean refresh;
    private String routing;
    private Long timeoutMs;
}
