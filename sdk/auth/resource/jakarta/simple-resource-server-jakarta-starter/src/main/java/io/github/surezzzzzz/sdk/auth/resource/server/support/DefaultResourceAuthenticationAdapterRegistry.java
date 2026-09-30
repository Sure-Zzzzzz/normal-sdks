package io.github.surezzzzzz.sdk.auth.resource.server.support;

import io.github.surezzzzzz.sdk.auth.resource.core.model.ResourceAuthenticationSourceId;
import io.github.surezzzzzz.sdk.auth.resource.core.spi.ResourceAuthenticationAdapter;
import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;
import io.github.surezzzzzz.sdk.auth.resource.server.exception.ResourceServerConfigurationException;

import java.util.*;

/**
 * 默认资源认证适配器注册表。
 *
 * @author surezzzzzz
 */
public final class DefaultResourceAuthenticationAdapterRegistry implements ResourceAuthenticationAdapterRegistry {

    private final Map<ResourceAuthenticationSourceId, ResourceAuthenticationAdapter> adapters;

    /**
     * 创建并校验认证适配器注册表。
     *
     * @param adapters 认证适配器集合
     */
    public DefaultResourceAuthenticationAdapterRegistry(Collection<ResourceAuthenticationAdapter> adapters) {
        if (adapters == null) {
            throw new ResourceServerConfigurationException(
                    SimpleResourceServerStarterConstant.ERROR_AUTHENTICATION_ADAPTER_COLLECTION_NULL);
        }
        Map<ResourceAuthenticationSourceId, ResourceAuthenticationAdapter> registered =
                new LinkedHashMap<ResourceAuthenticationSourceId, ResourceAuthenticationAdapter>();
        for (ResourceAuthenticationAdapter adapter : adapters) {
            if (adapter == null) {
                throw new ResourceServerConfigurationException(
                        SimpleResourceServerStarterConstant.ERROR_AUTHENTICATION_ADAPTER_NULL);
            }
            ResourceAuthenticationSourceId sourceId = adapter.sourceId();
            if (sourceId == null) {
                throw new ResourceServerConfigurationException(
                        SimpleResourceServerStarterConstant.ERROR_AUTHENTICATION_ADAPTER_SOURCE_NULL);
            }
            if (registered.put(sourceId, adapter) != null) {
                throw new ResourceServerConfigurationException(String.format(
                        SimpleResourceServerStarterConstant.ERROR_DUPLICATE_AUTHENTICATION_SOURCE, sourceId.getValue()));
            }
        }
        this.adapters = Collections.unmodifiableMap(registered);
    }

    /**
     * 按来源查找唯一认证适配器。
     *
     * @param sourceId 认证来源标识
     * @return 唯一认证适配器；不存在时返回null
     */
    @Override
    public ResourceAuthenticationAdapter get(ResourceAuthenticationSourceId sourceId) {
        return adapters.get(sourceId);
    }

    /**
     * 获取已注册适配器。
     *
     * @return 不可修改的适配器集合
     */
    @Override
    public Collection<ResourceAuthenticationAdapter> getAll() {
        List<ResourceAuthenticationAdapter> values = new ArrayList<ResourceAuthenticationAdapter>(adapters.values());
        return Collections.unmodifiableList(values);
    }
}
