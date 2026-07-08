package io.github.surezzzzzz.sdk.elasticsearch.persistence.executor;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.annotation.SimpleElasticsearchPersistenceComponent;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorCode;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.exception.SimpleElasticsearchPersistenceException;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.PersistenceRequest;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persistence Executor Registry
 *
 * @author surezzzzzz
 */
@SimpleElasticsearchPersistenceComponent
public class PersistenceExecutorRegistry {

    private final Map<Class<? extends PersistenceRequest>, PersistenceExecutor<?, ?>> executorMap;

    public PersistenceExecutorRegistry(List<PersistenceExecutor<?, ?>> executorList) {
        this.executorMap = new ConcurrentHashMap<>();
        for (PersistenceExecutor<?, ?> executor : executorList) {
            executorMap.put(executor.getRequestType(), executor);
        }
    }

    @SuppressWarnings("unchecked")
    public <Req extends PersistenceRequest, Res> PersistenceExecutor<Req, Res> find(Req request) {
        PersistenceExecutor<?, ?> executor = executorMap.get(request.getClass());
        if (executor == null) {
            throw new SimpleElasticsearchPersistenceException(
                    ErrorCode.EXECUTOR_NOT_FOUND,
                    String.format(ErrorMessage.EXECUTOR_NOT_FOUND, request.getClass().getName()));
        }
        return (PersistenceExecutor<Req, Res>) executor;
    }
}
