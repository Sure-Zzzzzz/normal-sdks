package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.option;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

/**
 * Bulk Options
 *
 * @author surezzzzzz
 */
@Data
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class BulkOptions extends WriteOptions {

    /**
     * 分批大小。
     */
    private Integer batchSize;
    /**
     * 是否在存在 item 失败时继续提交后续批次。
     */
    private Boolean continueOnFailure;
    /**
     * Bulk 请求级 ingest pipeline。
     */
    private String pipeline;
}
