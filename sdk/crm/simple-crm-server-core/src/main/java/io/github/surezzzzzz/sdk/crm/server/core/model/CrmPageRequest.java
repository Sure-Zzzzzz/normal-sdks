package io.github.surezzzzzz.sdk.crm.server.core.model;

import io.github.surezzzzzz.sdk.crm.server.core.error.CrmException;
import lombok.Getter;

/**
 * 管理台页码查询：页码从一开始，总数与数据使用相同的过滤及授权范围。
 *
 * @author surezzzzzz
 */
@Getter
public final class CrmPageRequest {

    /**
     * 默认页码。
     */
    public static final int DEFAULT_PAGE = 1;
    /**
     * 默认每页条数。
     */
    public static final int DEFAULT_PAGE_SIZE = 20;
    /**
     * 单页最大条数。
     */
    public static final int MAX_PAGE_SIZE = 100;
    /**
     * 页码。
     */
    private final int page;
    /**
     * 每页条数。
     */
    private final int pageSize;

    /**
     * 创建页码请求，拒绝非法页码和条数，不静默截断参数。
     *
     * @param page     页码
     * @param pageSize 每页条数
     */
    public CrmPageRequest(int page, int pageSize) {
        if (page < DEFAULT_PAGE || pageSize < DEFAULT_PAGE || pageSize > MAX_PAGE_SIZE) {
            throw CrmException.validation("pagination");
        }
        this.page = page;
        this.pageSize = pageSize;
    }

    /**
     * 获取数据库偏移量，以长整数避免大页码乘法溢出。
     *
     * @return 跳过的记录数
     */
    public long offset() {
        return ((long) page - DEFAULT_PAGE) * pageSize;
    }
}
