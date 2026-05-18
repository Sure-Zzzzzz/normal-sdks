package io.github.surezzzzzz.sdk.elasticsearch.search.core.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 执行上下文基类
 * <p>
 * 包含查询/聚合执行后的上下文信息，随 {@link io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsQueryEvent}
 * 和 {@link io.github.surezzzzzz.sdk.elasticsearch.search.core.event.EsAggEvent} 一起发布，供审计/监控扩展使用。
 * <p>
 * {@link QueryExecutionContext} 和 {@link AggExecutionContext} 均继承此类，现有监听器代码无需修改。
 *
 * @author surezzzzzz
 * @since 1.0.9
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionContext {

    /**
     * 实际查询的物理索引列表
     */
    private String[] actualIndices;

    /**
     * 数据源 key
     */
    private String datasource;

    /**
     * 降级级别（0 = 未降级，1~3 = 降级程度递增）
     */
    private int downgradeLevel;

    /**
     * 来源类型，由 starter 端点在调用 executor 前设置
     * 取值参考 starter 中的 SourceType 常量类
     * 例如：QUERY_API / NL_API / EXPRESSION_API
     */
    private String sourceType;
}
