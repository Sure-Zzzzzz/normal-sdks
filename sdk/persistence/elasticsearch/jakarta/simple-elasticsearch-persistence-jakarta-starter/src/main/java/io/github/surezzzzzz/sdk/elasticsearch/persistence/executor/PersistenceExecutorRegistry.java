package io.github.surezzzzzz.sdk.elasticsearch.persistence.executor;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.PersistenceRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.PersistenceTargetResolver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 冻结的执行器注册表，重复或缺失注册均拒绝。
 */
public class PersistenceExecutorRegistry {
    private final Map<Class<?>, PersistenceExecutor<?, ?>> executors = new LinkedHashMap<>();

    /**
     * 登记执行器并拒绝重复请求类型。
     */
    public PersistenceExecutorRegistry(List<PersistenceExecutor<?, ?>> values) {
        for (PersistenceExecutor<?, ?> executor : values) {
            if (executors.putIfAbsent(executor.getRequestType(), executor) != null)
                throw PersistenceTargetResolver.invalid();
        }
    }

    /**
     * 按精确请求类型定位，不猜测请求子类的处理方式。
     */
    @SuppressWarnings("unchecked")
    public <Req extends PersistenceRequest, Res> PersistenceExecutor<Req, Res> find(Req request) {
        if (request == null || !executors.containsKey(request.getClass())) throw PersistenceTargetResolver.invalid();
        return (PersistenceExecutor<Req, Res>) executors.get(request.getClass());
    }
}
