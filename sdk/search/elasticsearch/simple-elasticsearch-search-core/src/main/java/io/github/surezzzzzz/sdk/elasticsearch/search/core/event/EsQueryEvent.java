package io.github.surezzzzzz.sdk.elasticsearch.search.core.event;

import io.github.surezzzzzz.sdk.elasticsearch.search.core.model.QueryExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryRequest;
import io.github.surezzzzzz.sdk.elasticsearch.search.query.model.QueryResponse;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * ES 查询事件
 *
 * @author surezzzzzz
 * @since 1.0.1
 */
@Getter
public class EsQueryEvent extends ApplicationEvent {

    private final QueryRequest request;
    private final QueryResponse response;
    private final QueryExecutionContext context;

    public EsQueryEvent(Object source, QueryRequest request,
                        QueryResponse response, QueryExecutionContext context) {
        super(source);
        this.request = request;
        this.response = response;
        this.context = context;
    }
}
