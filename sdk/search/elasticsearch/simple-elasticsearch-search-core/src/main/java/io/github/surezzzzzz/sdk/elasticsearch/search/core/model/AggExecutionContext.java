package io.github.surezzzzzz.sdk.elasticsearch.search.core.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 聚合执行上下文
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AggExecutionContext {
    /**
     * 实际查询的物理索引
     */
    private String[] actualIndices;

    /**
     * 数据源
     */
    private String datasource;
}
