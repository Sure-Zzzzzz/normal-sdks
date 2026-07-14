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

    /**
     * 是否等待任务完成。
     */
    private Boolean waitForCompletion;
    /**
     * 批处理大小。
     */
    private Integer batchSize;
    /**
     * 滚动查询大小。
     */
    private Integer scrollSize;
    /**
     * 并行切片数。
     */
    private Integer slices;
    /**
     * 版本冲突策略。
     */
    private String conflicts;
    /**
     * 每秒请求数节流参数，null 表示不限制。ES 默认不限制（传 -1 或不传），可设正数如 500f，或 0 暂停。
     */
    private Float requestsPerSecond;
    /**
     * 限制处理的最大文档数，超出后任务停止。0 表示处理 0 个文档。
     */
    private Long maxDocs;
    /**
     * 开始执行前要求的活跃分片数。0 表示要求全部必需分片就绪，比默认（1 个分片）更严格。
     */
    private Integer waitForActiveShards;
}
