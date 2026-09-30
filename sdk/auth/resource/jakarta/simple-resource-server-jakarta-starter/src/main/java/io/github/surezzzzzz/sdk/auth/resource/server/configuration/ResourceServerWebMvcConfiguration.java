package io.github.surezzzzzz.sdk.auth.resource.server.configuration;

import io.github.surezzzzzz.sdk.auth.authorization.application.core.spi.ApplicationAuthorizationEvaluator;
import io.github.surezzzzzz.sdk.auth.resource.server.interceptor.RequireApiPermissionInterceptor;
import io.github.surezzzzzz.sdk.auth.resource.server.support.ConfiguredApiPermissionResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 通用资源服务MVC授权配置。
 *
 * @author surezzzzzz
 */
public class ResourceServerWebMvcConfiguration implements WebMvcConfigurer {

    private final ApplicationAuthorizationEvaluator evaluator;
    private final ConfiguredApiPermissionResolver resolver;

    /**
     * 创建MVC授权配置。
     *
     * @param evaluator 精确API权限判定器
     * @param resolver  配置化精确API权限规则解析器
     */
    public ResourceServerWebMvcConfiguration(ApplicationAuthorizationEvaluator evaluator,
                                             ConfiguredApiPermissionResolver resolver) {
        this.evaluator = evaluator;
        this.resolver = resolver;
    }

    /**
     * 注册精确API权限拦截器。
     *
     * @param registry 拦截器注册表
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RequireApiPermissionInterceptor(evaluator, resolver))
                .addPathPatterns(resolver.getProtectedPathPatterns());
    }
}
