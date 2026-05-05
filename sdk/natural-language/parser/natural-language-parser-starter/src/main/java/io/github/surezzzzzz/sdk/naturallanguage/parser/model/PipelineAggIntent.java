package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.AggType;
import lombok.*;

/**
 * Pipeline 聚合意图
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PipelineAggIntent {

    /**
     * 聚合名称提示
     */
    private String nameHint;

    /**
     * Pipeline 聚合类型（BUCKET_SORT / BUCKET_SELECTOR）
     */
    private AggType type;

    /**
     * 排序方向（用于 bucket_sort）
     */
    private String sortField;

    /**
     * 排序方向（用于 bucket_sort）
     */
    private String sortOrder;

    /**
     * 大小限制（用于 bucket_sort，如 Top N）
     */
    private Integer size;

    /**
     * 条件脚本（用于 bucket_selector，如 HAVING 条件）
     */
    private String script;
}
