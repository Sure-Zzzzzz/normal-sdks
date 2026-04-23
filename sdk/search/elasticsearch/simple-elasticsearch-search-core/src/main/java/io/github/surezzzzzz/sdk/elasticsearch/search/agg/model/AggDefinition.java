package io.github.surezzzzzz.sdk.elasticsearch.search.agg.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.AggType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 聚合定义
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class AggDefinition {

    /**
     * 聚合名称（结果中的 key）
     */
    private String name;

    /**
     * 聚合类型
     */
    private String type;

    /**
     * 聚合字段
     */
    private String field;

    /**
     * Bucket 聚合的大小（仅 terms、date_histogram 等）
     */
    private Integer size;

    /**
     * 时间间隔（仅 date_histogram）
     * 如：day、week、month、year
     */
    private String interval;

    /**
     * 范围列表（仅 range 聚合）
     */
    private List<Range> ranges;

    /**
     * 是否使用 composite 聚合（支持翻页）
     * 仅对 terms、date_histogram、histogram 生效
     * 默认 null/false，不影响现有行为
     */
    private Boolean composite;

    /**
     * composite 聚合的排序方向（asc / desc）
     * 按分组字段值排序，不能按 doc_count 排序
     * 默认 asc
     */
    private String order;

    /**
     * 嵌套聚合（支持多层嵌套）
     */
    private List<AggDefinition> aggs;

    /**
     * Pipeline 聚合列表
     * 作用于当前 bucket 聚合的结果，在 ES 中作为 sub-aggregation 发送。
     * 仅对普通 bucket 聚合有效（terms、date_histogram、histogram、range 等），
     * composite 聚合下不允许使用（ES 不支持）。
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<PipelineAggDefinition> pipelineAggs;

    /**
     * 获取聚合类型枚举
     */
    @JsonIgnore
    public AggType getTypeEnum() {
        return AggType.fromString(type);
    }

    /**
     * 范围定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Range {
        private Object from;
        private Object to;
        private String key;
    }

    /**
     * 初始化集合
     */
    public static class AggDefinitionBuilder {
        public AggDefinitionBuilder() {
            this.aggs = new ArrayList<>();
            this.ranges = new ArrayList<>();
            this.pipelineAggs = new ArrayList<>();
        }
    }
}
