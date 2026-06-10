package io.github.surezzzzzz.sdk.elasticsearch.search.core.event;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.AggExecutionContext;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * ES 聚合失败事件
 * <p>
 * 当 AggExecutor 执行过程中抛出异常时发布，供审计/监控扩展使用。
 * 注意：仅在 executor 执行阶段失败时发布，端点层的参数校验失败（400）不触发此事件。
 *
 * @author surezzzzzz
 * @since 1.0.9
 */
@Getter
public class EsAggErrorEvent extends ApplicationEvent {

    private final AggRequest request;
    private final Throwable error;

    /**
     * 路由到的数据源 key，路由阶段前失败时为 null
     */
    private final String datasource;

    /**
     * 降级级别（0 = 未降级，1~3 = 降级程度递增）
     *
     * @since 1.0.11
     */
    private final int downgradeLevel;

    /**
     * 请求来源类型（QUERY_API / NL_API / EXPRESSION_API），取自 AggRequest.sourceType
     */
    private final String sourceType;

    /**
     * 执行上下文（路由成功后失败时有值，校验阶段/路由阶段失败时为 null）
     * <p>
     * 供下游监听器获取完整的执行上下文，未来新增字段扩展也加在此处。
     *
     * @since 1.0.11
     */
    private final AggExecutionContext context;

    public EsAggErrorEvent(Object source, AggRequest request,
                           Throwable error, String datasource) {
        this(source, request, error, datasource, 0, null);
    }

    /**
     * @since 1.0.11
     */
    public EsAggErrorEvent(Object source, AggRequest request,
                           Throwable error, String datasource,
                           int downgradeLevel,
                           AggExecutionContext context) {
        super(source);
        this.request = request;
        this.error = error;
        this.datasource = datasource;
        this.downgradeLevel = downgradeLevel;
        this.sourceType = request != null ? request.getSourceType() : null;
        this.context = context;
    }
}
