package io.github.surezzzzzz.sdk.auth.aksk.openapi.client.model;

import java.util.List;

/**
 * 分页响应（server 通用分页 wire 形态）。
 *
 * @param <T> 数据元素类型
 * @author surezzzzzz
 */
public class PageResponse<T> {

    private List<T> data;

    private Long total;

    private Integer page;

    private Integer size;

    private Integer totalPages;

    public List<T> getData() {
        return data;
    }

    public void setData(List<T> data) {
        this.data = data;
    }

    public Long getTotal() {
        return total;
    }

    public void setTotal(Long total) {
        this.total = total;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public Integer getTotalPages() {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages) {
        this.totalPages = totalPages;
    }
}
