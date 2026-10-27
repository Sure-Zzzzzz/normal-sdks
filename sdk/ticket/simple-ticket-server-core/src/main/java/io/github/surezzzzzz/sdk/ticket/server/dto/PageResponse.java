package io.github.surezzzzzz.sdk.ticket.server.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页响应（content+page+size；不返回 total——列表按加载更多/游标形态消费）。
 *
 * @param <T> 内容类型
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResponse<T> {

    /**
     * 内容
     */
    @Builder.Default
    private List<T> content = new ArrayList<>();

    /**
     * 页码（1 起）
     */
    private int page;

    /**
     * 页大小
     */
    private int size;
}
