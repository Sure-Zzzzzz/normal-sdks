package io.github.surezzzzzz.sdk.elasticsearch.orm.query.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 查询响应
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryResponse {

    /**
     * 总数
     */
    private Long total;

    /**
     * 当前页
     */
    private Integer page;

    /**
     * 每页大小
     */
    private Integer size;

    /**
     * 数据列表
     */
    private List<Map<String, Object>> items;

    /**
     * 分页信息
     */
    private PaginationResult pagination;

    /**
     * 查询耗时（毫秒）
     */
    private Long took;

    /**
     * 分页结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PaginationResult {
        /**
         * 分页类型
         */
        private String type;

        /**
         * 是否有更多数据
         */
        private Boolean hasMore;

        /**
         * 下一次 search_after 参数
         */
        private List<Object> nextSearchAfter;
    }

    /**
     * 初始化集合
     */
    public static class QueryResponseBuilder {
        public QueryResponseBuilder() {
            this.items = new ArrayList<>();
        }
    }
}
