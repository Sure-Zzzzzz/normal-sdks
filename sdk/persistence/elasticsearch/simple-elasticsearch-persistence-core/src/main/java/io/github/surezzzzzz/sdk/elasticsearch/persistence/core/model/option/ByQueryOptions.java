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

    /** 是否等待任务完成。 */
    private Boolean waitForCompletion;
    /** 批处理大小。 */
    private Integer batchSize;
    /** 滚动查询大小。 */
    private Integer scrollSize;
    /** 并行切片数。 */
    private Integer slices;
    /** 版本冲突策略。 */
    private String conflicts;
}
