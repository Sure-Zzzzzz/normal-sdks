package io.github.surezzzzzz.sdk.crm.server.support;

import java.util.Collections;
import java.util.ArrayList;
import lombok.Getter;
import java.util.List;

/**
 * 仓储分页载体：当页行 + 末行主键（keyset 续读锚点）。
 *
 * @param <T> 行事实类型
 * @author surezzzzzz
 */
@Getter
public final class SimpleCrmServerPaged<T> {

    private final List<T> items;
    private final Long lastPrimaryKey;
    /** 页码模式真实总数，游标模式为空。 */
    private final Long total;
    /** 游标模式是否实际读到额外行。 */
    private final boolean hasNext;

    /**
     * 创建分页载体。
     *
     * @param items 当页行
     * @param lastPrimaryKey 末行主键（空页为 null）
     */
    public SimpleCrmServerPaged(List<T> items, Long lastPrimaryKey) {
        this.items = Collections.unmodifiableList(items);
        this.lastPrimaryKey = lastPrimaryKey;
        this.total = null;
        this.hasNext = false;
    }

    /**
     * 从查询窗口构造分页结果，额外探测行不进入返回结果。
     *
     * @param rows 原查询窗口
     * @param primaryKeys 窗口对应的主键
     * @param limit 单页条数
     * @param total 页码模式总数，游标模式为空
     */
    public SimpleCrmServerPaged(List<T> rows, List<Long> primaryKeys, int limit, Long total) {
        int size = Math.min(rows.size(), limit);
        this.items = Collections.unmodifiableList(new ArrayList<T>(rows.subList(0, size)));
        this.lastPrimaryKey = size == 0 ? null : primaryKeys.get(size - 1);
        this.total = total;
        this.hasNext = total == null && rows.size() > limit;
    }

    /**
     * 当页行。
     *
     * @return 不可变列表
     */
    public List<T> getItems() {
        return items;
    }

    /**
     * 末行主键。
     *
     * @return 主键或 null
     */
    public Long getLastPrimaryKey() {
        return lastPrimaryKey;
    }

    /**
     * 是否满页（可能还有下一页）。
     *
     * @param limit 每页上限
     * @return 满页返回 true
     */
    public boolean isFull(int limit) {
        return items.size() >= limit;
    }
}
