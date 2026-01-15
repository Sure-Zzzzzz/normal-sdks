package io.github.surezzzzzz.sdk.elasticsearch.search.query.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询请求
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class QueryRequest {

    /**
     * 索引别名
     */
    private String index;

    /**
     * 日期范围（仅日期分割索引）
     */
    private DateRange dateRange;

    /**
     * 查询条件
     */
    private QueryCondition query;

    /**
     * 分页信息
     */
    private PaginationInfo pagination;

    /**
     * 字段投影（只返回指定字段）
     */
    private List<String> fields;

    /**
     * 字段折叠（去重）
     * 按指定字段折叠，每个唯一值只返回一条文档
     */
    private CollapseField collapse;

    /**
     * 日期范围
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DateRange {
        /**
         * 开始日期
         */
        private String from;

        /**
         * 结束日期
         */
        private String to;
    }

    /**
     * 字段折叠（去重）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollapseField {
        /**
         * 折叠字段名
         */
        private String field;

        /**
         * 每个折叠组返回的最大内部命中数（可选，用于查看折叠的其他文档）
         */
        private Integer maxConcurrentGroupSearches;
    }

    /**
     * 初始化集合
     */
    public static class QueryRequestBuilder {
        public QueryRequestBuilder() {
            this.fields = new ArrayList<>();
        }
    }
}
