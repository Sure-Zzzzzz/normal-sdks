package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.IndexOperationType;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Index Options
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class IndexOptions extends WriteOptions {

    private IndexOperationType operationType;
}
