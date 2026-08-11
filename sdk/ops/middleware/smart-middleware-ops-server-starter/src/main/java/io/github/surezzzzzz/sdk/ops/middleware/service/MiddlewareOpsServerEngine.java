package io.github.surezzzzzz.sdk.ops.middleware.service;

/**
 * Middleware Ops Server 唯一编排入口。
 *
 * @author surezzzzzz
 */
public interface MiddlewareOpsServerEngine {

    /**
     * 执行已注册的显式只读能力。
     *
     * @param request      请求
     * @param responseType 预期响应类型
     * @param <Res>        响应类型
     * @return 响应
     */
    <Res> Res execute(MiddlewareOpsRequest request, Class<Res> responseType);
}
