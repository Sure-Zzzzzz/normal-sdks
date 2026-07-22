package io.github.surezzzzzz.sdk.limiter.redis.smart.management.configuration;

import io.github.surezzzzzz.sdk.limiter.redis.smart.management.constant.SmartRedisLimiterManagementConstant;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * SmartRedisLimiter Management 页面资源配置
 *
 * @author surezzzzzz
 */
@Configuration
@ConditionalOnProperty(
        prefix = SmartRedisLimiterManagementConstant.CONFIG_PREFIX + ".ui",
        name = SmartRedisLimiterManagementConstant.CONFIG_FIELD_ENABLE,
        havingValue = "true")
public class SmartRedisLimiterManagementWebMvcConfiguration implements WebMvcConfigurer {

    private static final String RESOURCE_LOCATION =
            "classpath:/static/smart-redis-limiter-management/assets/";

    private final SmartRedisLimiterManagementProperties properties;

    /**
     * 构造页面资源配置
     *
     * @param properties management 配置
     */
    public SmartRedisLimiterManagementWebMvcConfiguration(
            SmartRedisLimiterManagementProperties properties) {
        this.properties = properties;
    }

    /**
     * 注册 Management 页面静态资源
     *
     * @param registry 资源处理器注册表
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(properties.getUi().getBasePath()
                        + SmartRedisLimiterManagementConstant.PATH_ASSETS_WILDCARD)
                .addResourceLocations(RESOURCE_LOCATION);
    }
}
