package io.github.surezzzzzz.sdk.elasticsearch.persistence.core.event;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.PersistenceExecutionContext;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.PersistenceRequest;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * Elasticsearch Persistence Error Event
 *
 * @author surezzzzzz
 */
@Getter
public class EsPersistenceErrorEvent extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    private final PersistenceRequest request;
    private final Throwable error;
    private final PersistenceExecutionContext context;

    public EsPersistenceErrorEvent(Object source, PersistenceRequest request, Throwable error,
                                   PersistenceExecutionContext context) {
        super(source);
        this.request = request;
        this.error = error;
        this.context = context;
    }
}
