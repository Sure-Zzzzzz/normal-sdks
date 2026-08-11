package io.github.surezzzzzz.sdk.ops.middleware.configuration;

import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Middleware Ops 页面静态资源映射。
 *
 * @author surezzzzzz
 */
public class MiddlewareOpsWebMvcConfiguration implements WebMvcConfigurer {

    private final SmartMiddlewareOpsServerProperties properties;

    /**
     * 创建页面静态资源映射。
     *
     * @param properties Server 配置
     */
    public MiddlewareOpsWebMvcConfiguration(SmartMiddlewareOpsServerProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(properties.getUiBasePath() + "/**")
                .addResourceLocations("classpath:/static/middleware-ops/");
    }
}
