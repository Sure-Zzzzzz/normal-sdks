package io.github.surezzzzzz.sdk.ops.middleware.service;

/**
 * 固定请求类型的执行器基础类。
 *
 * @param <Req> 请求类型
 * @param <Res> 响应类型
 * @author surezzzzzz
 */
public abstract class AbstractMiddlewareOpsExecutor<Req extends MiddlewareOpsRequest, Res>
        implements MiddlewareOpsExecutor<Req, Res> {

    private final Class<Req> requestType;

    /**
     * 创建固定请求类型执行器。
     *
     * @param requestType 请求类型
     */
    protected AbstractMiddlewareOpsExecutor(Class<Req> requestType) {
        this.requestType = requestType;
    }

    @Override
    public Class<Req> getRequestType() {
        return requestType;
    }
}
