package io.github.surezzzzzz.sdk.naturallanguage.parser.model;

import lombok.*;

/**
 * 聚合范围提示
 *
 * @author surezzzzzz
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggRangeHint {

    /**
     * 起始值
     */
    private Object from;

    /**
     * 结束值
     */
    private Object to;

    /**
     * 范围名称
     */
    private String key;
}
