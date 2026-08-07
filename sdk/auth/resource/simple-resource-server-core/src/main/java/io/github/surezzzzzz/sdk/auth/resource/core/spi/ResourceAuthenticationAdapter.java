package io.github.surezzzzzz.sdk.auth.resource.core.spi;

import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationResult;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceCredential;

/**
 * 资源认证Provider适配器。
 *
 * @author surezzzzzz
 */
public interface ResourceAuthenticationAdapter {

    /**
     * 返回适配器处理的唯一来源标识。
     *
     * @return 唯一来源标识
     */
    ResourceAuthenticationSourceId sourceId();

    /**
     * 对已唯一路由到当前适配器的凭据执行认证。
     *
     * @param credential 已唯一路由的凭据
     * @return 认证结果
     */
    ResourceAuthenticationResult authenticate(ResourceCredential credential);
}
