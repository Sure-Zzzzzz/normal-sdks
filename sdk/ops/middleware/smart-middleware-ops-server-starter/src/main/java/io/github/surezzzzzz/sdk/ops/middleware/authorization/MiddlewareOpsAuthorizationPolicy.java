package io.github.surezzzzzz.sdk.ops.middleware.authorization;

/**
 * 运维能力授权策略扩展口。
 *
 * @author surezzzzzz
 */
public interface MiddlewareOpsAuthorizationPolicy {

    /**
     * 判断当前已认证主体是否可执行能力。
     *
     * @param context 授权上下文
     * @return true 表示允许
     */
    boolean isAllowed(MiddlewareOpsAuthorizationContext context);
}
