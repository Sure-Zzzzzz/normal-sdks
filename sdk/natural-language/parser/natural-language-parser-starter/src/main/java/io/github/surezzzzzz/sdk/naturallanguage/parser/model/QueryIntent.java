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
        this.fieldHints = fieldHints != null ? fieldHints : new ArrayList<>();
    }
}
