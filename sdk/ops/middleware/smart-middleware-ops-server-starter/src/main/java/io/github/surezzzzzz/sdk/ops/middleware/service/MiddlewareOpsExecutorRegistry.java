package io.github.surezzzzzz.sdk.ops.middleware.service;

/**
 * 已注册显式能力的类型化执行器注册表。
 *
 * @author surezzzzzz
 */
public interface MiddlewareOpsExecutorRegistry {

    /**
     * 获取匹配请求类型的执行器。
     *
     * @param request 请求
     * @return 执行器
     */
    MiddlewareOpsExecutor<?, ?> getExecutor(MiddlewareOpsRequest request);

    /**
     * 校验请求边界。
     *
     * @param request 请求
     */
    void validate(MiddlewareOpsRequest request);
}
