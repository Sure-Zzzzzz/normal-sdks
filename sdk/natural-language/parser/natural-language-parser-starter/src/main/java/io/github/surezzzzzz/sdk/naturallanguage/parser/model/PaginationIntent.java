package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 分页意图
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationIntent {

    /**
     * 页码（从 1 开始）
     */
    private Integer page;

    /**
     * 每页大小
     */
    private Integer size;

    /**
     * 偏移量
     */
    private Integer offset;

    /**
     * 限制数量
     */
    private Integer limit;

    /**
     * search_after 游标值（用于ES深度分页）
     * 通常是一个List，对应排序字段的值
     * 例如：["2024-01-01T12:00:00", "user_123"]
     */
    private List<Object> searchAfter;

    /**
     * 是否为续查请求（配合searchAfter使用）
     * true表示"继续上次查询"
     */
    private Boolean continueSearch;
}
