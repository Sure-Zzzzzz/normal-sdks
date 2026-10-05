package io.github.surezzzzzz.sdk.elasticsearch.persistence.executor;

import io.github.surezzzzzz.sdk.elasticsearch.persistence.core.model.request.PersistenceRequest;
import io.github.surezzzzzz.sdk.elasticsearch.persistence.support.ElasticsearchWriteApiHelper;
import lombok.RequiredArgsConstructor;

/**
 * 默认 REST 执行器适配器，每个请求类型可以被单独替换。
 */
@RequiredArgsConstructor
public class RestPersistenceExecutor<Req extends PersistenceRequest, Res> implements PersistenceExecutor<Req, Res> {
    private final Class<Req> requestType;
    private final ElasticsearchWriteApiHelper helper;

    /**
     * 声明执行器接收的请求类型。
     */
    @Override
    public Class<Req> getRequestType() {
        return requestType;
    }

    /**
     * 执行持久化请求，返回最终结果。
     */
    @Override
    @SuppressWarnings("unchecked")
    public Res execute(Req request) {
        return (Res) helper.prepare(request, false).get();
    }

    /**
     * 在异步排队前冻结并编译请求。
     */
    @Override
    @SuppressWarnings("unchecked")
    public java.util.function.Supplier<Res> prepare(Req request, boolean clientAsync) {
        java.util.function.Supplier<Object> prepared = helper.prepare(request, clientAsync);
        return () -> (Res) prepared.get();
    }
}
