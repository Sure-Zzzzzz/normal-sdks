package io.github.surezzzzzz.sdk.elasticsearch.persistence.executor;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.PersistenceRequest;

/**
 * 可替换的请求执行器。
 */
public interface PersistenceExecutor<Req extends PersistenceRequest, Res> {
    /**
     * 当前执行器拥有的请求类型。
     */
    Class<Req> getRequestType();

    /**
     * 完成一个业务请求并返回终态。
     */
    Res execute(Req request);

    /**
     * 准备可延迟执行的请求；自定义实现负责在此冻结可变输入。
     */
    default java.util.function.Supplier<Res> prepare(Req request, boolean clientAsync) {
        return () -> execute(request);
    }
}
