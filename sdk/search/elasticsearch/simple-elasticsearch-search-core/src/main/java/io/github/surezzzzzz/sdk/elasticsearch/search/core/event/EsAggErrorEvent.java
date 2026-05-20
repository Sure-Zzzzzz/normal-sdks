package io.github.surezzzzzz.sdk.elasticsearch.search.core.event;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
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
     * 请求来源类型（QUERY_API / NL_API / EXPRESSION_API），取自 AggRequest.sourceType
     */
    private final String sourceType;

    public EsAggErrorEvent(Object source, AggRequest request,
                           Throwable error, String datasource) {
        super(source);
        this.request = request;
        this.error = error;
        this.datasource = datasource;
        this.sourceType = request != null ? request.getSourceType() : null;
    }
}
