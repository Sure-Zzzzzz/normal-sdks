package io.github.surezzzzzz.sdk.auth.resource.core.model;

/**
 * 认证适配器调用期凭据。
 *
 * @author surezzzzzz
 */
public interface ResourceCredential {

    /**
     * 返回唯一路由来源标识；无法安全路由时返回null。
     *
     * @return 路由来源标识
     */
    ResourceAuthenticationSourceId getSourceId();
}
