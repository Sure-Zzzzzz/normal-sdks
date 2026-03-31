package io.github.surezzzzzz.sdk.elasticsearch.search.core.event;

import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.agg.model.AggResponse;
import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.AggExecutionContext;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * ES 聚合事件
 *
 * @author surezzzzzz
 * @since 1.0.1
 */
@Getter
public class EsAggEvent extends ApplicationEvent {

    private final AggRequest request;
    private final AggResponse response;
    private final AggExecutionContext context;

    public EsAggEvent(Object source, AggRequest request,
                      AggResponse response, AggExecutionContext context) {
        super(source);
        this.request = request;
        this.response = response;
        this.context = context;
    }
}
