package io.github.surezzzzzz.sdk.ops.middleware.authentication;

/**
 * 将已认证传输上下文解析为运维身份的扩展口。
 *
 * @author surezzzzzz
 */
public interface MiddlewareOpsIdentityResolver {

    /**
     * 解析当前请求的已认证身份。
     *
     * @return 身份；未认证时返回 null
     */
    MiddlewareOpsIdentity resolve();
}
