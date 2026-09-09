package io.github.surezzzzzz.sdk.auth.iam.server.dto.response;

import lombok.Data;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 管理端分页响应
 *
 * @author surezzzzzz
 */
@Data
public class AdminPageResponse<T> {

    /**
     * 当前页数据
     */
    private List<T> content;

    /**
     * 总记录数
     */
    private long totalElements;

    /**
     * 总页数
     */
    private int totalPages;

    /**
     * 当前页码，从 1 开始
     */
    private int page;

    /**
     * 每页大小
     */
    private int size;

    /**
     * 当前页记录数
     */
    private int numberOfElements;

    /**
     * 是否首页
     */
    private boolean first;

    /**
     * 是否末页
     */
    private boolean last;

    /**
     * 是否为空
     */
    private boolean empty;

    /**
     * 从 Spring Data 分页结果转换为管理端分页响应
     *
     * @param source Spring Data 分页结果
     * @param <T>    数据类型
     * @return 管理端分页响应
     */
    public static <T> AdminPageResponse<T> from(Page<T> source) {
        AdminPageResponse<T> response = new AdminPageResponse<T>();
        response.setContent(source.getContent());
        response.setTotalElements(source.getTotalElements());
        response.setTotalPages(source.getTotalPages());
        response.setPage(source.getNumber() + 1);
        response.setSize(source.getSize());
        response.setNumberOfElements(source.getNumberOfElements());
        response.setFirst(source.isFirst());
        response.setLast(source.isLast());
        response.setEmpty(source.isEmpty());
        return response;
    }
}
