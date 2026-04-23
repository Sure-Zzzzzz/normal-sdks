package io.github.surezzzzzz.sdk.elasticsearch.search.agg.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * Pipeline 聚合定义
 * 作用于父 bucket 聚合的结果，在 ES 中作为 sub-aggregation 发送。
 * 仅支持 bucket_sort 和 bucket_selector 两种类型。
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PipelineAggDefinition {

    /**
     * 聚合名称（结果中的 key）
     */
    private String name;

    /**
     * 聚合类型：bucket_sort / bucket_selector
     */
    private String type;

    /**
     * 排序字段（bucket_sort 专用）
     * key：同级 metrics agg 的 name，value：asc / desc
     */
    private Map<String, String> sort;

    /**
     * 返回的 bucket 数量 Top N（bucket_sort 专用）
     * 不填则不限制数量，仅排序
     */
    private Integer size;

    /**
     * 跳过的 bucket 数量（bucket_sort 专用），默认 0
     */
    private Integer from;

    /**
     * 过滤脚本 Painless（bucket_selector 专用）
     * 示例："params.totalSales > 100 && params.avgPrice < 500"
     */
    private String script;

    /**
     * buckets_path 映射（bucket_selector 专用，可选）
     * key：script 中使用的变量名，value：引用的同级聚合名称
     * 不填时自动从 script 中提取 params.xxx 变量名，直接映射为同名聚合
     */
    private Map<String, String> bucketsPath;
}
