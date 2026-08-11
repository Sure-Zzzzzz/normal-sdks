package io.github.surezzzzzz.sdk.ops.middleware.authorization;

/**
 * 一期已认证用户全量只读放行策略。
 *
 * @author surezzzzzz
 */
public class AuthenticatedAllowAllMiddlewareOpsAuthorizationPolicy implements MiddlewareOpsAuthorizationPolicy {

    @Override
    public boolean isAllowed(MiddlewareOpsAuthorizationContext context) {
        return context != null && context.getIdentity() != null && context.getCapability() != null
                && context.getMiddlewareType() == context.getCapability().getMiddlewareType();
    }
}
