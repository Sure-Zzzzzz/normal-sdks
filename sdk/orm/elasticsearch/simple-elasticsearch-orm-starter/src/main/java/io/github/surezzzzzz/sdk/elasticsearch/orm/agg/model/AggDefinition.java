package io.github.surezzzzzz.sdk.elasticsearch.orm.agg.model;

import io.github.surezzzzzz.sdk.elasticsearch.orm.constant.AggType;
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
     * 嵌套聚合（支持多层嵌套）
     */
    private List<AggDefinition> aggs;

    /**
     * 获取聚合类型枚举
     */
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
        }
    }
}
