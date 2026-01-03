package io.github.surezzzzzz.sdk.elasticsearch.search.query.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.PaginationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页信息
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class PaginationInfo {

    /**
     * 分页类型：offset / search_after
     */
    private String type;

    /**
     * 页码（offset 分页用，从 1 开始）
     */
    private Integer page;

    /**
     * 每页大小
     */
    private Integer size;

    /**
     * search_after 参数（search_after 分页用）
     */
    private List<Object> searchAfter;

    /**
     * 排序字段（search_after 必须有排序）
     */
    private List<SortField> sort;

    /**
     * 获取分页类型枚举
     */
    @JsonIgnore
    public PaginationType getTypeEnum() {
        return PaginationType.fromString(type);
    }

    /**
     * 是否为 offset 分页
     */
    @JsonIgnore
    public boolean isOffsetPagination() {
        return PaginationType.OFFSET == getTypeEnum();
    }

    /**
     * 是否为 search_after 分页
     */
    @JsonIgnore
    public boolean isSearchAfterPagination() {
        return PaginationType.SEARCH_AFTER == getTypeEnum();
    }

    /**
     * 排序字段
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SortField {
        /**
         * 字段名
         */
        private String field;

        /**
         * 排序方向：asc / desc
         */
        private String order;
    }
}
