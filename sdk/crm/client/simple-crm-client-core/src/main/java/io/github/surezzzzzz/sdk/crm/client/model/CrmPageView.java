package io.github.surezzzzzz.sdk.crm.client.model;

import java.util.Collections;
import java.util.List;

/**
 * CRM 机器 wire 列表页：items + 不透明 nextCursor（null=末页）。
 *
 * <p>不承载管理台的 page/pageSize/total 响应；消费者完整列表的 nextCursor 为空。</p>
 *
 * @author surezzzzzz
 */
public final class CrmPageView {

    private final List<CrmResourceView> items;
    private final String nextCursor;

    /**
     * 创建列表页。
     *
     * @param items      当页行
     * @param nextCursor 下一页游标（可空）
     */
    public CrmPageView(List<CrmResourceView> items, String nextCursor) {
        this.items = Collections.unmodifiableList(items);
        this.nextCursor = nextCursor;
    }

    /**
     * 当页行。
     *
     * @return 不可变列表
     */
    public List<CrmResourceView> getItems() {
        return items;
    }

    /**
     * 是否还有下一页。
     *
     * @return 有下一页返回 true
     */
    public boolean hasNext() {
        return nextCursor != null;
    }

    /**
     * 下一页游标。
     *
     * @return 游标或 null
     */
    public String getNextCursor() {
        return nextCursor;
    }
}
