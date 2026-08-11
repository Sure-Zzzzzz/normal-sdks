package io.github.surezzzzzz.sdk.ops.middleware.service;

/**
 * 类型化只读能力执行器。
 *
 * @param <Req> 请求类型
 * @param <Res> 响应类型
 * @author surezzzzzz
 */
public interface MiddlewareOpsExecutor<Req extends MiddlewareOpsRequest, Res> {

    /**
     * 获取请求类型。
     *
     * @return 请求类型
     */
    Class<Req> getRequestType();

    /**
     * 执行已受控的只读能力。
     *
     * @param request 请求
     * @return 响应
     */
    Res execute(Req request);
}
