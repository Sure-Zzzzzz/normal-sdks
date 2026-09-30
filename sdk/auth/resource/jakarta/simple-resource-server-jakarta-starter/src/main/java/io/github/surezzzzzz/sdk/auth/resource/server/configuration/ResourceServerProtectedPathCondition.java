package io.github.surezzzzzz.sdk.auth.resource.server.configuration;

import io.github.surezzzzzz.sdk.auth.resource.server.constant.SimpleResourceServerStarterConstant;
import io.github.surezzzzzz.sdk.auth.resource.server.exception.ResourceServerConfigurationException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/**
 * 仅在显式配置受保护路径时启用资源安全链。
 *
 * @author surezzzzzz
 */
public final class ResourceServerProtectedPathCondition implements Condition {

    /**
     * 判断是否存在受保护路径。
     *
     * @param context  条件上下文
     * @param metadata 注解元数据
     * @return 是否启用资源安全链
     */
    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        ResourceServerProperties properties = Binder.get(context.getEnvironment())
                .bind(SimpleResourceServerStarterConstant.CONFIG_PREFIX, Bindable.of(ResourceServerProperties.class))
                .orElseGet(ResourceServerProperties::new);
        if (!properties.isEnabled()) {
            return false;
        }
        if (properties.getSecurity().getProtectedPaths().isEmpty()
                && !properties.getSecurity().getApiPermissionRules().isEmpty()) {
            throw new ResourceServerConfigurationException(
                    SimpleResourceServerStarterConstant.ERROR_MISSING_PROTECTED_PATH);
        }
        return !properties.getSecurity().getProtectedPaths().isEmpty();
    }
}
