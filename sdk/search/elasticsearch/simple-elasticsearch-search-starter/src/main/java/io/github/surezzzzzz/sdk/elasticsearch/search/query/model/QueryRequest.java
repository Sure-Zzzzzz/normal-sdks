package io.github.surezzzzzz.sdk.elasticsearch.search.query.model;

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
     * 初始化集合
     */
    public static class QueryRequestBuilder {
        public QueryRequestBuilder() {
            this.fields = new ArrayList<>();
        }
    }
}
