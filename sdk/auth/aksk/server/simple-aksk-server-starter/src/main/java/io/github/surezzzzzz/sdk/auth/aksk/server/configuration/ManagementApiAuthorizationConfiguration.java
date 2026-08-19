package io.github.surezzzzzz.sdk.auth.aksk.server.configuration;

import io.github.surezzzzzz.sdk.auth.aksk.server.annotation.SimpleAkskServerComponent;
import io.github.surezzzzzz.sdk.auth.aksk.server.interceptor.ManagementApiAuthorizationInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 管理 REST API 授权配置。
 *
 * @author surezzzzzz
 */
@Configuration
@SimpleAkskServerComponent
@RequiredArgsConstructor
public class ManagementApiAuthorizationConfiguration implements WebMvcConfigurer {

    private final ManagementApiAuthorizationInterceptor managementApiAuthorizationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(managementApiAuthorizationInterceptor)
                .addPathPatterns("/api/client/**", "/api/token/**", "/api/application-authorization/**");
    }
}
