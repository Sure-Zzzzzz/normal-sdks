package io.github.surezzzzzz.sdk.auth.aksk.server.controller.response;

import lombok.Data;

import java.util.List;

/**
 * Page Response
 * 通用分页响应
 *
 * @param <T> 数据类型
 * @author surezzzzzz
 */
@Data
public class PageResponse<T> {
    /**
     * 当前页的数据列表
     */
    private List<T> data;

    /**
     * 总记录数
     */
    private Long total;

    /**
     * 当前页码（从1开始）
     */
    private Integer page;

    /**
     * 每页大小
     */
    private Integer size;

    /**
     * 总页数
     */
    private Integer totalPages;

    public PageResponse() {
    }

    public PageResponse(List<T> data, Long total, Integer page, Integer size) {
        this.data = data;
        this.total = total;
        this.page = page;
        this.size = size;
        this.totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
    }

    /**
     * 创建分页响应
     *
     * @param data 当前页的数据列表
     * @param total 总记录数
     * @param page 当前页码
     * @param size 每页大小
     * @param <T> 数据类型
     * @return 分页响应
     */
    public static <T> PageResponse<T> of(List<T> data, Long total, Integer page, Integer size) {
        return new PageResponse<>(data, total, page, size);
    }

    /**
     * 从Spring Data Page创建分页响应（页码从0转换为从1开始）
     *
     * @param page Spring Data分页对象
     * @param <T> 数据类型
     * @return 分页响应
     */
    public static <T> PageResponse<T> fromPage(org.springframework.data.domain.Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getNumber() + 1,  // Spring Data页码从0开始，转换为从1开始
                page.getSize()
        );
    }
}
