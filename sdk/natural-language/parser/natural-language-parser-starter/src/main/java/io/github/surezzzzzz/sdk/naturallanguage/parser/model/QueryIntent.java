package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 查询意图
 *
 * @author surezzzzzz
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Builder
@AllArgsConstructor
public class QueryIntent extends Intent {

    /**
     * 查询条件
     */
    private ConditionIntent condition;

    /**
     * 排序
     */
    @Builder.Default
    private List<SortIntent> sorts = new ArrayList<>();

    /**
     * 分页
     */
    private PaginationIntent pagination;

    /**
     * 日期范围
     */
    private DateRangeIntent dateRange;

    /**
     * 字段折叠（去重）
     */
    private CollapseIntent collapse;

    /**
     * 字段投影（只返回哪些字段）
     */
    @Builder.Default
    private List<String> fieldProjections = new ArrayList<>();

    public QueryIntent() {
        super(IntentType.QUERY);
    }

    /**
     * 是否有查询条件
     *
     * @return true 有，false 无
     */
    public boolean hasCondition() {
        return condition != null;
    }

    /**
     * 是否有日期范围
     *
     * @return true 有，false 无
     */
    public boolean hasDateRange() {
        return dateRange != null;
    }

    /**
     * 是否有折叠
     *
     * @return true 有，false 无
     */
    public boolean hasCollapse() {
        return collapse != null;
    }

    /**
     * 是否有排序
     *
     * @return true 有，false 无
     */
    public boolean hasSort() {
        return sorts != null && !sorts.isEmpty();
    }

    /**
     * 是否有分页
     *
     * @return true 有，false 无
     */
    public boolean hasPagination() {
        return pagination != null;
    }
}
