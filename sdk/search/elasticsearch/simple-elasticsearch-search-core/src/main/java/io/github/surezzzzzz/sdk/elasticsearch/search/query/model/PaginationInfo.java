package io.github.surezzzzzz.sdk.elasticsearch.search.query.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.PaginationType;
import io.github.surezzzzzz.sdk.elasticsearch.search.constant.SearchAfterMode;
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
     * search_after 翻页模式（仅 search_after 分页生效）
     * tiebreaker（默认）：自动追加 _id ASC 作为 tiebreaker，兼容 v1.2.1 行为
     * pit             ：使用 Point In Time 快照翻页，需要 ES 7.10+，不追加 _id
     * none            ：不追加任何 tiebreaker，由调用方自己保证排序字段唯一性
     */
    private String searchAfterMode;

    /**
     * PIT ID（searchAfterMode=pit 时使用）
     * 第一次请求不传，库自动 open PIT 并在响应中返回；后续翻页将响应中的 pitId 带回即可
     */
    private String pitId;

    /**
     * PIT 保活时间（searchAfterMode=pit 时必填，如 "1m"、"5m"）
     * 含义：两次翻页之间的最长空闲时间，每次查询自动续期
     * 不能超过服务端 pit.max-keep-alive 配置，超过则报错
     */
    private String pitKeepAlive;

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
     * 获取 search_after 翻页模式枚举，默认 TIEBREAKER
     */
    @JsonIgnore
    public SearchAfterMode getSearchAfterModeEnum() {
        return SearchAfterMode.fromCode(searchAfterMode);
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
