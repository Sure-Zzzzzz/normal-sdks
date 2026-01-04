package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.IntentType;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

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
     * 时间范围过滤
     */
    private DateRangeIntent dateRange;

    /**
     * 字段投影（只返回指定字段）
     */
    @Builder.Default
    private List<String> fieldHints = new ArrayList<>();

    /**
     * 是否包含条件
     */
    public boolean hasCondition() {
        return condition != null;
    }

    /**
     * 是否包含排序
     */
    public boolean hasSort() {
        return sorts != null && !sorts.isEmpty();
    }

    /**
     * 是否包含分页
     */
    public boolean hasPagination() {
        return pagination != null;
    }

    /**
     * 是否包含时间范围过滤
     */
    public boolean hasDateRange() {
        return dateRange != null && dateRange.isValid();
    }

    /**
     * 初始化
     */
    public QueryIntent() {
        super(IntentType.QUERY);
        this.sorts = new ArrayList<>();
        this.fieldHints = new ArrayList<>();
    }

    /**
     * 构造函数
     */
    public QueryIntent(ConditionIntent condition, List<SortIntent> sorts,
                       PaginationIntent pagination, List<String> fieldHints) {
        super(IntentType.QUERY);
        this.condition = condition;
        this.sorts = sorts != null ? sorts : new ArrayList<>();
        this.pagination = pagination;
        this.dateRange = null;
        this.fieldHints = fieldHints != null ? fieldHints : new ArrayList<>();
    }

    /**
     * 完整构造函数（包含dateRange）
     */
    public QueryIntent(ConditionIntent condition, List<SortIntent> sorts,
                       PaginationIntent pagination, DateRangeIntent dateRange,
                       List<String> fieldHints) {
        super(IntentType.QUERY);
        this.condition = condition;
        this.sorts = sorts != null ? sorts : new ArrayList<>();
        this.pagination = pagination;
        this.dateRange = dateRange;
        this.fieldHints = fieldHints != null ? fieldHints : new ArrayList<>();
    }
}
