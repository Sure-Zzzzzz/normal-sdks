package io.github.surezzzzzz.sdk.crm.server.core.model;

import io.github.surezzzzzz.sdk.crm.server.core.support.CrmValidationHelper;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 列表查询页：机器游标分页或管理台真实页码分页结果。
 *
 * <p>游标为不透明字符串（服务端编码排序键与过滤指纹）；nextCursor 为 null 表示没有更多数据；
 * 过滤条件或授权范围变更后旧游标失效，由实现返回校验错误。
 * 页码模式返回 page、pageSize、total，nextCursor 为空；total 与当页行来自相同读取快照。</p>
 *
 * @param <T> 行事实类型
 * @author surezzzzzz
 */
@Getter
public final class CrmPage<T> {

    private final List<T> items;
    private final String nextCursor;
    /**
     * 页码模式的当前页，游标模式为空。
     */
    private final Integer page;
    /**
     * 页码模式的每页条数，游标模式为空。
     */
    private final Integer pageSize;
    /**
     * 相同过滤与授权范围内的真实总数，游标模式为空。
     */
    private final Long total;

    /**
     * 创建游标分页结果。
     *
     * @param items      当页行事实集合（可为空集合，不可为 null）
     * @param nextCursor 下一页游标（null 表示末页）
     */
    public CrmPage(List<T> items, String nextCursor) {
        if (items == null || items.contains(null)) {
            throw new IllegalArgumentException("items");
        }
        this.items = Collections.unmodifiableList(new ArrayList<T>(items));
        this.nextCursor = CrmValidationHelper.optional(nextCursor);
        this.page = null;
        this.pageSize = null;
        this.total = null;
    }

    /**
     * 创建真实页码结果，不携带游标。
     *
     * @param items   当页行
     * @param request 页码请求
     * @param total   相同过滤和权限下的总数
     */
    public CrmPage(List<T> items, CrmPageRequest request, long total) {
        if (items == null || items.contains(null) || request == null || total < 0
                || items.size() > request.getPageSize() || items.size() > total
                || !items.isEmpty() && request.offset() + items.size() > total) {
            throw new IllegalArgumentException("pagination");
        }
        this.items = Collections.unmodifiableList(new ArrayList<T>(items));
        this.nextCursor = null;
        this.page = request.getPage();
        this.pageSize = request.getPageSize();
        this.total = total;
    }

    /**
     * 是否还有下一页。
     *
     * @return 还有下一页返回 true
     */
    public boolean hasNext() {
        if (total != null) {
            long offset = ((long) page - CrmPageRequest.DEFAULT_PAGE) * pageSize;
            return offset + items.size() < total;
        }
        return nextCursor != null;
    }
}
