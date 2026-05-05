package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.PaginationType;
import io.github.surezzzzzz.sdk.naturallanguage.parser.constant.SearchAfterMode;
import lombok.*;

import java.util.List;

/**
 * 分页意图
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaginationIntent {

    /**
     * 分页类型
     */
    private PaginationType type;

    /**
     * 页码（从1开始，offset 分页使用）
     */
    private Integer page;

    /**
     * 每页大小
     */
    private Integer size;

    /**
     * 偏移量
     */
    private Long offset;

    /**
     * search_after 值
     */
    private List<Object> searchAfter;

    /**
     * PIT ID
     */
    private String pitId;

    /**
     * PIT 保持活跃时间
     */
    private String pitKeepAlive;

    /**
     * search_after 模式
     */
    private SearchAfterMode searchAfterMode;
}
