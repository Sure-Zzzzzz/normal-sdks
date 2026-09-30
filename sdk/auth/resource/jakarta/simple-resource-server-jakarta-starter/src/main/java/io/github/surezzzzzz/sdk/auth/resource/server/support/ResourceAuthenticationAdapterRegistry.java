package io.github.surezzzzzz.sdk.auth.resource.server.support;

import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;

import java.util.Collection;

/**
 * 资源认证适配器注册表。
 *
 * @author surezzzzzz
 */
public interface ResourceAuthenticationAdapterRegistry {

    /**
     * 按来源查找唯一认证适配器。
     *
     * @param sourceId 认证来源标识
     * @return 唯一认证适配器；不存在时返回null
     */
    ResourceAuthenticationAdapter get(ResourceAuthenticationSourceId sourceId);

    /**
     * 获取已注册适配器。
     *
     * @return 不可修改的适配器集合
     */
    Collection<ResourceAuthenticationAdapter> getAll();
}
