package io.github.surezzzzzz.sdk.elasticsearch.persistence.executor;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.PersistenceRequest;

/**
 * Persistence Executor
 *
 * @author surezzzzzz
 */
public interface PersistenceExecutor<Req extends PersistenceRequest, Res> {

    Class<Req> getRequestType();

    Res execute(Req request);
}
