package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import lombok.*;

/**
 * 字段折叠意图
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollapseIntent {

    /**
     * 字段提示
     */
    private String fieldHint;

    /**
     * 最大并发组搜索数
     */
    private Integer maxConcurrentGroupSearches;
}
