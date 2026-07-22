package io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorCode;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.ErrorMessage;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import io.github.surezzzzzz.sdk.limiter.redis.smart.management.exception.SmartRedisLimiterManagementConfigurationException;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.context.annotation.ImportBeanDefinitionRegistrar;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotationMetadata;

/**
 * Management 自动配置解析阶段校验器
 *
 * @author surezzzzzz
 */
public class SmartRedisLimiterManagementConfigurationValidator
        implements ImportBeanDefinitionRegistrar, EnvironmentAware {

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void registerBeanDefinitions(
            AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
        SmartRedisLimiterManagementProperties properties = Binder.get(environment)
                .bind(SmartRedisLimiterManagementConstant.CONFIG_PREFIX,
                        Bindable.of(SmartRedisLimiterManagementProperties.class))
                .orElseGet(SmartRedisLimiterManagementProperties::new);
        properties.init();
        if (isApiEnabled(properties) && isResourceServerExplicitlyDisabled()
                && !hasText(properties.getRest().getPolicyToken())) {
            throw new SmartRedisLimiterManagementConfigurationException(
                    ErrorCode.CONFIG_REST_TOKEN_REQUIRED,
                    ErrorMessage.CONFIG_REST_TOKEN_REQUIRED);
        }
    }

    private boolean isApiEnabled(SmartRedisLimiterManagementProperties properties) {
        return Boolean.TRUE.equals(properties.getApi().getEnable());
    }

    private boolean isResourceServerExplicitlyDisabled() {
        return Boolean.FALSE.equals(Binder.get(environment)
                .bind(SmartRedisLimiterManagementConstant.RESOURCE_SERVER_CONFIG_PREFIX + ".enabled",
                        Boolean.class)
                .orElse(null));
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
