package io.github.surezzzzzz.sdk.ops.middleware.service;

/**
 * 类型化运维请求校验器。
 *
 * @param <Req> 请求类型
 * @author surezzzzzz
 */
public interface MiddlewareOpsRequestValidator<Req extends MiddlewareOpsRequest> {

    /**
     * 获取请求类型。
     *
     * @return 请求类型
     */
    Class<Req> getRequestType();

    /**
     * 校验请求边界。
     *
     * @param request 请求
     */
    void validate(Req request);
}
