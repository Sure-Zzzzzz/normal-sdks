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

    /**
     * 目标索引。
     */
    private String index;
    /**
     * 文档 ID。
     */
    private String id;
    /**
     * 文档类型。
     */
    private Class<?> documentClass;
    /**
     * Delete 写入选项。
     */
    private DeleteOptions options;
}
