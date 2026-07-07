package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.event;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.PersistenceRequest;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Elasticsearch Persistence Event
 *
 * @author surezzzzzz
 */
@Getter
public class EsPersistenceEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    private final PersistenceRequest request;
    private final Object result;
    private final PersistenceExecutionContext context;

    public EsPersistenceEvent(Object source, PersistenceRequest request, Object result,
                              PersistenceExecutionContext context) {
        super(source);
        this.request = request;
        this.result = result;
        this.context = context;
    }
}
