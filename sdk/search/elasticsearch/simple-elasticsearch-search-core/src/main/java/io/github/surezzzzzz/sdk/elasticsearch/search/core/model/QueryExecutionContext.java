package io.github.surezzzzzz.sdk.elasticsearch.search.core.model;

import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * 查询执行上下文
 * <p>
 * 继承自 {@link ExecutionContext}，随 EsQueryEvent 发布。
 * 现有监听器代码无需修改，可直接通过父类字段获取 downgradeLevel、sourceType 等新增信息。
 *
 * @author surezzzzzz
 * @since 1.0.0
 */
@SuperBuilder
@NoArgsConstructor
public class QueryExecutionContext extends ExecutionContext {

    public QueryExecutionContext(String[] actualIndices, String datasource) {
        super(actualIndices, datasource, 0, null);
    }

    public QueryExecutionContext(String[] actualIndices, String datasource,
                                 int downgradeLevel, String sourceType) {
        super(actualIndices, datasource, downgradeLevel, sourceType);
    }
}
